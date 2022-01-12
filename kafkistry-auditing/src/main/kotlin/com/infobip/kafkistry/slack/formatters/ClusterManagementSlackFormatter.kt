package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.ClusterManagementAuditEvent
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class ClusterManagementSlackFormatter : AuditEventSlackFormatter<ClusterManagementAuditEvent> {

    override fun eventType() = ClusterManagementAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: ClusterManagementAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.linksToApp(event: ClusterManagementAuditEvent): List<EventLink> {
        val inspectClusterUrl = link { clusters().showCluster(event.clusterIdentifier) }
        return listOf(EventLink(inspectClusterUrl, "Cluster in registry", "Show"))
    }
}