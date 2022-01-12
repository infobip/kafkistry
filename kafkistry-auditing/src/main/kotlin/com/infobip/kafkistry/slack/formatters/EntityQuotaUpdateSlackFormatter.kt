package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.EntityQuotaUpdateAuditEvent
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class EntityQuotaUpdateSlackFormatter(
    private val quotasRegistryService: QuotasRegistryService,
) : AuditEventSlackFormatter<EntityQuotaUpdateAuditEvent> {

    override fun eventType() = EntityQuotaUpdateAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: EntityQuotaUpdateAuditEvent): List<TextObject> =
        listOfNotNull(
            "*Quota entity:*\n`${event.quotaEntity.format()}`".asMarkdown(),
            event.quotaDescription?.owner?.let { "*Owner*\n`$it`".asMarkdown() },
            event.message?.let { "*Reason message:*\n>>>$it".asMarkdown() },
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: EntityQuotaUpdateAuditEvent): List<LayoutBlock> {
        val quotaDescription = event.quotaDescription ?: return emptyList()
        return with(quotaDescription) {
            val globalQuotaBlock = SectionBlock.builder()
                .text("Global quota:\n${properties.format()}".asMarkdown())
                .build()
            val tagQuotas = tagOverrides.map { (tag, quotas) ->
                "Tag `$tag` quota:\n${quotas.format()}".asMarkdown()
            }
            val clusterQuotas = clusterOverrides.map { (cluster, quotas) ->
                "Cluster `$cluster` quota:\n${quotas.format()}".asMarkdown()
            }
            val overrideQuotas = tagQuotas + clusterQuotas
            listOfNotNull(
                globalQuotaBlock,
                overrideQuotas.takeIf { it.isNotEmpty() }?.let {
                    SectionBlock.builder().fields(it).build()
                }
            )
        }
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: EntityQuotaUpdateAuditEvent): List<EventLink> {
        val quotaEntityID = event.quotaEntity.asID()
        return sequence {
            if (quotasRegistryService.findQuotas(quotaEntityID) != null) {
                EventLink(
                    url = link { quotas().showEntity(quotaEntityID) },
                    title = "Entity quotas in registry",
                    label = "Open current",
                ).also { yield(it) }
            }
            quotasRegistryService.findPendingRequests(quotaEntityID).forEach { pendingRequest ->
                gitBrowseBranchBaseUrl()?.also { gitBranchBaseUrl ->
                    EventLink(
                        url = gitBranchBaseUrl + pendingRequest.branch,
                        title = "Branch on git",
                        label = pendingRequest.branch,
                    ).also { yield(it) }
                }
                EventLink(
                    url = link { quotas().showEditEntityOnBranch(quotaEntityID, pendingRequest.branch) },
                    title = "Edit on branch in registry",
                    label = pendingRequest.branch,
                ).also { yield(it) }
            }
            yieldAll(event.message.extractJiraLinks())
        }.toList()
    }

}