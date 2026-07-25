package com.rapidcents.challenge.contract

interface TransactionJournal {

    suspend fun save(
        snapshot: TransactionSnapshot
    )

    suspend fun findByRequestId(
        requestId: String
    ): TransactionSnapshot?

    suspend fun findLatestByInvoiceId(
        invoiceId: String
    ): TransactionSnapshot?

    suspend fun unfinished(): List<TransactionSnapshot>
}

fun interface AuditSink {

    fun record(event: SafeAuditEvent)
}

data class SafeAuditEvent(
    val timestampEpochMs: Long,
    val correlationId: String,
    val requestId: String,
    val invoiceIdHashPrefix: String,
    val state: TransactionState,
    val eventType: String,
    val outcomeCode: String? = null,
    val provider: AcceptanceType? = null,
    val durationMs: Long? = null
)

interface ChallengeClock {

    fun nowEpochMs(): Long

    suspend fun delay(ms: Long)
}

data class ChallengeDependencies(
    val hardwareSdk: HardwareCardSdk,
    val tapToPaySdk: AndroidTapToPaySdk,
    val gateway: GatewayClient,
    val journal: TransactionJournal,
    val audit: AuditSink,
    val clock: ChallengeClock
)