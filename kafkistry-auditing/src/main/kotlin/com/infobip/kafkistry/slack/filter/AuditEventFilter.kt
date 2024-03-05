package com.infobip.kafkistry.slack.filter

import com.infobip.kafkistry.audit.AuditEvent

interface AuditEventFilter {

    fun accepts(auditEvent: AuditEvent): Boolean

    companion object {

        private val ACCEPT_ALL: AuditEventFilter = AcceptAllFilter

        fun allOf(filters: List<AuditEventFilter>): AuditEventFilter {
            return when (filters.size) {
                0 -> ACCEPT_ALL
                1 -> filters.first()
                else -> MatchAllFilter(filters)
            }
        }

    }
}

private object AcceptAllFilter : AuditEventFilter {
    override fun accepts(auditEvent: AuditEvent): Boolean = true
}

private class MatchAllFilter(private val filters: List<AuditEventFilter>) : AuditEventFilter {
    override fun accepts(auditEvent: AuditEvent): Boolean {
        return filters.all { it.accepts(auditEvent) }
    }
}
