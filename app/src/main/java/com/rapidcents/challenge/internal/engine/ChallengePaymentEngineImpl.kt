package com.rapidcents.challenge.internal.engine

import com.rapidcents.challenge.contract.AcceptanceEvent
import com.rapidcents.challenge.contract.AcceptanceListener
import com.rapidcents.challenge.contract.AcceptanceRequest
import com.rapidcents.challenge.contract.AcceptanceType
import com.rapidcents.challenge.contract.CancelResult
import com.rapidcents.challenge.contract.ChallengeDependencies
import com.rapidcents.challenge.contract.ChallengePaymentEngine
import com.rapidcents.challenge.contract.GatewayRequest
import com.rapidcents.challenge.contract.GatewayResult
import com.rapidcents.challenge.contract.InquiryResult
import com.rapidcents.challenge.contract.OpaquePaymentCredential
import com.rapidcents.challenge.contract.RecoveryReport
import com.rapidcents.challenge.contract.ReversalResult
import com.rapidcents.challenge.contract.SafeAuditEvent
import com.rapidcents.challenge.contract.StartResult
import com.rapidcents.challenge.contract.StartSale
import com.rapidcents.challenge.contract.TransactionSnapshot
import com.rapidcents.challenge.contract.TransactionState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume


/**
 * Core payment engine implementation.
 *
 * This class contains no Android imports, so it can later be moved
 * into a pure Kotlin/JVM core module.
 */
class ChallengePaymentEngineImpl(
    private val dependencies: ChallengeDependencies
) : ChallengePaymentEngine {

    private val stateMutex = Mutex()

    private val auditEvents = MutableSharedFlow<SafeAuditEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    /**
     * Only one active transaction is allowed on the terminal.
     */
    private var activeRequestId: String? = null

    /**
     * Thread-safe ownership of the acceptance operation that is
     * currently waiting for an SDK callback.
     *
     * Cancellation removes this session before calling SDK cancel.
     * A late callback therefore cannot start authorization.
     */
    private val activeAcceptance =
        AtomicReference<ActiveAcceptanceSession?>(null)

    override suspend fun startSale(
        command: StartSale
    ): StartResult {

        // Idempotency:
        // the same request ID must return the existing transaction
        // instead of creating another authorization.
        dependencies.journal.findByRequestId(command.requestId)?.let {
            return StartResult.Final(it)
        }

        validate(command)?.let {
            return it
        }

        val existingInvoice =
            dependencies.journal.findLatestByInvoiceId(command.invoiceId)

        if (
            existingInvoice != null &&
            existingInvoice.requestId != command.requestId &&
            !command.explicitInvoiceOverride
        ) {
            return StartResult.DuplicateInvoiceRejected(
                invoiceId = command.invoiceId,
                existingRequestId = existingInvoice.requestId,
                existingState = existingInvoice.state
            )
        }

        val busyResult = stateMutex.withLock {
            val currentRequestId = activeRequestId

            if (currentRequestId != null) {
                StartResult.Busy(currentRequestId)
            } else {
                activeRequestId = command.requestId
                null
            }
        }

        if (busyResult != null) {
            return busyResult
        }

        val sessionId = UUID.randomUUID().toString()

        return try {
            processSale(
                command = command,
                sessionId = sessionId
            )
        } finally {
            clearAcceptanceSession(sessionId)

            stateMutex.withLock {
                if (activeRequestId == command.requestId) {
                    activeRequestId = null
                }
            }
        }
    }

    private suspend fun processSale(
        command: StartSale,
        sessionId: String
    ): StartResult {

        var snapshot = TransactionSnapshot(
            requestId = command.requestId,
            invoiceId = command.invoiceId,
            amount = command.amount,
            state = TransactionState.VALIDATING,
            updatedAtEpochMs = dependencies.clock.nowEpochMs(),
            paymentMethod = command.paymentMethod
        )

        saveAndAudit(
            snapshot = snapshot,
            eventType = "SALE_VALIDATING",
            provider = command.acceptance
        )

        snapshot = snapshot.nextState(
            TransactionState.WAITING_FOR_ACCEPTANCE
        )

        saveAndAudit(
            snapshot = snapshot,
            eventType = "WAITING_FOR_ACCEPTANCE",
            provider = command.acceptance
        )

        val acceptanceOutcome = waitForAcceptance(
            command = command,
            sessionId = sessionId
        )

        when (acceptanceOutcome) {
            is AcceptanceOutcome.Cancelled -> {
                snapshot = snapshot.nextState(
                    state = TransactionState.CANCELLED,
                    outcomeCode = "USER_CANCELLED",
                    message = "Acceptance was cancelled."
                )

                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "ACCEPTANCE_CANCELLED",
                    provider = command.acceptance
                )

                return StartResult.Final(snapshot)
            }

            is AcceptanceOutcome.Error -> {
                snapshot = snapshot.nextState(
                    state = TransactionState.FAILED,
                    outcomeCode = acceptanceOutcome.code,
                    message = "Acceptance provider failed."
                )

                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "ACCEPTANCE_FAILED",
                    provider = command.acceptance
                )

                return StartResult.Final(snapshot)
            }

            is AcceptanceOutcome.Credential -> {
                // Continue below.
            }
        }

        val credential =
            (acceptanceOutcome as AcceptanceOutcome.Credential).value

        snapshot = snapshot.nextState(
            TransactionState.CARD_PRESENTED
        )

        saveAndAudit(
            snapshot = snapshot,
            eventType = "CARD_PRESENTED",
            provider = command.acceptance
        )

        snapshot = snapshot.nextState(
            TransactionState.AUTHORIZING
        )

        // Persist AUTHORIZING before sending anything to the gateway.
        // If the app stops here, recovery will use inquiry rather than
        // sending another authorization.
        saveAndAudit(
            snapshot = snapshot,
            eventType = "AUTHORIZATION_STARTED",
            provider = command.acceptance
        )

        val gatewayResult = dependencies.gateway.authorize(
            GatewayRequest(
                requestId = command.requestId,
                amount = command.amount,
                credential = credential
            )
        )

        return handleGatewayResult(
            snapshot = snapshot,
            result = gatewayResult,
            provider = command.acceptance
        )
    }

    override suspend fun cancel(
        requestId: String
    ): CancelResult {

        if (requestId.isBlank()) {
            return CancelResult.Rejected(
                code = "INVALID_REQUEST_ID",
                message = "Request ID must not be blank."
            )
        }

        val current = dependencies.journal.findByRequestId(requestId)
            ?: return CancelResult.NotFound(requestId)

        return when (current.state) {
            TransactionState.APPROVED,
            TransactionState.PARTIALLY_APPROVED -> {
                cancelApprovedTransaction(current)
            }

            TransactionState.AUTHORIZING,
            TransactionState.RESOLVING_UNKNOWN,
            TransactionState.RECOVERY_PENDING -> {
                val cancelling = current.nextState(
                    state = TransactionState.CANCELLING,
                    outcomeCode = "CANCEL_REQUESTED",
                    message = "Cancellation requested while outcome is uncertain."
                )

                saveAndAudit(
                    snapshot = cancelling,
                    eventType = "CANCEL_REQUESTED"
                )

                // We cannot assume that authorization stopped.
                // Recovery/inquiry must determine the actual result.
                CancelResult.Accepted(requestId)
            }

            TransactionState.VALIDATING,
            TransactionState.WAITING_FOR_ACCEPTANCE,
            TransactionState.CARD_PRESENTED -> {
                cancelAcceptanceProvider()

                val cancelled = current.nextState(
                    state = TransactionState.CANCELLED,
                    outcomeCode = "USER_CANCELLED",
                    message = "Cancelled before gateway authorization."
                )

                saveAndAudit(
                    snapshot = cancelled,
                    eventType = "SALE_CANCELLED"
                )

                CancelResult.Final(cancelled)
            }

            TransactionState.CANCELLING,
            TransactionState.REVERSAL_PENDING -> {
                CancelResult.Accepted(requestId)
            }

            TransactionState.DECLINED,
            TransactionState.CANCELLED,
            TransactionState.FAILED -> {
                CancelResult.Final(current)
            }
        }
    }

    private suspend fun cancelApprovedTransaction(
        current: TransactionSnapshot
    ): CancelResult {

        var snapshot = current.nextState(
            state = TransactionState.CANCELLING,
            outcomeCode = "REVERSAL_REQUESTED",
            message = "Reversal requested for approved transaction."
        )

        saveAndAudit(
            snapshot = snapshot,
            eventType = "REVERSAL_STARTED"
        )

        return when (
            val reversal = dependencies.gateway.reverse(current.requestId)
        ) {
            ReversalResult.Success -> {
                snapshot = snapshot.nextState(
                    state = TransactionState.CANCELLED,
                    outcomeCode = "REVERSED",
                    message = "Authorization was successfully reversed."
                )

                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "REVERSAL_SUCCEEDED"
                )

                CancelResult.Final(snapshot)
            }

            ReversalResult.Pending -> {
                snapshot = snapshot.nextState(
                    state = TransactionState.REVERSAL_PENDING,
                    outcomeCode = "REVERSAL_PENDING",
                    message = "Reversal outcome is still pending."
                )

                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "REVERSAL_PENDING"
                )

                CancelResult.Accepted(current.requestId)
            }

            is ReversalResult.Failed -> {
                snapshot = snapshot.nextState(
                    state = TransactionState.REVERSAL_PENDING,
                    outcomeCode = "REVERSAL_FAILED",
                    message = reversal.reason
                )

                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "REVERSAL_FAILED"
                )

                // Do not silently change an approved payment to cancelled.
                CancelResult.Accepted(current.requestId)
            }
        }
    }

    override suspend fun status(
        requestId: String
    ): TransactionSnapshot? {
        return dependencies.journal.findByRequestId(requestId)
    }

    override suspend fun recover(): RecoveryReport {
        val unfinishedTransactions =
            dependencies.journal.unfinished()

        var finalized = 0
        var stillPending = 0
        val failures = mutableListOf<String>()

        unfinishedTransactions.forEach { snapshot ->
            try {
                val recovered = when (snapshot.state) {
                    TransactionState.AUTHORIZING,
                    TransactionState.RESOLVING_UNKNOWN,
                    TransactionState.RECOVERY_PENDING,
                    TransactionState.CANCELLING -> {
                        recoverAuthorization(snapshot)
                    }

                    TransactionState.REVERSAL_PENDING -> {
                        recoverReversal(snapshot)
                    }

                    else -> snapshot
                }

                if (recovered.state.isFinalState()) {
                    finalized++
                } else {
                    stillPending++
                }
            } catch (exception: Exception) {
                failures.add(
                    "${snapshot.requestId}: ${exception.message ?: "Recovery error"}"
                )
            }
        }

        return RecoveryReport(
            inspected = unfinishedTransactions.size,
            finalized = finalized,
            stillPending = stillPending,
            failures = failures
        )
    }

    private suspend fun recoverAuthorization(
        snapshot: TransactionSnapshot
    ): TransactionSnapshot {

        val inquiry = dependencies.gateway.inquire(
            snapshot.requestId
        )

        return resolveInquiry(
            snapshot = snapshot,
            inquiry = inquiry
        )
    }

    private suspend fun recoverReversal(
        snapshot: TransactionSnapshot
    ): TransactionSnapshot {

        return when (
            val result = dependencies.gateway.reverse(snapshot.requestId)
        ) {
            ReversalResult.Success -> {
                val cancelled = snapshot.nextState(
                    state = TransactionState.CANCELLED,
                    outcomeCode = "REVERSED",
                    message = "Pending reversal completed during recovery."
                )

                saveAndAudit(
                    snapshot = cancelled,
                    eventType = "RECOVERY_REVERSAL_SUCCEEDED"
                )

                cancelled
            }

            ReversalResult.Pending -> {
                saveAndAudit(
                    snapshot = snapshot,
                    eventType = "RECOVERY_REVERSAL_PENDING"
                )

                snapshot
            }

            is ReversalResult.Failed -> {
                val pending = snapshot.nextState(
                    state = TransactionState.REVERSAL_PENDING,
                    outcomeCode = "REVERSAL_FAILED",
                    message = result.reason
                )

                saveAndAudit(
                    snapshot = pending,
                    eventType = "RECOVERY_REVERSAL_FAILED"
                )

                pending
            }
        }
    }

    override fun events(): Flow<SafeAuditEvent> {
        return auditEvents.asSharedFlow()
    }

    private suspend fun handleGatewayResult(
        snapshot: TransactionSnapshot,
        result: GatewayResult,
        provider: AcceptanceType
    ): StartResult {

        val resolvedSnapshot = when (result) {
            is GatewayResult.Approved -> {
                snapshot.nextState(
                    state = TransactionState.APPROVED,
                    approvedAmountMinor = result.approvedAmount,
                    hostReference = result.hostReference,
                    outcomeCode = result.approvalCode,
                    message = "Approved."
                )
            }

            is GatewayResult.Declined -> {
                snapshot.nextState(
                    state = TransactionState.DECLINED,
                    outcomeCode = result.code,
                    message = result.message
                )
            }

            is GatewayResult.PartialApproval -> {
                snapshot.nextState(
                    state = TransactionState.PARTIALLY_APPROVED,
                    approvedAmountMinor = result.approvedAmount,
                    hostReference = result.hostReference,
                    outcomeCode = result.approvalCode,
                    message = "Partial approval requires an explicit decision."
                )
            }

            GatewayResult.Timeout,
            GatewayResult.Unknown,
            is GatewayResult.Malformed -> {
                val resolving = snapshot.nextState(
                    state = TransactionState.RESOLVING_UNKNOWN,
                    outcomeCode = "UNKNOWN_OUTCOME",
                    message = "Gateway outcome is uncertain; inquiry required."
                )

                saveAndAudit(
                    snapshot = resolving,
                    eventType = "AUTHORIZATION_OUTCOME_UNKNOWN",
                    provider = provider
                )

                return StartResult.Final(
                    resolveInquiry(
                        snapshot = resolving,
                        inquiry = dependencies.gateway.inquire(
                            snapshot.requestId
                        )
                    )
                )
            }
        }

        saveAndAudit(
            snapshot = resolvedSnapshot,
            eventType = "AUTHORIZATION_COMPLETED",
            provider = provider
        )

        return StartResult.Final(resolvedSnapshot)
    }

    private suspend fun resolveInquiry(
        snapshot: TransactionSnapshot,
        inquiry: InquiryResult
    ): TransactionSnapshot {

        val resolved = when (inquiry) {
            is InquiryResult.Approved -> {
                snapshot.nextState(
                    state = TransactionState.APPROVED,
                    approvedAmountMinor = inquiry.approvedAmount,
                    hostReference = inquiry.hostReference,
                    outcomeCode = inquiry.approvalCode,
                    message = "Approved result recovered by inquiry."
                )
            }

            is InquiryResult.Declined -> {
                snapshot.nextState(
                    state = TransactionState.DECLINED,
                    outcomeCode = inquiry.code,
                    message = inquiry.message
                )
            }

            InquiryResult.NotFound,
            InquiryResult.Unknown -> {
                snapshot.nextState(
                    state = TransactionState.RECOVERY_PENDING,
                    outcomeCode = "INQUIRY_UNRESOLVED",
                    message = "Inquiry could not determine a final outcome."
                )
            }
        }

        saveAndAudit(
            snapshot = resolved,
            eventType = "INQUIRY_COMPLETED"
        )

        return resolved
    }

    private suspend fun waitForAcceptance(
        command: StartSale,
        sessionId: String
    ): AcceptanceOutcome {

        val request = AcceptanceRequest(
            sessionId = sessionId,
            requestId = command.requestId,
            amount = command.amount
        )

        return suspendCancellableCoroutine { continuation ->
            val session = ActiveAcceptanceSession(
                sessionId = sessionId,
                requestId = command.requestId,
                acceptanceType = command.acceptance,
                continuation = continuation
            )

            check(
                activeAcceptance.compareAndSet(
                    null,
                    session
                )
            ) {
                "Another acceptance session is already active."
            }

            val listener = AcceptanceListener { event ->
                when (event) {
                    is AcceptanceEvent.Prompt -> {
                        val currentSession =
                            activeAcceptance.get()

                        if (currentSession?.sessionId != sessionId) {
                            return@AcceptanceListener
                        }

                        recordAudit(
                            snapshot = null,
                            requestId = command.requestId,
                            invoiceId = command.invoiceId,
                            state = TransactionState.WAITING_FOR_ACCEPTANCE,
                            eventType = "SDK_PROMPT_${event.code}",
                            provider = command.acceptance
                        )
                    }

                    is AcceptanceEvent.Credential -> {
                        completeAcceptance(
                            session = session,
                            outcome = AcceptanceOutcome.Credential(
                                event.value
                            )
                        )
                    }

                    is AcceptanceEvent.Error -> {
                        completeAcceptance(
                            session = session,
                            outcome = AcceptanceOutcome.Error(
                                event.code
                            )
                        )
                    }

                    AcceptanceEvent.Cancelled -> {
                        completeAcceptance(
                            session = session,
                            outcome = AcceptanceOutcome.Cancelled
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                val removed =
                    activeAcceptance.compareAndSet(
                        session,
                        null
                    )

                if (removed) {
                    cancelSdkSession(
                        acceptanceType = command.acceptance,
                        sessionId = sessionId
                    )
                }
            }

            try {
                when (command.acceptance) {
                    AcceptanceType.HARDWARE -> {
                        dependencies.hardwareSdk.start(
                            request = request,
                            listener = listener
                        )
                    }

                    AcceptanceType.TAP_TO_PAY -> {
                        dependencies.tapToPaySdk.start(
                            request = request,
                            listener = listener
                        )
                    }
                }
            } catch (exception: Exception) {
                completeAcceptance(
                    session = session,
                    outcome = AcceptanceOutcome.Error(
                        exception.message ?: "SDK_START_FAILED"
                    )
                )
            }
        }
    }

    /**
     * Completes an acceptance operation exactly once.
     *
     * compareAndSet is safe when an SDK callback arrives from
     * another thread. A session removed by cancel() cannot be resumed.
     */
    private fun completeAcceptance(
        session: ActiveAcceptanceSession,
        outcome: AcceptanceOutcome
    ) {
        val sessionWasActive =
            activeAcceptance.compareAndSet(
                session,
                null
            )

        if (!sessionWasActive) {
            // Stale callback after cancellation or completion.
            return
        }

        if (session.continuation.isActive) {
            session.continuation.resume(outcome)
        }
    }

    private fun validate(
        command: StartSale
    ): StartResult.Rejected? {

        if (command.requestId.isBlank()) {
            return StartResult.Rejected(
                code = "INVALID_REQUEST_ID",
                message = "Request ID must not be blank."
            )
        }

        if (command.invoiceId.isBlank()) {
            return StartResult.Rejected(
                code = "INVALID_INVOICE_ID",
                message = "Invoice ID must not be blank."
            )
        }

        if (command.amount.minor <= 0L) {
            return StartResult.Rejected(
                code = "INVALID_AMOUNT",
                message = "Amount must be greater than zero."
            )
        }

        return null
    }

    // Persist AUTHORIZING before sending anything to the gateway.
    //
    // If the application crashes after the authorization request
    // leaves the device but before the response is received,
    // Recovery MUST perform an Inquiry rather than sending
    // another authorization request.
    //
    // This guarantees exactly-once payment processing.
    private suspend fun saveAndAudit(
        snapshot: TransactionSnapshot,
        eventType: String,
        provider: AcceptanceType? = null
    ) {
        dependencies.journal.save(snapshot)

        recordAudit(
            snapshot = snapshot,
            requestId = snapshot.requestId,
            invoiceId = snapshot.invoiceId,
            state = snapshot.state,
            eventType = eventType,
            provider = provider
        )
    }

    private fun recordAudit(
        snapshot: TransactionSnapshot?,
        requestId: String,
        invoiceId: String,
        state: TransactionState,
        eventType: String,
        provider: AcceptanceType? = null
    ) {
        val event = SafeAuditEvent(
            timestampEpochMs = dependencies.clock.nowEpochMs(),
            correlationId = requestId,
            requestId = requestId,
            invoiceIdHashPrefix = hashPrefix(invoiceId),
            state = state,
            eventType = eventType,
            outcomeCode = snapshot?.outcomeCode,
            provider = provider
        )

        dependencies.audit.record(event)
        auditEvents.tryEmit(event)
    }

    /**
     * Invalidates the acceptance session before calling the SDK.
     *
     * This ordering prevents a delayed credential callback from
     * starting gateway authorization after the user cancelled.
     */
    private fun cancelAcceptanceProvider() {
        val session =
            activeAcceptance.getAndSet(null)
                ?: return

        if (session.continuation.isActive) {
            session.continuation.resume(
                AcceptanceOutcome.Cancelled
            )
        }

        cancelSdkSession(
            acceptanceType = session.acceptanceType,
            sessionId = session.sessionId
        )
    }

    private fun cancelSdkSession(
        acceptanceType: AcceptanceType,
        sessionId: String
    ) {
        when (acceptanceType) {
            AcceptanceType.HARDWARE -> {
                dependencies.hardwareSdk.cancel(sessionId)
            }

            AcceptanceType.TAP_TO_PAY -> {
                dependencies.tapToPaySdk.cancel(sessionId)
            }
        }
    }

    /**
     * Defensive cleanup when startSale() exits.
     */
    private fun clearAcceptanceSession(
        sessionId: String
    ) {
        while (true) {
            val current =
                activeAcceptance.get()
                    ?: return

            if (current.sessionId != sessionId) {
                return
            }

            if (
                activeAcceptance.compareAndSet(
                    current,
                    null
                )
            ) {
                return
            }
        }
    }

    private fun TransactionSnapshot.nextState(
        state: TransactionState,
        approvedAmountMinor: Long? = this.approvedAmountMinor,
        hostReference: String? = this.hostReference,
        outcomeCode: String? = this.outcomeCode,
        message: String? = this.message
    ): TransactionSnapshot {
        return copy(
            state = state,
            approvedAmountMinor = approvedAmountMinor,
            hostReference = hostReference,
            outcomeCode = outcomeCode,
            message = message,
            updatedAtEpochMs = dependencies.clock.nowEpochMs()
        )
    }

    private fun TransactionState.isFinalState(): Boolean {
        return when (this) {
            TransactionState.APPROVED,
            TransactionState.DECLINED,
            TransactionState.CANCELLED,
            TransactionState.FAILED -> true

            else -> false
        }
    }

    private fun hashPrefix(value: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())

        return digest
            .take(4)
            .joinToString("") { byte ->
                "%02x".format(byte)
            }
    }

    private data class ActiveAcceptanceSession(
        val sessionId: String,
        val requestId: String,
        val acceptanceType: AcceptanceType,
        val continuation:
        CancellableContinuation<AcceptanceOutcome>
    )

    private sealed interface AcceptanceOutcome {

        data class Credential(
            val value: OpaquePaymentCredential
        ) : AcceptanceOutcome

        data class Error(
            val code: String
        ) : AcceptanceOutcome

        data object Cancelled : AcceptanceOutcome
    }
}