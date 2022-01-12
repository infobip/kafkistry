package com.infobip.kafkistry.audit

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.Exception

@Component
class ManagementEventsListener(
        private val eventSubscribers: List<ManagementEventSubscriber>
) {

    private val log = LoggerFactory.getLogger(ManagementEventsListener::class.java)

    fun acceptEvent(event: AuditEvent) {
        eventSubscribers.forEach {
            try {
                 it.handleEvent(event)
            } catch (ex: Exception) {
                //if subscriber throws exception, we want to suppress it (just log) and deliver event to all subscribers
                log.error("Exception thrown by $it, suppressing it and continuing", ex)
            }
        }
    }
}