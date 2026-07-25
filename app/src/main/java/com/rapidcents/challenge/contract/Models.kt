package com.rapidcents.challenge.contract

enum class Currency {
    CAD,
    USD
}

enum class CommandSource {
    STANDALONE,
    ECR,
    APP
}

enum class AcceptanceType {
    HARDWARE,
    TAP_TO_PAY
}

enum class EntryMode {
    CHIP,
    CONTACTLESS,
    MAGSTRIPE,
    MOBILE_WALLET
}

data class Money(
    val minor: Long,
    val currency: Currency
)

data class StartSale(
    val requestId: String,
    val invoiceId: String,
    val amount: Money,
    val source: CommandSource,
    val acceptance: AcceptanceType,
    val explicitInvoiceOverride: Boolean = false,
    val paymentMethod: PaymentMethod,
    )

enum class TransactionState {
    VALIDATING,
    WAITING_FOR_ACCEPTANCE,
    CARD_PRESENTED,
    AUTHORIZING,
    RESOLVING_UNKNOWN,
    APPROVED,
    DECLINED,
    PARTIALLY_APPROVED,
    CANCELLING,
    REVERSAL_PENDING,
    CANCELLED,
    RECOVERY_PENDING,
    FAILED
}

data class TransactionSnapshot(
    val requestId: String,
    val invoiceId: String,
    val amount: Money,
    val state: TransactionState,
    val updatedAtEpochMs: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.TAP,
    val approvedAmountMinor: Long? = null,
    val hostReference: String? = null,
    val outcomeCode: String? = null,
    val message: String? = null
)