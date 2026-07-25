package com.rapidcents.challenge

import com.rapidcents.challenge.contract.CandidateFactory
import com.rapidcents.challenge.contract.ChallengeDependencies
import com.rapidcents.challenge.contract.ChallengePaymentEngine
import com.rapidcents.challenge.internal.acceptance.FakeHardwareCardSdk
import com.rapidcents.challenge.internal.acceptance.FakeTapToPaySdk
import com.rapidcents.challenge.internal.audit.InMemoryAuditSink
import com.rapidcents.challenge.internal.clock.SystemChallengeClock
import com.rapidcents.challenge.internal.gateway.FakeGatewayClient
import com.rapidcents.challenge.internal.journal.InMemoryTransactionJournal

/**
 * Creates and owns the dependencies used by the demo application.
 *
 * In production, these fake implementations could be replaced by:
 * - a real payment gateway client
 * - a real hardware terminal SDK
 * - a real Tap to Pay SDK
 * - persistent encrypted storage
 *
 * The payment engine and UI do not need to change when the
 * implementations are replaced.
 */
object DemoAppContainer {

    val journal =
        InMemoryTransactionJournal()

    val auditSink =
        InMemoryAuditSink()

    val gateway =
        FakeGatewayClient()

    val hardwareSdk =
        FakeHardwareCardSdk()

    val tapToPaySdk =
        FakeTapToPaySdk()

    val clock =
        SystemChallengeClock()

    val engine: ChallengePaymentEngine by lazy {
        CandidateFactory.create(
            dependencies = ChallengeDependencies(
                journal = journal,
                audit = auditSink,
                clock = clock,
                gateway = gateway,
                hardwareSdk = hardwareSdk,
                tapToPaySdk = tapToPaySdk
            )
        )
    }
}