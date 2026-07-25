package com.rapidcents.challenge.contract

data class AcceptanceRequest(
    val sessionId: String,
    val requestId: String,
    val amount: Money
)

data class OpaquePaymentCredential(
    val opaqueValue: String, // simulator only; never log or persist
    val entryMode: EntryMode,
    val fallbackAllowed: Boolean = false,
    val providerReference: String? = null
)

sealed interface AcceptanceEvent {

    data class Prompt(
        val code: String
    ) : AcceptanceEvent

    data class Credential(
        val value: OpaquePaymentCredential
    ) : AcceptanceEvent

    data class Error(
        val code: String,
        val retryable: Boolean
    ) : AcceptanceEvent

    data object Cancelled : AcceptanceEvent
}

fun interface AcceptanceListener {

    fun onEvent(event: AcceptanceEvent)
}

interface HardwareCardSdk {

    fun start(
        request: AcceptanceRequest,
        listener: AcceptanceListener
    )

    fun cancel(
        sessionId: String
    )
}

interface AndroidTapToPaySdk {

    fun initialize(
        listener: (TapInitializationResult) -> Unit
    )

    fun start(
        request: AcceptanceRequest,
        listener: AcceptanceListener
    )

    fun cancel(
        sessionId: String
    )
}

sealed interface TapInitializationResult {

    data object Ready : TapInitializationResult

    data class Unsupported(
        val reason: String
    ) : TapInitializationResult

    data class AttestationFailed(
        val code: String
    ) : TapInitializationResult

    data class Failed(
        val code: String
    ) : TapInitializationResult
}