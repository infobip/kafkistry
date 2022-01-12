package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.ClusterUpdateAuditEvent
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class ClusterUpdateSlackFormatter(
    private val clustersRegistryService: ClustersRegistryService,
) : AuditEventSlackFormatter<ClusterUpdateAuditEvent> {

    override fun eventType() = ClusterUpdateAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: ClusterUpdateAuditEvent): List<TextObject> =
        listOfNotNull(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            event.message?.let { "*Reason message:*\n>>>$it".asMarkdown() },
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: ClusterUpdateAuditEvent): List<LayoutBlock> {
        val kafkaCluster = event.kafkaCluster ?: return emptyList()
        val tags = kafkaCluster.tags
            .joinToString(separator = ", ") { "`$it`" }
            .takeIf { it.isNotEmpty() }
            ?: "_none_"
        return listOf(
            SectionBlock.builder()
                .text("Tags: $tags".asMarkdown())
                .build(),
            SectionBlock.builder()
                .text("Brokers connection:\n```${kafkaCluster.connectionString}```\n".asMarkdown())
                .build(),
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: ClusterUpdateAuditEvent): List<EventLink> {
        return sequence {
            if (clustersRegistryService.findCluster(event.clusterIdentifier) != null) {
                EventLink(
                    url = link { clusters().showCluster(event.clusterIdentifier) },
                    title = "Cluster in registry",
                    label = "Open current",
                ).also { yield(it) }
            }
            clustersRegistryService.findPendingRequests(event.clusterIdentifier).forEach { pendingRequest ->
                gitBrowseBranchBaseUrl()?.also { gitBranchBaseUrl ->
                    EventLink(
                        url = gitBranchBaseUrl + pendingRequest.branch,
                        title = "Branch on git",
                        label = pendingRequest.branch,
                    ).also { yield(it) }
                }
            }
            yieldAll(event.message.extractJiraLinks())
        }.toList()
    }
}