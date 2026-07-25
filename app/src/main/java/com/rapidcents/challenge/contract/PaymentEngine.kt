package com.rapidcents.challenge.contract

import kotlinx.coroutines.flow.Flow

sealed interface StartResult {

    data class Final(
        val snapshot: TransactionSnapshot
    ) : StartResult

    data class Busy(
        val activeRequestId: String
    ) : StartResult

    data class DuplicateInvoiceRejected(
        val invoiceId: String,
        val existingRequestId: String,
        val existingState: TransactionState
    ) : StartResult

    data class Rejected(
        val code: String,
        val message: String
    ) : StartResult
}

sealed interface CancelResult {

    data class Final(
        val snapshot: TransactionSnapshot
    ) : CancelResult

    data class Accepted(
        val requestId: String
    ) : CancelResult

    data class NotFound(
        val requestId: String
    ) : CancelResult

    data class Rejected(
        val code: String,
        val message: String
    ) : CancelResult
}

data class RecoveryReport(
    val inspected: Int,
    val finalized: Int,
    val stillPending: Int,
    val failures: List<String>
)

interface ChallengePaymentEngine {

    suspend fun startSale(
        command: StartSale
    ): StartResult

    suspend fun cancel(
        requestId: String
    ): CancelResult

    suspend fun status(
        requestId: String
    ): TransactionSnapshot?

    suspend fun recover(): RecoveryReport

    fun events(): Flow<SafeAuditEvent>
}