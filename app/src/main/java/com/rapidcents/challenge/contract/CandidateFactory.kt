package com.rapidcents.challenge.contract

import com.rapidcents.challenge.internal.engine.ChallengePaymentEngineImpl

object CandidateFactory {

    fun create(
        dependencies: ChallengeDependencies
    ): ChallengePaymentEngine {
        return ChallengePaymentEngineImpl(dependencies)
    }
}