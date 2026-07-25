package com.rapidcents.challenge.internal.acceptance

import com.rapidcents.challenge.contract.AcceptanceEvent
import com.rapidcents.challenge.contract.AcceptanceListener
import com.rapidcents.challenge.contract.AcceptanceRequest
import com.rapidcents.challenge.contract.AndroidTapToPaySdk
import com.rapidcents.challenge.contract.EntryMode
import com.rapidcents.challenge.contract.HardwareCardSdk
import com.rapidcents.challenge.contract.OpaquePaymentCredential
import com.rapidcents.challenge.contract.TapInitializationResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple hardware SDK simulator for tests and demo.
 *
 * Never log or persist opaqueValue.
 */
class FakeHardwareCardSdk : HardwareCardSdk {

    private val activeListeners =
        ConcurrentHashMap<String, AcceptanceListener>()

    override fun start(
        request: AcceptanceRequest,
        listener: AcceptanceListener
    ) {
        activeListeners[request.sessionId] = listener

        listener.onEvent(
            AcceptanceEvent.Prompt(
                code = "PRESENT_CARD"
            )
        )

        listener.onEvent(
            AcceptanceEvent.Credential(
                value = OpaquePaymentCredential(
                    opaqueValue = "SIMULATED-HARDWARE-CREDENTIAL",
                    entryMode = EntryMode.CHIP,
                    fallbackAllowed = false,
                    providerReference = "HW-${request.requestId}"
                )
            )
        )

        activeListeners.remove(request.sessionId)
    }

    override fun cancel(sessionId: String) {
        val listener = activeListeners.remove(sessionId)

        listener?.onEvent(
            AcceptanceEvent.Cancelled
        )
    }
}

/**
 * Simple Tap to Pay SDK simulator for tests and demo.
 */
class FakeTapToPaySdk : AndroidTapToPaySdk {

    private val activeListeners =
        ConcurrentHashMap<String, AcceptanceListener>()

    override fun initialize(
        listener: (TapInitializationResult) -> Unit
    ) {
        listener(TapInitializationResult.Ready)
    }

    override fun start(
        request: AcceptanceRequest,
        listener: AcceptanceListener
    ) {
        activeListeners[request.sessionId] = listener

        listener.onEvent(
            AcceptanceEvent.Prompt(
                code = "TAP_CARD_OR_DEVICE"
            )
        )

        listener.onEvent(
            AcceptanceEvent.Credential(
                value = OpaquePaymentCredential(
                    opaqueValue = "SIMULATED-TAP-CREDENTIAL",
                    entryMode = EntryMode.CONTACTLESS,
                    fallbackAllowed = false,
                    providerReference = "TTP-${request.requestId}"
                )
            )
        )

        activeListeners.remove(request.sessionId)
    }

    override fun cancel(
        sessionId: String
    ) {
        val listener =
            activeListeners.remove(sessionId)

        listener?.onEvent(
            AcceptanceEvent.Cancelled
        )
    }
}