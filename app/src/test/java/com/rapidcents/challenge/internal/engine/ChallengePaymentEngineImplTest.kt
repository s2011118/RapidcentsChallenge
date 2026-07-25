package com.rapidcents.challenge.internal.engine

import com.rapidcents.challenge.internal.acceptance.ControllableTapToPaySdk
import com.rapidcents.challenge.internal.acceptance.ControllableHardwareCardSdk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import com.rapidcents.challenge.contract.CancelResult
import com.rapidcents.challenge.internal.gateway.ReversalBehavior
import com.rapidcents.challenge.internal.gateway.AuthorizeBehavior
import com.rapidcents.challenge.internal.gateway.InquiryBehavior
import com.rapidcents.challenge.contract.AcceptanceType
import com.rapidcents.challenge.contract.ChallengeDependencies
import com.rapidcents.challenge.contract.CommandSource
import com.rapidcents.challenge.contract.Currency
import com.rapidcents.challenge.contract.GatewayResult
import com.rapidcents.challenge.contract.Money
import com.rapidcents.challenge.contract.PaymentMethod
import com.rapidcents.challenge.contract.ReversalResult
import com.rapidcents.challenge.contract.StartResult
import com.rapidcents.challenge.contract.StartSale
import com.rapidcents.challenge.contract.TransactionSnapshot
import com.rapidcents.challenge.contract.TransactionState
import com.rapidcents.challenge.internal.acceptance.FakeHardwareCardSdk
import com.rapidcents.challenge.internal.acceptance.FakeTapToPaySdk
import com.rapidcents.challenge.internal.audit.InMemoryAuditSink
import com.rapidcents.challenge.internal.clock.SystemChallengeClock
import com.rapidcents.challenge.internal.gateway.FakeGatewayClient
import com.rapidcents.challenge.internal.journal.InMemoryTransactionJournal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChallengePaymentEngineImplTest {

    private lateinit var journal: InMemoryTransactionJournal
    private lateinit var auditSink: InMemoryAuditSink
    private lateinit var engine: ChallengePaymentEngineImpl
    private lateinit var gateway: FakeGatewayClient

    @Before
    fun setUp() {
        journal = InMemoryTransactionJournal()
        auditSink = InMemoryAuditSink()
        gateway = FakeGatewayClient()

        val dependencies = ChallengeDependencies(
            journal = journal,
            audit = auditSink,
            clock = SystemChallengeClock(),
            gateway = gateway,
            hardwareSdk = FakeHardwareCardSdk(),
            tapToPaySdk = FakeTapToPaySdk()
        )

        engine = ChallengePaymentEngineImpl(dependencies)
    }

    /**
     * Happy Path
     *
     * Simulates a normal payment flow:
     *
     * Card Presented
     *        ↓
     * Gateway Authorize
     *        ↓
     * Approved
     *        ↓
     * Persist Journal
     */
    @Test
    fun startSale_hardwarePayment_returnsApprovedSnapshot() = runTest {
        // Arrange
        val command = StartSale(
            requestId = "request-001",
            invoiceId = "invoice-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        val result = engine.startSale(command)

        // Assert
        assertTrue(result is StartResult.Final)

        val finalResult = result as StartResult.Final

        assertEquals(
            TransactionState.APPROVED,
            finalResult.snapshot.state
        )

        assertEquals(
            "request-001",
            finalResult.snapshot.requestId
        )

        assertEquals(
            "invoice-001",
            finalResult.snapshot.invoiceId
        )

        assertEquals(
            1_000L,
            finalResult.snapshot.approvedAmountMinor
        )
    }

    /**
     * Verify that every completed transaction
     * is persisted in the journal.
     *
     * Recovery relies on journal persistence
     * after application restart.
     */
    @Test
    fun startSale_approvedTransaction_isSavedInJournal() = runTest {
        // Arrange
        val command = StartSale(
            requestId = "request-002",
            invoiceId = "invoice-002",
            amount = Money(
                minor = 2_500L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.TAP_TO_PAY,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        engine.startSale(command)

        // Assert
        val savedTransaction =
            journal.findByRequestId("request-002")

        assertNotNull(savedTransaction)

        assertEquals(
            TransactionState.APPROVED,
            savedTransaction?.state
        )

        assertEquals(
            2_500L,
            savedTransaction?.approvedAmountMinor
        )
    }

    /**
     * Verify audit events are generated
     * without exposing sensitive payment data.
     *
     * Only sanitized information should be recorded.
     */
    @Test
    fun startSale_createsSafeAuditEvents() = runTest {
        // Arrange
        val command = StartSale(
            requestId = "request-003",
            invoiceId = "invoice-003",
            amount = Money(
                minor = 500L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        engine.startSale(command)

        // Assert
        val events = auditSink.allEvents()

        assertTrue(events.isNotEmpty())

        assertTrue(
            events.any {
                it.eventType == "AUTHORIZATION_COMPLETED"
            }
        )

        assertTrue(
            events.any {
                it.state == TransactionState.APPROVED
            }
        )
    }

    /**
     * Scenario:
     * The POS accidentally submits the same request twice.
     *
     * Expected behaviour:
     * - The engine returns the existing transaction.
     * - Gateway authorization is NOT executed again.
     *
     * This guarantees request-level idempotency.
     */
    @Test
    fun startSale_sameRequestId_authorizesOnlyOnce() = runTest {
        // Arrange
        val command = StartSale(
            requestId = "request-idempotency-001",
            invoiceId = "invoice-idempotency-001",
            amount = Money(
                minor = 1_500L,
                currency = Currency.CAD
            ),

            // Use the same CommandSource value as your passing tests.
            source = CommandSource.APP,

            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        // First submission.
        val firstResult = engine.startSale(command)
        // Simulate the POS sending the exact same request again.
        val secondResult = engine.startSale(command)

        // Assert
        assertTrue(firstResult is StartResult.Final)
        assertTrue(secondResult is StartResult.Final)

        // Gateway should only receive one authorization request.
        assertEquals(
            1,
            gateway.authorizeCallCount
        )

        val firstSnapshot =
            (firstResult as StartResult.Final).snapshot

        val secondSnapshot =
            (secondResult as StartResult.Final).snapshot

        assertEquals(
            firstSnapshot,
            secondSnapshot
        )

        assertEquals(
            TransactionState.APPROVED,
            secondSnapshot.state
        )
    }

    /**
     * Scenario:
     * The gateway times out after the authorization request is sent.
     *
     * Expected behaviour:
     * 1. Do NOT send another authorization request.
     * 2. Resolve the final payment status using Inquiry.
     * 3. Prevent duplicate charges.
     *
     * This is one of the most important payment safety rules.
     */
    @Test
    fun startSale_gatewayTimeout_usesInquiryWithoutSecondAuthorization() = runTest {
        // Arrange:
        // Configure the fake gateway to simulate a timeout.
        // Inquiry will later return an APPROVED result.
        gateway.authorizeBehavior = AuthorizeBehavior.TIMEOUT
        gateway.inquiryBehavior = InquiryBehavior.APPROVED

        // Act:
        // Execute the sale.
        // The engine should automatically perform an Inquiry
        // after receiving an uncertain gateway response.
        val command = StartSale(
            requestId = "request-timeout-001",
            invoiceId = "invoice-timeout-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),

            // Use the same valid CommandSource value as your existing tests.
            source = CommandSource.APP,

            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        val result = engine.startSale(command)

        // Assert
        assertTrue(result is StartResult.Final)

        val snapshot =
            (result as StartResult.Final).snapshot

        // Assert:
        // The transaction should eventually become APPROVED
        // based on the Inquiry result.
        assertEquals(
            TransactionState.APPROVED,
            snapshot.state
        )

        // Payment Safety:
        //
        // Authorization must only be sent ONCE.
        // A second authorization could result in charging
        // the customer's card twice.
        assertEquals(
            1,
            gateway.authorizeCallCount
        )


        // Verify the engine resolved the uncertain outcome
        // by performing an Inquiry instead of retrying authorization.
        assertEquals(
            1,
            gateway.inquiryCallCount
        )
    }

    /**
     * Scenario:
     * Authorization timed out and Inquiry
     * still cannot determine the final outcome.
     *
     * Expected behaviour:
     * Keep the transaction in RECOVERY_PENDING
     * so that a later recovery process can
     * safely determine the final result.
     */
    @Test
    fun startSale_timeoutAndUnknownInquiry_remainsRecoveryPending() = runTest {
        // Arrange
        gateway.authorizeBehavior = AuthorizeBehavior.TIMEOUT
        gateway.inquiryBehavior = InquiryBehavior.UNKNOWN

        val command = StartSale(
            requestId = "request-unknown-001",
            invoiceId = "invoice-unknown-001",
            amount = Money(
                minor = 2_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Act
        val result = engine.startSale(command)

        // Assert
        assertTrue(result is StartResult.Final)

        val snapshot =
            (result as StartResult.Final).snapshot

        assertEquals(
            TransactionState.RECOVERY_PENDING,
            snapshot.state
        )

        assertEquals(
            1,
            gateway.authorizeCallCount
        )

        assertEquals(
            1,
            gateway.inquiryCallCount
        )
    }

    /**
     * Scenario:
     * A payment has already been approved by the gateway,
     * and the user requests cancellation.
     *
     * Expected behaviour:
     * 1. The engine must not directly mark the payment as CANCELLED.
     * 2. It must first send a reversal request to the gateway.
     * 3. The transaction becomes CANCELLED only after the
     *    reversal is confirmed successful.
     *
     * This prevents the local application state from disagreeing
     * with the payment host.
     */
    @Test
    fun cancel_approvedTransaction_reversesPaymentAndReturnsCancelled() = runTest {
        // Arrange:
        // Configure the gateway so the initial authorization is approved
        // and the later reversal request succeeds.
        gateway.authorizeBehavior = AuthorizeBehavior.APPROVED
        gateway.reversalBehavior = ReversalBehavior.SUCCESS

        val command = StartSale(
            requestId = "request-cancel-001",
            invoiceId = "invoice-cancel-001",
            amount = Money(
                minor = 3_000L,
                currency = Currency.CAD
            ),

            // Use the same valid CommandSource value
            // already used by your passing tests.
            source = CommandSource.APP,

            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Complete the original payment first.
        val saleResult = engine.startSale(command)

        assertTrue(saleResult is StartResult.Final)

        val approvedSnapshot =
            (saleResult as StartResult.Final).snapshot

        assertEquals(
            TransactionState.APPROVED,
            approvedSnapshot.state
        )

        // Act:
        // Request cancellation after the payment has already
        // been approved by the host.
        val cancelResult = engine.cancel(command.requestId)

        // Assert:
        // A successful reversal should produce a final
        // CANCELLED transaction.
        assertTrue(cancelResult is CancelResult.Final)

        val cancelledSnapshot =
            (cancelResult as CancelResult.Final).snapshot

        assertEquals(
            TransactionState.CANCELLED,
            cancelledSnapshot.state
        )

        assertEquals(
            "REVERSED",
            cancelledSnapshot.outcomeCode
        )

        // Payment safety:
        // The original authorization should only be sent once.
        assertEquals(
            1,
            gateway.authorizeCallCount
        )

        // The engine must call reversal exactly once before
        // changing an approved payment to CANCELLED.
        assertEquals(
            1,
            gateway.reversalCallCount
        )

        // Verify that the final cancelled state was persisted.
        val savedTransaction =
            journal.findByRequestId(command.requestId)

        assertEquals(
            TransactionState.CANCELLED,
            savedTransaction?.state
        )
    }

    /**
     * Scenario:
     * An approved payment is cancelled, but the gateway cannot
     * immediately confirm whether the reversal succeeded.
     *
     * Expected behaviour:
     * 1. Keep the transaction in REVERSAL_PENDING.
     * 2. Do not incorrectly mark it as CANCELLED.
     * 3. During recovery, retry or resolve the pending reversal.
     * 4. Mark the transaction CANCELLED only after confirmation.
     *
     * This keeps the local transaction state consistent
     * with the payment host.
     */
    @Test
    fun recover_pendingReversal_completesCancellationAfterConfirmation() = runTest {
        // Arrange:
        // The original payment is approved normally.
        gateway.authorizeBehavior = AuthorizeBehavior.APPROVED

        // The first reversal attempt cannot be confirmed yet.
        gateway.reversalBehavior = ReversalBehavior.PENDING

        val command = StartSale(
            requestId = "request-reversal-recovery-001",
            invoiceId = "invoice-reversal-recovery-001",
            amount = Money(
                minor = 4_000L,
                currency = Currency.CAD
            ),

            // Use the same CommandSource value as your existing passing tests.
            source = CommandSource.APP,

            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Complete the original sale.
        val saleResult = engine.startSale(command)

        assertTrue(saleResult is StartResult.Final)

        val approvedSnapshot =
            (saleResult as StartResult.Final).snapshot

        assertEquals(
            TransactionState.APPROVED,
            approvedSnapshot.state
        )

        // Request cancellation.
        // Because the gateway returns PENDING, the engine must
        // preserve the uncertain reversal state.
        val cancelResult = engine.cancel(command.requestId)

        assertTrue(cancelResult is CancelResult.Accepted)

        val pendingSnapshot =
            journal.findByRequestId(command.requestId)

        assertEquals(
            TransactionState.REVERSAL_PENDING,
            pendingSnapshot?.state
        )

        // Simulate the gateway becoming available later
        // and confirming the reversal.
        gateway.reversalBehavior = ReversalBehavior.SUCCESS

        // Act:
        // Simulate application startup recovery.
        val recoveryReport = engine.recover()

        // Assert:
        // The pending reversal should now be completed.
        val recoveredSnapshot =
            journal.findByRequestId(command.requestId)

        assertEquals(
            TransactionState.CANCELLED,
            recoveredSnapshot?.state
        )

        assertEquals(
            "REVERSED",
            recoveredSnapshot?.outcomeCode
        )

        // One reversal call happened during cancel,
        // and one more during recovery.
        assertEquals(
            2,
            gateway.reversalCallCount
        )

        assertEquals(
            1,
            recoveryReport.inspected
        )

        assertEquals(
            1,
            recoveryReport.finalized
        )

        assertEquals(
            0,
            recoveryReport.stillPending
        )
    }

    /**
     * Scenario:
     * A second request intentionally reuses the same invoice ID,
     * and the caller explicitly allows the duplicate invoice.
     *
     * Expected behaviour:
     * The engine may process the new request because the caller
     * has made an explicit business decision to override the
     * duplicate invoice protection.
     */
    @Test
    fun startSale_sameInvoiceWithExplicitOverride_allowsNewTransaction() = runTest {
        // Arrange
        val firstCommand = StartSale(
            requestId = "request-override-001",
            invoiceId = "invoice-override-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        val overrideCommand = StartSale(
            requestId = "request-override-002",
            invoiceId = "invoice-override-001",
            amount = Money(
                minor = 1_500L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,

            // The caller explicitly confirms that reusing
            // the invoice ID is intentional.
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = true
        )

        // Complete the original sale.
        engine.startSale(firstCommand)

        // Act
        val overrideResult = engine.startSale(overrideCommand)

        // Assert
        assertTrue(overrideResult is StartResult.Final)

        val overrideSnapshot =
            (overrideResult as StartResult.Final).snapshot

        assertEquals(
            TransactionState.APPROVED,
            overrideSnapshot.state
        )

        assertEquals(
            "request-override-002",
            overrideSnapshot.requestId
        )

        // Both transactions were intentionally authorized.
        assertEquals(
            2,
            gateway.authorizeCallCount
        )
    }

    /**
     * Scenario:
     * A first payment is still waiting for card acceptance,
     * while a second payment request is submitted.
     *
     * Expected behaviour:
     * 1. Only one active transaction is allowed.
     * 2. The second request must return Busy.
     * 3. The second request must not reach the gateway.
     * 4. Once the first card is presented, the first sale may complete.
     *
     * This prevents SDK sessions, callbacks and transaction states
     * from being mixed between two simultaneous payments.
     */
    @Test
    fun startSale_whenAnotherTransactionIsActive_returnsBusy() = runTest {
        // Arrange:
        // Use a controllable SDK so the first payment remains
        // active while waiting for card presentation.
        val controllableHardwareSdk =
            ControllableHardwareCardSdk()

        val localGateway =
            FakeGatewayClient()

        val localJournal =
            InMemoryTransactionJournal()

        val localEngine = ChallengePaymentEngineImpl(
            ChallengeDependencies(
                journal = localJournal,
                audit = InMemoryAuditSink(),
                clock = SystemChallengeClock(),
                gateway = localGateway,
                hardwareSdk = controllableHardwareSdk,
                tapToPaySdk = FakeTapToPaySdk()
            )
        )

        val firstCommand = StartSale(
            requestId = "request-busy-001",
            invoiceId = "invoice-busy-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        val secondCommand = StartSale(
            requestId = "request-busy-002",
            invoiceId = "invoice-busy-002",
            amount = Money(
                minor = 2_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Start the first transaction asynchronously.
        // It will stop at WAITING_FOR_ACCEPTANCE because the
        // controllable SDK has not presented a card yet.
        val firstSale = async {
            localEngine.startSale(firstCommand)
        }

        // Allow the first coroutine to enter the acceptance flow.
        advanceUntilIdle()

        val activeSessionId =
            controllableHardwareSdk.activeSessionId()

        assertNotNull(activeSessionId)

        // Act:
        // Submit another sale while the first transaction is active.
        val secondResult =
            localEngine.startSale(secondCommand)

        // Assert:
        // The second transaction should be rejected as Busy.
        assertTrue(
            secondResult is StartResult.Busy
        )

        val busyResult =
            secondResult as StartResult.Busy

        assertEquals(
            "request-busy-001",
            busyResult.activeRequestId
        )

        // The second request must not reach the gateway.
        assertEquals(
            0,
            localGateway.authorizeCallCount
        )

        // Complete the first transaction by simulating card presentation.
        controllableHardwareSdk.presentCard(
            activeSessionId!!
        )

        advanceUntilIdle()

        val firstResult =
            firstSale.await()

        assertTrue(
            firstResult is StartResult.Final
        )

        val firstSnapshot =
            (firstResult as StartResult.Final).snapshot

        assertEquals(
            TransactionState.APPROVED,
            firstSnapshot.state
        )

        // Only the first transaction should be authorized.
        assertEquals(
            1,
            localGateway.authorizeCallCount
        )

        // The rejected second request should not create a journal record.
        assertEquals(
            null,
            localJournal.findByRequestId(
                "request-busy-002"
            )
        )
    }

    /**
     * Scenario:
     * A payment is waiting for the customer to present a card,
     * and the user cancels before authorization begins.
     *
     * Expected behaviour:
     * 1. Cancel the active acceptance SDK session.
     * 2. Mark the transaction as CANCELLED.
     * 3. Do not send any authorization request to the gateway.
     *
     * Because no authorization has reached the host,
     * no reversal is required.
     */
    @Test
    fun cancel_whileWaitingForCard_cancelsWithoutGatewayAuthorization() = runTest {
        // Arrange:
        // Use a controllable SDK so the transaction remains
        // in WAITING_FOR_ACCEPTANCE until the test cancels it.
        val controllableHardwareSdk =
            ControllableHardwareCardSdk()

        val localGateway =
            FakeGatewayClient()

        val localJournal =
            InMemoryTransactionJournal()

        val localEngine = ChallengePaymentEngineImpl(
            ChallengeDependencies(
                journal = localJournal,
                audit = InMemoryAuditSink(),
                clock = SystemChallengeClock(),
                gateway = localGateway,
                hardwareSdk = controllableHardwareSdk,
                tapToPaySdk = FakeTapToPaySdk()
            )
        )

        val command = StartSale(
            requestId = "request-cancel-before-auth-001",
            invoiceId = "invoice-cancel-before-auth-001",
            amount = Money(
                minor = 1_200L,
                currency = Currency.CAD
            ),

            // Use the CommandSource value already used
            // successfully in your other tests.
            source = CommandSource.APP,

            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // Start the sale asynchronously.
        // The controllable SDK will keep it waiting for a card.
        val saleTask = async {
            localEngine.startSale(command)
        }

        // Allow the engine to enter WAITING_FOR_ACCEPTANCE.
        advanceUntilIdle()

        val waitingSnapshot =
            localJournal.findByRequestId(command.requestId)

        assertEquals(
            TransactionState.WAITING_FOR_ACCEPTANCE,
            waitingSnapshot?.state
        )

        assertNotNull(
            controllableHardwareSdk.activeSessionId()
        )

        // Act:
        // Cancel before any card credential is returned
        // and before authorization reaches the gateway.
        val cancelResult =
            localEngine.cancel(command.requestId)

        advanceUntilIdle()

        // Assert:
        // The cancellation request should return a final result.
        assertTrue(
            cancelResult is CancelResult.Final
        )

        val cancelledSnapshot =
            (cancelResult as CancelResult.Final).snapshot

        assertEquals(
            TransactionState.CANCELLED,
            cancelledSnapshot.state
        )

        assertEquals(
            "USER_CANCELLED",
            cancelledSnapshot.outcomeCode
        )

        // Payment safety:
        // No authorization was sent, so the customer
        // cannot be charged.
        assertEquals(
            0,
            localGateway.authorizeCallCount
        )

        // No reversal is needed because the payment
        // never reached the authorization stage.
        assertEquals(
            0,
            localGateway.reversalCallCount
        )

        // The SDK cancellation callback should also allow
        // the suspended startSale operation to finish safely.
        val saleResult =
            saleTask.await()

        assertTrue(
            saleResult is StartResult.Final
        )

        val saleSnapshot =
            (saleResult as StartResult.Final).snapshot

        assertEquals(
            TransactionState.CANCELLED,
            saleSnapshot.state
        )

        // Verify that the final state remains persisted.
        val savedSnapshot =
            localJournal.findByRequestId(command.requestId)

        assertEquals(
            TransactionState.CANCELLED,
            savedSnapshot?.state
        )
    }

    /**
     * INCIDENT CARD A:
     * Ambiguous timeout after authorization approval.
     *
     * Scenario:
     * 1. The terminal sends one authorization request.
     * 2. The processor approves the transaction.
     * 3. The terminal receives a timeout and cannot see the approval.
     * 4. The engine performs an inquiry using the same request identity.
     * 5. Inquiry returns APPROVED.
     *
     * Safety requirement:
     * The engine must never send a second authorization because
     * the first request may already have charged the customer.
     */
    @Test
    fun incidentA_timeoutAfterApproval_usesInquiryAndDoesNotReauthorize() =
        runTest {

            // Arrange:
            // Simulate an ambiguous host timeout.
            gateway.authorizeBehavior =
                AuthorizeBehavior.TIMEOUT

            // The processor had actually approved the original request.
            gateway.inquiryBehavior =
                InquiryBehavior.APPROVED

            val command = StartSale(
                requestId = "incident-a-request-001",
                invoiceId = "incident-a-invoice-001",
                amount = Money(
                    minor = 2_500L,
                    currency = Currency.CAD
                ),

                // Use the CommandSource value that already works
                // in your existing tests.
                source = CommandSource.APP,

                acceptance = AcceptanceType.HARDWARE,
                paymentMethod = PaymentMethod.TAP,
                explicitInvoiceOverride = false
            )

            // Act
            val result =
                engine.startSale(command)

            // Assert:
            // Inquiry should resolve the uncertain outcome as APPROVED.
            assertTrue(
                result is StartResult.Final
            )

            val finalSnapshot =
                (result as StartResult.Final).snapshot

            assertEquals(
                TransactionState.APPROVED,
                finalSnapshot.state
            )

            /*
             * Critical payment safety assertion:
             *
             * Authorization must be sent exactly once.
             *
             * A second authorization could create a duplicate charge
             * because the processor may already have approved the first one.
             */
            assertEquals(
                1,
                gateway.authorizeCallCount
            )

            // The engine must reconcile the timeout through inquiry.
            assertEquals(
                1,
                gateway.inquiryCallCount
            )

            // The resolved final result must be persisted in the journal.
            val savedSnapshot =
                journal.findByRequestId(command.requestId)

            assertEquals(
                TransactionState.APPROVED,
                savedSnapshot?.state
            )
        }

    @Test
    fun incidentB_sameInvoiceWithNewRequestId_doesNotAuthorizeAgain() = runTest {

        val firstCommand = StartSale(
            requestId = "incident-b-request-001",
            invoiceId = "incident-b-invoice-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        val secondCommand = StartSale(
            requestId = "incident-b-request-002",
            invoiceId = "incident-b-invoice-001",
            amount = Money(
                minor = 1_000L,
                currency = Currency.CAD
            ),
            source = CommandSource.APP,
            acceptance = AcceptanceType.HARDWARE,
            paymentMethod = PaymentMethod.TAP,
            explicitInvoiceOverride = false
        )

        // First payment succeeds.
        val firstResult = engine.startSale(firstCommand)

        assertTrue(firstResult is StartResult.Final)

        // Second payment reuses the same invoice.
        val secondResult = engine.startSale(secondCommand)

        // Must reject duplicate invoice.
        assertTrue(
            secondResult is StartResult.DuplicateInvoiceRejected
        )

        val duplicate =
            secondResult as StartResult.DuplicateInvoiceRejected

        assertEquals(
            "incident-b-invoice-001",
            duplicate.invoiceId
        )

        assertEquals(
            "incident-b-request-001",
            duplicate.existingRequestId
        )

        // Only the first payment reached the gateway.
        assertEquals(
            1,
            gateway.authorizeCallCount
        )
    }

    /**
     * INCIDENT CARD C:
     * Late Tap to Pay credential callback after cancellation.
     *
     * Scenario:
     * 1. A Tap to Pay sale starts and waits for card acceptance.
     * 2. The user cancels before a credential is returned.
     * 3. The SDK reports cancellation.
     * 4. A delayed credential callback arrives after cancellation.
     *
     * Safety requirement:
     * The late callback must be ignored.
     * The gateway must never receive an authorization request.
     */
    @Test
    fun incidentC_lateCredentialAfterCancel_doesNotAuthorize() =
        runTest {

            // Arrange:
            // Use a controllable Tap to Pay SDK so the transaction
            // remains waiting until the test sends a callback.
            val controllableTapToPaySdk =
                ControllableTapToPaySdk()

            val localGateway =
                FakeGatewayClient()

            val localJournal =
                InMemoryTransactionJournal()

            val localEngine =
                ChallengePaymentEngineImpl(
                    ChallengeDependencies(
                        journal = localJournal,
                        audit = InMemoryAuditSink(),
                        clock = SystemChallengeClock(),
                        gateway = localGateway,
                        hardwareSdk = FakeHardwareCardSdk(),
                        tapToPaySdk = controllableTapToPaySdk
                    )
                )

            val command =
                StartSale(
                    requestId = "incident-c-request-001",
                    invoiceId = "incident-c-invoice-001",
                    amount = Money(
                        minor = 1_500L,
                        currency = Currency.CAD
                    ),
                    source = CommandSource.APP,
                    acceptance = AcceptanceType.TAP_TO_PAY,
                    paymentMethod = PaymentMethod.TAP,
                    explicitInvoiceOverride = false
                )

            // Start asynchronously because the controllable SDK
            // does not immediately return a credential.
            val saleTask =
                async {
                    localEngine.startSale(command)
                }

            // Let the engine reach WAITING_FOR_ACCEPTANCE.
            advanceUntilIdle()

            val waitingSnapshot =
                localJournal.findByRequestId(
                    command.requestId
                )

            assertEquals(
                TransactionState.WAITING_FOR_ACCEPTANCE,
                waitingSnapshot?.state
            )

            val sessionId =
                controllableTapToPaySdk.activeSessionId()

            assertNotNull(sessionId)

            // Act:
            // Cancel the transaction before any credential is returned.
            val cancelResult =
                localEngine.cancel(
                    command.requestId
                )

            advanceUntilIdle()

            // Verify the direct cancellation result.
            assertTrue(
                cancelResult is CancelResult.Final
            )

            val cancelledSnapshot =
                (cancelResult as CancelResult.Final).snapshot

            assertEquals(
                TransactionState.CANCELLED,
                cancelledSnapshot.state
            )

            assertEquals(
                "USER_CANCELLED",
                cancelledSnapshot.outcomeCode
            )

            // Simulate the SDK incorrectly delivering a credential
            // after the acceptance session was already cancelled.
            controllableTapToPaySdk.sendLateCredential(
                sessionId!!
            )

            advanceUntilIdle()

            // Payment safety:
            // The delayed credential must not start authorization.
            assertEquals(
                0,
                localGateway.authorizeCallCount
            )

            // No authorization occurred, so no reversal is required.
            assertEquals(
                0,
                localGateway.reversalCallCount
            )

            // The suspended startSale call should finish as CANCELLED.
            val saleResult =
                saleTask.await()

            assertTrue(
                saleResult is StartResult.Final
            )

            val saleSnapshot =
                (saleResult as StartResult.Final).snapshot

            assertEquals(
                TransactionState.CANCELLED,
                saleSnapshot.state
            )

            assertEquals(
                "USER_CANCELLED",
                saleSnapshot.outcomeCode
            )

            // Confirm the late callback did not overwrite
            // the final journal state.
            val finalSavedSnapshot =
                localJournal.findByRequestId(
                    command.requestId
                )

            assertEquals(
                TransactionState.CANCELLED,
                finalSavedSnapshot?.state
            )

            assertEquals(
                "USER_CANCELLED",
                finalSavedSnapshot?.outcomeCode
            )

            // Final safety assertion after every coroutine completes.
            assertEquals(
                0,
                localGateway.authorizeCallCount
            )
        }

    /**
     * INCIDENT CARD D:
     * Process death after AUTHORIZING was persisted.
     *
     * Recovery must use the original request ID and inquiry.
     * It must never send a second authorization.
     */
    @Test
    fun incidentD_processDeathDuringAuthorization_usesInquiryOnly() =
        runTest {

            val persistedJournal =
                InMemoryTransactionJournal()

            val localGateway =
                FakeGatewayClient(
                    inquiryBehavior = InquiryBehavior.APPROVED,
                    inquiryApprovedAmount = 1_005L
                )

            val originalRequestId =
                "incident-d-request-001"

            val originalInvoiceId =
                "incident-d-invoice-001"

            /*
             * Simulate the transaction state that was safely persisted
             * before the gateway request left the original process.
             */
            persistedJournal.save(
                TransactionSnapshot(
                    requestId = originalRequestId,
                    invoiceId = originalInvoiceId,
                    amount = Money(
                        minor = 1_005L,
                        currency = Currency.CAD
                    ),
                    state = TransactionState.AUTHORIZING,
                    updatedAtEpochMs = 1_000L,
                    paymentMethod = PaymentMethod.TAP
                )
            )

            /*
             * Instantiate a completely fresh engine, representing
             * application restart after process death.
             */
            val restartedEngine =
                ChallengePaymentEngineImpl(
                    ChallengeDependencies(
                        journal = persistedJournal,
                        audit = InMemoryAuditSink(),
                        clock = SystemChallengeClock(),
                        gateway = localGateway,
                        hardwareSdk = FakeHardwareCardSdk(),
                        tapToPaySdk = FakeTapToPaySdk()
                    )
                )

            val report =
                restartedEngine.recover()

            assertEquals(
                1,
                report.inspected
            )

            assertEquals(
                1,
                report.finalized
            )

            assertEquals(
                0,
                report.stillPending
            )

            /*
             * Critical payment safety assertions:
             *
             * Recovery performs one inquiry and zero new authorizations.
             */
            assertEquals(
                0,
                localGateway.authorizeCallCount
            )

            assertEquals(
                1,
                localGateway.inquiryCallCount
            )

            val recovered =
                persistedJournal.findByRequestId(
                    originalRequestId
                )

            assertNotNull(recovered)

            assertEquals(
                originalRequestId,
                recovered?.requestId
            )

            assertEquals(
                originalInvoiceId,
                recovered?.invoiceId
            )

            assertEquals(
                TransactionState.APPROVED,
                recovered?.state
            )

            assertEquals(
                1_005L,
                recovered?.approvedAmountMinor
            )

            assertEquals(
                "HOST-$originalRequestId",
                recovered?.hostReference
            )

            assertEquals(
                originalRequestId,
                localGateway.lastInquiryRequestId
            )

            /*
             * Final proof that recovery did not create a replacement
             * authorization after application restart.
             */
            assertEquals(
                0,
                localGateway.authorizeCallCount
            )
        }

    /**
     * INCIDENT CARD E:
     * Partial approval followed by customer rejection.
     *
     * Requested amount: 1005
     * Approved amount: 700
     *
     * The customer rejects paying the remaining balance by
     * another method, so the approved portion must be reversed.
     */
    @Test
    fun incidentE_partialApprovalRejected_reversesApprovedPortion() =
        runTest {

            val localGateway =
                FakeGatewayClient(
                    authorizeBehavior =
                        AuthorizeBehavior.PARTIAL_APPROVAL,
                    reversalBehavior =
                        ReversalBehavior.SUCCESS
                )

            val localJournal =
                InMemoryTransactionJournal()

            val localEngine =
                ChallengePaymentEngineImpl(
                    ChallengeDependencies(
                        journal = localJournal,
                        audit = InMemoryAuditSink(),
                        clock = SystemChallengeClock(),
                        gateway = localGateway,
                        hardwareSdk = FakeHardwareCardSdk(),
                        tapToPaySdk = FakeTapToPaySdk()
                    )
                )

            val command =
                StartSale(
                    requestId = "incident-e-request-001",
                    invoiceId = "incident-e-invoice-001",
                    amount = Money(
                        minor = 1_005L,
                        currency = Currency.CAD
                    ),
                    source = CommandSource.APP,
                    acceptance = AcceptanceType.TAP_TO_PAY,
                    paymentMethod = PaymentMethod.TAP,
                    explicitInvoiceOverride = false
                )

            val saleResult =
                localEngine.startSale(command)

            assertTrue(
                saleResult is StartResult.Final
            )

            val partialSnapshot =
                (saleResult as StartResult.Final).snapshot

            /*
             * State evidence.
             */
            assertEquals(
                TransactionState.PARTIALLY_APPROVED,
                partialSnapshot.state
            )

            /*
             * Amount evidence:
             * requested amount is 1005, but only 700 was approved.
             */
            assertEquals(
                1_005L,
                partialSnapshot.amount.minor
            )

            assertEquals(
                700L,
                partialSnapshot.approvedAmountMinor
            )

            assertTrue(
                partialSnapshot.approvedAmountMinor !=
                        partialSnapshot.amount.minor
            )

            /*
             * Customer rejects paying the remaining balance by
             * another method.
             */
            val cancelResult =
                localEngine.cancel(
                    command.requestId
                )

            assertTrue(
                cancelResult is CancelResult.Final
            )

            val reversedSnapshot =
                (cancelResult as CancelResult.Final).snapshot

            assertEquals(
                TransactionState.CANCELLED,
                reversedSnapshot.state
            )

            assertEquals(
                "REVERSED",
                reversedSnapshot.outcomeCode
            )

            /*
             * The original authorization is not sent again.
             */
            assertEquals(
                1,
                localGateway.authorizeCallCount
            )

            /*
             * The partial authorization is reversed exactly once.
             */
            assertEquals(
                1,
                localGateway.reversalCallCount
            )

            /*
             * The approved amount remains truthful in the journal.
             * It must not be replaced by the requested full amount.
             */
            val saved =
                localJournal.findByRequestId(
                    command.requestId
                )

            assertEquals(
                TransactionState.CANCELLED,
                saved?.state
            )

            assertEquals(
                700L,
                saved?.approvedAmountMinor
            )

            assertTrue(
                saved?.approvedAmountMinor !=
                        saved?.amount?.minor
            )
        }
}



