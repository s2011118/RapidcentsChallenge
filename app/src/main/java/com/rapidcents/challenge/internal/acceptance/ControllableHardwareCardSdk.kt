package com.rapidcents.challenge.internal.acceptance

import com.rapidcents.challenge.contract.AcceptanceEvent
import com.rapidcents.challenge.contract.AcceptanceListener
import com.rapidcents.challenge.contract.AcceptanceRequest
import com.rapidcents.challenge.contract.EntryMode
import com.rapidcents.challenge.contract.HardwareCardSdk
import com.rapidcents.challenge.contract.OpaquePaymentCredential
import java.util.concurrent.ConcurrentHashMap

/**
 * A controllable hardware SDK simulator used by concurrency tests.
 *
 * Unlike FakeHardwareCardSdk, this simulator does not immediately
 * return a credential. The test decides when the card is presented.
 *
 * This allows us to keep one payment active while submitting
 * another request to verify the terminal busy rule.
 */
class ControllableHardwareCardSdk : HardwareCardSdk {

    private val activeListeners =
        ConcurrentHashMap<String, AcceptanceListener>()

    /**
     * Keeps the payment session active and waits for the test
     * to manually simulate card presentation.
     */
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
    }

    /**
     * Simulates a successful card read for the specified session.
     *
     * The opaque credential is only passed to the engine.
     * It should never be logged or persisted.
     */
    fun presentCard(sessionId: String) {
        val listener = activeListeners.remove(sessionId)
            ?: error("No active payment session: $sessionId")

        listener.onEvent(
            AcceptanceEvent.Credential(
                value = OpaquePaymentCredential(
                    opaqueValue = "SIMULATED-CREDENTIAL",
                    entryMode = EntryMode.CHIP,
                    fallbackAllowed = false,
                    providerReference = "HW-$sessionId"
                )
            )
        )
    }

    /**
     * Returns the currently active session ID.
     *
     * This is exposed only for tests and demo simulation.
     */
    fun activeSessionId(): String? {
        return activeListeners.keys.firstOrNull()
    }

    /**
     * Simulates cancellation from the payment engine.
     */
    override fun cancel(sessionId: String) {
        val listener = activeListeners.remove(sessionId)

        listener?.onEvent(
            AcceptanceEvent.Cancelled
        )
    }
}