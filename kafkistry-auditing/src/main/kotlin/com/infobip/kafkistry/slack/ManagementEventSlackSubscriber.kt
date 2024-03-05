package com.infobip.kafkistry.slack

import com.infobip.kafkistry.audit.AuditEvent
import com.infobip.kafkistry.audit.ManagementEventSubscriber
import com.infobip.kafkistry.slack.filter.AuditEventFilterFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.slack.enabled")
class ManagementEventSlackSubscriber(
    private val slackSender: SlackSender,
    private val slackMessageFormatter: SlackMessageFormatter,
    auditEventFilterFactory: AuditEventFilterFactory,
) : ManagementEventSubscriber {

    private val filter = auditEventFilterFactory.createFilter()

    override fun handleEvent(event: AuditEvent) {
        if (!filter.accepts(event)) {
            return
        }
        val slackMessage = slackMessageFormatter.generateSlackMessage(event)
        slackSender.sendMessage(slackMessage)
    }
}