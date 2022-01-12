package com.infobip.kafkistry.audit

interface ManagementEventSubscriber {

    fun handleEvent(event: AuditEvent)
}