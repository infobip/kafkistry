package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.TopicManualProduceAuditEvent
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class TopicManualProduceSlackFormatter : AuditEventSlackFormatter<TopicManualProduceAuditEvent> {

    override fun eventType() = TopicManualProduceAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: TopicManualProduceAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Topic:*\n`${event.topicName}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(
        event: TopicManualProduceAuditEvent
    ): List<LayoutBlock> {
        val fields = mutableListOf<TextObject>()

        // Key info
        if (event.keySerializerType != null) {
            val keyContent = event.keyContent ?: ""
            fields.add("*Key:*\n`${event.keySerializerType}`: `$keyContent`".asMarkdown())
        } else {
            fields.add("*Key:*\n`null`".asMarkdown())
        }

        // Value info
        if (event.valueSerializerType != null) {
            val valueContent = event.valueContent ?: ""
            fields.add("*Value:*\n`${event.valueSerializerType}`: \n```\n$valueContent\n```".asMarkdown())
        } else {
            fields.add("*Value:*\n`null (tombstone)`".asMarkdown())
        }

        // Headers
        if (event.headers.isNotEmpty()) {
            val headersText = event.headers.joinToString("\n") { header ->
                if (header.valueSerializerType != null) {
                    val content = header.valueContent ?: ""
                    "  • `${header.key}`: `${header.valueSerializerType}`: `$content`"
                } else {
                    "  • `${header.key}`: `null`"
                }
            }
            fields.add("*Headers (${event.headers.size}):*\n$headersText".asMarkdown())
        }

        // Request details
        if (event.partition != null) {
            fields.add("*Requested partition:*\n`${event.partition}`".asMarkdown())
        }
        if (event.timestamp != null) {
            fields.add("*Requested timestamp:*\n`${event.timestamp}`".asMarkdown())
        }

        // Result info
        val success = event.produceSuccess ?: false
        if (success) {
            fields.add("*Result:*\n✅ Success".asMarkdown())
            event.producePartition?.let {
                fields.add("*Partition:*\n`$it`".asMarkdown())
            }
            event.produceOffset?.let {
                fields.add("*Offset:*\n`$it`".asMarkdown())
            }
            event.produceTimestamp?.let {
                fields.add("*Timestamp:*\n`$it`".asMarkdown())
            }
        } else {
            fields.add("*Result:*\n❌ Failed".asMarkdown())
            event.errorMessage?.let {
                fields.add("*Error:*\n`$it`".asMarkdown())
            }
        }

        return listOf(
            DividerBlock(),
            SectionBlock.builder().fields(fields).build()
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: TopicManualProduceAuditEvent): List<EventLink> {
        val topicUrl = link {
            topics().showTopic(event.topicName)
        }
        val produceUrl = link {
            produceRecords().showProducePage(event.topicName, event.clusterIdentifier)
        }
        return listOf(
            EventLink(topicUrl, "Topic", "View topic"),
            EventLink(produceUrl, "Produce", "Produce to topic")
        )
    }
}
