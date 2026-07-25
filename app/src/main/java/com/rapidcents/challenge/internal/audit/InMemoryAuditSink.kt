package com.rapidcents.challenge.internal.audit

import com.rapidcents.challenge.contract.AuditSink
import com.rapidcents.challenge.contract.SafeAuditEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe in-memory audit sink.
 *
 * Only SafeAuditEvent is accepted, so sensitive payment credentials
 * should never be written here.
 */
class InMemoryAuditSink : AuditSink {

    private val events = CopyOnWriteArrayList<SafeAuditEvent>()

    override fun record(event: SafeAuditEvent) {
        events.add(event)
    }

    /**
     * Returns an immutable copy for tests or the demo UI.
     */
    fun allEvents(): List<SafeAuditEvent> {
        return events.toList()
    }

    fun clear() {
        events.clear()
    }
}