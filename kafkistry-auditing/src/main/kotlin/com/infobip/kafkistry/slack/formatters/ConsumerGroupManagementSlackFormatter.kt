package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.infobip.kafkistry.audit.ConsumerGroupManagementAuditEvent
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class ConsumerGroupManagementSlackFormatter : AuditEventSlackFormatter<ConsumerGroupManagementAuditEvent> {

    override fun eventType() = ConsumerGroupManagementAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: ConsumerGroupManagementAuditEvent) =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Consumer group:*\n`${event.consumerGroupId}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: ConsumerGroupManagementAuditEvent): List<LayoutBlock> {
        return listOfNotNull(
            DividerBlock(),
            event.consumerGroupOffsetsReset?.let { reset ->
                val resetText = with(reset) {
                    "Reset type: `${seek.type}`\n" +
                            "Clone from group: `${seek.cloneFromConsumerGroup}`\n"
                                .takeIf { seek.cloneFromConsumerGroup != null }
                                .orEmpty() +
                            "Topic(s): \n" +
                            topics.joinToString("\n") { " - " + it.topic }
                }
                SectionBlock.builder()
                    .text(resetText.asMarkdown())
                    .build()
            },
            event.consumerGroupOffsetChange?.let { change ->
                val messages = listOfNotNull(
                    "Total skip forward: `${change.totalSkip}` record(s)".takeIf { change.totalSkip > 0 },
                    "Total rewind backward: `${change.totalRewind}` record(s)".takeIf { change.totalRewind > 0 }
                ).takeIf { it.isNotEmpty() } ?: listOf("No changes to offsets")
                SectionBlock.builder()
                    .text(messages.joinToString("\n").asMarkdown())
                    .build()
            },
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: ConsumerGroupManagementAuditEvent): List<EventLink> {
        val inspectGroupUrl = link {
            consumerGroups().showConsumerGroup(event.clusterIdentifier, event.consumerGroupId)
        }
        return listOf(EventLink(inspectGroupUrl, "Group on cluster", "Inspect group"))
    }
}