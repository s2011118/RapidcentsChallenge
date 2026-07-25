package com.rapidcents.challenge.internal.journal

import com.rapidcents.challenge.contract.TransactionJournal
import com.rapidcents.challenge.contract.TransactionSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory transaction journal used by the demo app
 * and unit tests.
 *
 * Important:
 * This implementation is not durable. All records disappear
 * when the app process is terminated.
 */
class InMemoryTransactionJournal : TransactionJournal {

    /**
     * requestId is the unique key for each transaction.
     *
     * ConcurrentHashMap is used because payment operations,
     * cancellation and recovery may run from different coroutines.
     */
    private val transactions =
        ConcurrentHashMap<String, TransactionSnapshot>()

    /**
     * Saves a new transaction or replaces the existing snapshot
     * with the same request ID.
     *
     * This allows one transaction record to move through states such as:
     *
     * WAITING_FOR_ACCEPTANCE
     * → AUTHORIZING
     * → APPROVED
     * → CANCELLED
     */
    override suspend fun save(
        snapshot: TransactionSnapshot
    ) {
        transactions[snapshot.requestId] = snapshot
    }

    /**
     * Finds a transaction using the technical idempotency key.
     */
    override suspend fun findByRequestId(
        requestId: String
    ): TransactionSnapshot? {
        return transactions[requestId]
    }

    /**
     * Finds the latest transaction using the same business invoice ID.
     *
     * This supports duplicate invoice protection.
     */
    override suspend fun findLatestByInvoiceId(
        invoiceId: String
    ): TransactionSnapshot? {
        return transactions.values
            .filter { it.invoiceId == invoiceId }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    /**
     * Returns transactions that have not yet reached a final state.
     *
     * These records may require inquiry or reversal recovery.
     */
    override suspend fun unfinished():
            List<TransactionSnapshot> {

        return transactions.values.filter { snapshot ->
            !snapshot.state.isFinalState()
        }
    }

    /**
     * Returns a safe copy of all transaction snapshots.
     *
     * This method is used by the demo UI to display transaction history.
     * Returning a List prevents the UI from directly modifying the
     * journal's internal map.
     */
    fun allTransactions(): List<TransactionSnapshot> {
        return transactions.values.toList()
    }
}

/**
 * Final states do not require restart recovery.
 */
private fun com.rapidcents.challenge.contract.TransactionState.isFinalState(): Boolean {
    return when (this) {
        com.rapidcents.challenge.contract.TransactionState.APPROVED,
        com.rapidcents.challenge.contract.TransactionState.DECLINED,
        com.rapidcents.challenge.contract.TransactionState.CANCELLED,
        com.rapidcents.challenge.contract.TransactionState.FAILED -> true

        else -> false
    }
}

