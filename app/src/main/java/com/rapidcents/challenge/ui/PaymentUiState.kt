package com.rapidcents.challenge.ui

import com.rapidcents.challenge.contract.AcceptanceType
import com.rapidcents.challenge.contract.TransactionSnapshot
import com.rapidcents.challenge.contract.PaymentMethod

/**
 * Represents everything currently displayed on the payment screen.
 *
 * The UI reads this immutable state from the ViewModel.
 * User actions are sent back to the ViewModel instead of directly
 * calling the payment engine from the Compose screen.
 */
data class PaymentUiState(

    /**
     * Invoice ID entered by the operator.
     */
    val invoiceId: String = "INV-001",

    /**
     * Amount entered in major currency units.
     *
     * Example:
     * "10.50" will later be converted to 1050 minor units.
     */
    val amountText: String = "10.00",

    /**
     * Selected card acceptance provider.
     */
    val acceptanceType: AcceptanceType = AcceptanceType.HARDWARE,

    /**
     * Prevents accidental duplicate invoices unless the operator
     * intentionally enables the override.
     */
    val explicitInvoiceOverride: Boolean = false,

    // Controls the fake Gateway behaviour used by the demo.
    val demoScenario: DemoScenario =
        DemoScenario.APPROVED,

    /**
     * Indicates that a payment operation is currently running.
     */
    val isProcessing: Boolean = false,

    /**
     * Latest transaction returned by the payment engine.
     */
    val currentTransaction: TransactionSnapshot? = null,

    /**
     * User-readable status shown on the screen.
     */
    val statusMessage: String = "Ready",

    /**
     * Validation or processing error shown to the operator.
     */
    val errorMessage: String? = null,

    /**
     * Transaction history loaded from the journal.
     */
    val transactionHistory: List<TransactionSnapshot> = emptyList(),

    val selectedPaymentMethod: PaymentMethod? = null,
    val currentCheckoutStep: CheckoutStep =
        CheckoutStep.PAYMENT_DETAILS
)

enum class CheckoutStep {
    PAYMENT_DETAILS,
    PAYMENT_OPTIONS
}