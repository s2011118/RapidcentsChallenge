package com.rapidcents.challenge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapidcents.challenge.contract.AcceptanceType
import com.rapidcents.challenge.contract.PaymentMethod
import com.rapidcents.challenge.internal.journal.InMemoryTransactionJournal
import com.rapidcents.challenge.internal.gateway.AuthorizeBehavior
import com.rapidcents.challenge.internal.gateway.InquiryBehavior
import com.rapidcents.challenge.internal.gateway.ReversalBehavior
import com.rapidcents.challenge.internal.gateway.FakeGatewayClient
import com.rapidcents.challenge.contract.CancelResult
import com.rapidcents.challenge.contract.ChallengePaymentEngine
import com.rapidcents.challenge.contract.CommandSource
import com.rapidcents.challenge.contract.Currency
import com.rapidcents.challenge.contract.Money
import com.rapidcents.challenge.contract.StartResult
import com.rapidcents.challenge.contract.StartSale
import com.rapidcents.challenge.contract.TransactionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Coordinates the payment screen with the core payment engine.
 *
 * The ViewModel handles presentation-related state only.
 * Payment rules, authorization, recovery and idempotency
 * remain inside ChallengePaymentEngine.
 */
class PaymentViewModel(
    private val engine: ChallengePaymentEngine,
    private val demoGateway: FakeGatewayClient,
    private val demoJournal: InMemoryTransactionJournal
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(PaymentUiState())

    val uiState: StateFlow<PaymentUiState> =
        _uiState.asStateFlow()

    /**
     * Updates the invoice field without starting any payment logic.
     */
    fun onInvoiceIdChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            invoiceId = value,
            errorMessage = null
        )
    }

    /**
     * Keeps the raw amount text in the UI state.
     *
     * Conversion to minor units is performed only when
     * the operator starts the sale.
     */
    fun onAmountChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            amountText = value,
            errorMessage = null
        )
    }

    fun onAcceptanceTypeChanged(value: AcceptanceType) {
        _uiState.value = _uiState.value.copy(
            acceptanceType = value,
            errorMessage = null
        )
    }

    fun onInvoiceOverrideChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            explicitInvoiceOverride = enabled
        )
    }

    /**
     * Changes the fake Gateway response for UI demonstration.
     *
     * This configuration is outside the payment engine so the
     * production payment logic remains unchanged.
     */
    fun onDemoScenarioChanged(
        scenario: DemoScenario
    ) {
        if (_uiState.value.isProcessing) {
            return
        }

        configureGatewayScenario(scenario)

        _uiState.value = _uiState.value.copy(
            demoScenario = scenario,
            statusMessage =
                "Scenario: ${scenario.displayName}",
            errorMessage = null
        )
    }

    /**
     * Opens the payment-method selection interface.
     *
     * No transaction is sent to the engine at this stage.
     */
    fun continueToPaymentOptions() {
        val currentState = uiState.value

        if (currentState.invoiceId.isBlank()) {
            updateError(
                "Invoice ID is required."
            )
            return
        }

        val amount =
            currentState.amountText
                .trim()
                .toBigDecimalOrNull()

        if (amount == null ||
            amount <= java.math.BigDecimal.ZERO
        ) {
            updateError(
                "Enter a valid amount."
            )
            return
        }

        _uiState.value =
            currentState.copy(
                currentCheckoutStep =
                    CheckoutStep.PAYMENT_OPTIONS,
                errorMessage = null
            )
    }

    /**
     * Stores the payment entry method selected by the operator.
     */
    fun onPaymentMethodChanged(
        paymentMethod: PaymentMethod
    ) {
        val acceptanceType =
            when (paymentMethod) {
                PaymentMethod.TAP ->
                    AcceptanceType.TAP_TO_PAY

                PaymentMethod.CHIP,
                PaymentMethod.SWIPE,
                PaymentMethod.KEY_IN ->
                    AcceptanceType.HARDWARE
            }

        _uiState.value =
            _uiState.value.copy(
                selectedPaymentMethod = paymentMethod,
                acceptanceType = acceptanceType,
                errorMessage = null
            )
    }

    /**
     * Returns to the invoice and amount interface.
     */
    fun backToPaymentDetails() {
        _uiState.value =
            _uiState.value.copy(
                currentCheckoutStep =
                    CheckoutStep.PAYMENT_DETAILS
            )
    }



    /**
     * Validates UI input and submits a new sale to the engine.
     *
     * The request ID is generated once for this operator action.
     * If the same command is retried, the engine's idempotency
     * protection prevents a second authorization.
     */
    fun startSale() {
        val currentState = _uiState.value
        val paymentMethod =
            currentState.selectedPaymentMethod

        if (paymentMethod == null) {
            _uiState.value =
                currentState.copy(
                    errorMessage =
                        "Select a payment method first."
                )

            return
        }
        if (currentState.isProcessing) {
            return
        }

        val invoiceId =
            currentState.invoiceId.trim()

        if (invoiceId.isBlank()) {
            _uiState.value = currentState.copy(
                errorMessage = "Invoice ID is required."
            )
            return
        }

        val amountMinor =
            parseAmountToMinorUnits(currentState.amountText)

        if (amountMinor == null || amountMinor <= 0L) {
            _uiState.value = currentState.copy(
                errorMessage = "Enter a valid amount greater than zero."
            )
            return
        }


        val command =
            StartSale(
                requestId = UUID.randomUUID().toString(),
                invoiceId = invoiceId,
                amount = Money(
                    minor = amountMinor,
                    currency = Currency.CAD
                ),
                source = CommandSource.APP,
                acceptance = currentState.acceptanceType,
                paymentMethod = paymentMethod,
                explicitInvoiceOverride =
                    currentState.explicitInvoiceOverride
            )

        _uiState.value = currentState.copy(
            isProcessing = true,
            currentTransaction = null,
            statusMessage = "Starting payment...",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val result =
                    engine.startSale(command)

                handleStartResult(result)
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Payment failed",
                    errorMessage =
                        exception.message
                            ?: "Unexpected payment error."
                )
            }
        }
    }

    /**
     * Cancels the transaction currently displayed on screen.
     *
     * The engine decides whether it only needs to cancel the
     * acceptance SDK or whether a gateway reversal is required.
     */
    fun cancelCurrentTransaction() {
        val requestId =
            _uiState.value.currentTransaction?.requestId

        if (requestId == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage =
                    "There is no transaction available to cancel."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            statusMessage = "Cancelling transaction...",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                when (
                    val result = engine.cancel(requestId)
                ) {
                    is CancelResult.Final -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            currentTransaction = result.snapshot,
                            statusMessage =
                                stateMessage(result.snapshot.state),
                            errorMessage = null
                        )
                        refreshTransactionHistory()

                    }

                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            statusMessage =
                                "Cancellation was not completed.",
                            errorMessage =
                                "Check the transaction state."
                        )
                    }
                }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Cancellation failed",
                    errorMessage =
                        exception.message
                            ?: "Unexpected cancellation error."
                )
            }
        }
    }

    /**
     * Runs recovery for unfinished persisted transactions.
     *
     * This is useful after an application restart or an uncertain
     * gateway result, where the engine must reconcile state safely.
     */
    fun recoverTransactions() {
        if (_uiState.value.isProcessing) {
            return
        }

        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            statusMessage = "Running recovery...",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val report =
                    engine.recover()

                refreshTransactionHistory()

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage =
                        "Recovery completed: $report",
                    errorMessage = null
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Recovery failed",
                    errorMessage =
                        exception.message
                            ?: "Unexpected recovery error."
                )
            }
        }
    }



    private fun handleStartResult(
        result: StartResult
    ) {
        when (result) {
            is StartResult.Final -> {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    currentTransaction = result.snapshot,
                    statusMessage =
                        stateMessage(result.snapshot.state),
                    errorMessage = null
                )
                refreshTransactionHistory()
            }

            is StartResult.Busy -> {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Terminal is busy",
                    errorMessage =
                        "Another payment is already active."
                )
            }

            /*
             * Add the exact duplicate-invoice result type from
             * your contract here if your StartResult defines one.
             *
             * Example:
             *
             * is StartResult.DuplicateInvoiceRejected -> {
             *     _uiState.value = _uiState.value.copy(
             *         isProcessing = false,
             *         statusMessage = "Duplicate invoice",
             *         errorMessage =
             *             "This invoice has already been used."
             *     )
             * }
             */

            else -> {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "Payment not completed",
                    errorMessage =
                        "The engine returned a non-final result."
                )
            }
        }
    }

    /**
     * Converts a decimal display amount into minor units.
     *
     * Example:
     * "10.50" becomes 1050.
     *
     * BigDecimal is used instead of Double to avoid
     * floating-point rounding errors in payment amounts.
     */
    private fun parseAmountToMinorUnits(
        input: String
    ): Long? {
        return try {
            BigDecimal(input.trim())
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    private fun stateMessage(
        state: TransactionState
    ): String {
        return when (state) {
            TransactionState.APPROVED ->
                "Payment approved"

            TransactionState.DECLINED ->
                "Payment declined"

            TransactionState.CANCELLED ->
                "Payment cancelled"

            TransactionState.RECOVERY_PENDING ->
                "Recovery required"

            else ->
                state.name
                    .lowercase()
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
        }
    }

    private fun configureGatewayScenario(
        scenario: DemoScenario
    ) {
        when (scenario) {
            DemoScenario.APPROVED -> {
                demoGateway.authorizeBehavior =
                    AuthorizeBehavior.APPROVED

                demoGateway.inquiryBehavior =
                    InquiryBehavior.APPROVED

                demoGateway.reversalBehavior =
                    ReversalBehavior.SUCCESS
            }

            DemoScenario.DECLINED -> {
                demoGateway.authorizeBehavior =
                    AuthorizeBehavior.DECLINED

                demoGateway.inquiryBehavior =
                    InquiryBehavior.DECLINED

                demoGateway.reversalBehavior =
                    ReversalBehavior.SUCCESS
            }

            DemoScenario.TIMEOUT_INQUIRY_APPROVED -> {
                demoGateway.authorizeBehavior =
                    AuthorizeBehavior.TIMEOUT

                demoGateway.inquiryBehavior =
                    InquiryBehavior.APPROVED

                demoGateway.reversalBehavior =
                    ReversalBehavior.SUCCESS
            }

            DemoScenario.TIMEOUT_RECOVERY_PENDING -> {
                demoGateway.authorizeBehavior =
                    AuthorizeBehavior.TIMEOUT

                demoGateway.inquiryBehavior =
                    InquiryBehavior.UNKNOWN

                demoGateway.reversalBehavior =
                    ReversalBehavior.SUCCESS
            }

            DemoScenario.REVERSAL_PENDING -> {
                demoGateway.authorizeBehavior =
                    AuthorizeBehavior.APPROVED

                demoGateway.inquiryBehavior =
                    InquiryBehavior.APPROVED

                demoGateway.reversalBehavior =
                    ReversalBehavior.PENDING
            }
        }
    }

    /**
     * Loads the latest journal records into the UI state.
     *
     * The journal remains the source of truth.
     * The UI does not create or modify transaction records directly.
     */
    private fun refreshTransactionHistory() {
        _uiState.value = _uiState.value.copy(
            transactionHistory =
                demoJournal.allTransactions()
                    .sortedByDescending { it.requestId }
        )
    }

    private fun updateError(
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = message
            )
    }
}