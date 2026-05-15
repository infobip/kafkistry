package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.TopicSensitiveDataAccessAuditEvent
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class TopicSensitiveDataAccessSlackFormatter : AuditEventSlackFormatter<TopicSensitiveDataAccessAuditEvent> {

    override fun eventType() = TopicSensitiveDataAccessAuditEvent::class.java

    override fun preferredHexColor(event: TopicSensitiveDataAccessAuditEvent): String = "#C5A100"  // amber — security-sensitive access

    override fun SlackMessageFormatter.Context.headSections(event: TopicSensitiveDataAccessAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Topic:*\n`${event.topicName}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(
        event: TopicSensitiveDataAccessAuditEvent
    ): List<LayoutBlock> {
        val fields = mutableListOf<TextObject>()
        event.partition?.let { fields.add("*Partition:*\n`$it`".asMarkdown()) }
        event.offset?.let { fields.add("*Offset:*\n`$it`".asMarkdown()) }
        event.reason?.let { fields.add("*Reason:*\n${it.sanitizeMarkdown()}".asMarkdown()) }
        if (event.maskedKeyPaths.isNotEmpty()) {
            fields.add("*Masked key paths:*\n${event.maskedKeyPaths.formatPaths()}".asMarkdown())
        }
        if (event.maskedValuePaths.isNotEmpty()) {
            fields.add("*Masked value paths:*\n${event.maskedValuePaths.formatPaths()}".asMarkdown())
        }
        if (event.maskedHeaderPaths.isNotEmpty()) {
            val headers = event.maskedHeaderPaths.entries.joinToString("\n") { (header, paths) ->
                "  • `$header`: ${paths.formatPaths()}"
            }
            fields.add("*Masked header paths:*\n$headers".asMarkdown())
        }
        return listOf(DividerBlock(), SectionBlock.builder().fields(fields).build())
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: TopicSensitiveDataAccessAuditEvent): List<EventLink> {
        val partition = event.partition
        val offset = event.offset
        val consumeUrl = if (partition != null && offset != null) {
            link {
                consumeRecords().showConsumePageForProduced(event.topicName, event.clusterIdentifier, partition, offset)
            }
        } else {
            link { consumeRecords().showConsumePage(event.topicName, event.clusterIdentifier) }
        }
        val topicUrl = link { topics().showTopic(event.topicName) }
        return listOf(
            EventLink(topicUrl, "Topic", "View topic"),
            EventLink(consumeUrl, "Consume", "Open consume page at record"),
        )
    }

    private fun Set<String>.formatPaths(): String = joinToString(", ") { "`$it`" }

    private fun String.sanitizeMarkdown(): String =
        replace("`", "'").replace("*", "·").let { trimmed ->
            if (trimmed.length > 500) trimmed.take(500) + "…" else trimmed
        }
}
