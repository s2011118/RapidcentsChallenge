package com.rapidcents.challenge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rapidcents.challenge.contract.ChallengePaymentEngine
import com.rapidcents.challenge.internal.gateway.FakeGatewayClient
import com.rapidcents.challenge.internal.journal.InMemoryTransactionJournal

/**
 * Creates PaymentViewModel with its required payment engine dependency.
 *
 * The Compose UI should not manually construct the engine.
 * Dependency creation remains outside the screen and ViewModel.
 */
class PaymentViewModelFactory(
    private val engine: ChallengePaymentEngine,
    private val demoGateway: FakeGatewayClient,
    private val demoJournal: InMemoryTransactionJournal
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                PaymentViewModel::class.java
            )
        ) {
            return PaymentViewModel(
                engine = engine,
                demoGateway = demoGateway,
                demoJournal = demoJournal
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}