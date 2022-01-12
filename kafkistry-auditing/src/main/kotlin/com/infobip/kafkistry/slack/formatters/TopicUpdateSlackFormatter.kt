package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.TopicUpdateAuditEvent
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class TopicUpdateSlackFormatter(
    private val topicsRegistryService: TopicsRegistryService,
) : AuditEventSlackFormatter<TopicUpdateAuditEvent> {

    override fun eventType() = TopicUpdateAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: TopicUpdateAuditEvent): List<TextObject> =
        listOfNotNull(
            "*Topic:*\n`${event.topicName}`".asMarkdown(),
            event.topicDescription?.owner?.let { "*Owner*\n`$it`".asMarkdown() },
            event.message?.let { "*Reason message:*\n>>>$it".asMarkdown() },
        )

    override fun SlackMessageFormatter.Context.generateBlocks(
        event: TopicUpdateAuditEvent
    ): List<LayoutBlock> {
        val topicDescription = event.topicDescription ?: return emptyList()
        return listOf(
            SectionBlock.builder()
                .text("Topic description:\n```${topicDescription.description}```\n".asMarkdown())
                .build()
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: TopicUpdateAuditEvent): List<EventLink> {
        return sequence {
            if (topicsRegistryService.findTopic(event.topicName) != null) {
                EventLink(
                    url = link { topics().showTopic(event.topicName) },
                    title = "Topic in registry",
                    label = "Open current",
                ).also { yield(it) }
            }
            topicsRegistryService.findPendingRequests(event.topicName).forEach { pendingRequest ->
                gitBrowseBranchBaseUrl()?.also { gitBranchBaseUrl ->
                    EventLink(
                        url = gitBranchBaseUrl + pendingRequest.branch,
                        title = "Branch on git",
                        label = pendingRequest.branch,
                    ).also { yield(it) }
                }
                EventLink(
                    url = link { topics().showEditTopicOnBranch(event.topicName, pendingRequest.branch) },
                    title = "Edit on branch in registry",
                    label = pendingRequest.branch,
                ).also { yield(it) }
            }
            yieldAll(event.message.extractJiraLinks())
            yieldAll(event.topicDescription?.description.extractJiraLinks())
        }.distinct().toList()
    }
}