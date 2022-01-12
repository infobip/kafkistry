package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.TopicManagementAuditEvent
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class TopicManagementSlackFormatter : AuditEventSlackFormatter<TopicManagementAuditEvent> {

    override fun eventType() = TopicManagementAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: TopicManagementAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Topic:*\n`${event.topicName}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(
        event: TopicManagementAuditEvent
    ): List<LayoutBlock> {
        val statusBefore = event.kafkaTopicBefore
        val statusAfter = event.kafkaTopicAfter
        val fields = listOfNotNull(
            statusBefore?.let { "Status before:\n`${it.types}`".asMarkdown() },
            statusAfter?.let { "Status after:\n`${it.types}`".asMarkdown() },
        )
        return if (fields.isNotEmpty()) {
            listOf(DividerBlock(), SectionBlock.builder().fields(fields).build())
        } else {
            emptyList()
        }
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: TopicManagementAuditEvent): List<EventLink> {
        val inspectTopicUrl = link {
            topics().showInspectTopicOnCluster(event.topicName, event.clusterIdentifier)
        }
        return listOf(EventLink(inspectTopicUrl, "Topic on cluster", "Inspect topic"))
    }
}