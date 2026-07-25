package com.rapidcents.challenge.internal.acceptance

import com.rapidcents.challenge.contract.AcceptanceEvent
import com.rapidcents.challenge.contract.AcceptanceListener
import com.rapidcents.challenge.contract.AcceptanceRequest
import com.rapidcents.challenge.contract.AndroidTapToPaySdk
import com.rapidcents.challenge.contract.EntryMode
import com.rapidcents.challenge.contract.OpaquePaymentCredential
import com.rapidcents.challenge.contract.TapInitializationResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Controllable Tap to Pay SDK used for race-condition tests.
 *
 * It does not automatically return a credential.
 * The test decides when a normal or late callback is delivered.
 */
class ControllableTapToPaySdk : AndroidTapToPaySdk {

    private val activeListeners =
        ConcurrentHashMap<String, AcceptanceListener>()

    private val activeRequests =
        ConcurrentHashMap<String, AcceptanceRequest>()

    /**
     * Stores cancelled sessions so a test can simulate
     * a delayed SDK callback arriving after cancellation.
     */
    private val cancelledListeners =
        ConcurrentHashMap<String, AcceptanceListener>()

    private val cancelledRequests =
        ConcurrentHashMap<String, AcceptanceRequest>()

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
        activeRequests[request.sessionId] = request

        listener.onEvent(
            AcceptanceEvent.Prompt(
                code = "TAP_CARD_OR_DEVICE"
            )
        )
    }

    override fun cancel(
        sessionId: String
    ) {
        val listener =
            activeListeners.remove(sessionId)

        val request =
            activeRequests.remove(sessionId)

        /*
         * Keep a test-only reference to simulate an SDK that
         * incorrectly delivers a credential after cancellation.
         */
        if (listener != null && request != null) {
            cancelledListeners[sessionId] = listener
            cancelledRequests[sessionId] = request
        }

        listener?.onEvent(
            AcceptanceEvent.Cancelled
        )
    }

    /**
     * Simulates a normal credential callback while
     * the acceptance session is still active.
     */
    fun presentCredential(
        sessionId: String
    ) {
        val listener = activeListeners[sessionId]
            ?: return

        val request = activeRequests[sessionId]
            ?: return

        activeListeners.remove(sessionId)
        activeRequests.remove(sessionId)

        listener.onEvent(
            createCredentialEvent(
                request = request,
                opaqueValue = "SIMULATED-TAP-CREDENTIAL"
            )
        )
    }

    /**
     * Simulates Incident C:
     *
     * The SDK sends a credential callback after the engine
     * has already cancelled the acceptance session.
     */
    fun sendLateCredential(
        sessionId: String
    ) {
        val listener =
            cancelledListeners.remove(sessionId)
                ?: return

        val request =
            cancelledRequests.remove(sessionId)
                ?: return

        listener.onEvent(
            createCredentialEvent(
                request = request,
                opaqueValue = "LATE-TAP-CREDENTIAL"
            )
        )
    }

    fun activeSessionId(): String? {
        return activeListeners.keys.firstOrNull()
    }

    private fun createCredentialEvent(
        request: AcceptanceRequest,
        opaqueValue: String
    ): AcceptanceEvent.Credential {
        return AcceptanceEvent.Credential(
            value = OpaquePaymentCredential(
                opaqueValue = opaqueValue,
                entryMode = EntryMode.CONTACTLESS,
                fallbackAllowed = false,
                providerReference = "TTP-${request.requestId}"
            )
        )
    }
}