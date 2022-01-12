package com.infobip.kafkistry.slack

import com.infobip.kafkistry.audit.AuditEvent
import com.infobip.kafkistry.audit.ManagementEventSubscriber
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.slack.enabled")
class ManagementEventSlackSubscriber(
    private val slackSender: SlackSender,
    private val slackMessageFormatter: SlackMessageFormatter
) : ManagementEventSubscriber {

    override fun handleEvent(event: AuditEvent) {
        val slackMessage = slackMessageFormatter.generateSlackMessage(event)
        slackSender.sendMessage(slackMessage)
    }
}