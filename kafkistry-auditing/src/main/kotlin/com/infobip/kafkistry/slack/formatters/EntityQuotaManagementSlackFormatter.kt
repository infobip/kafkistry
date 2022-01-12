package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.EntityQuotaManagementAuditEvent
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class EntityQuotaManagementSlackFormatter : AuditEventSlackFormatter<EntityQuotaManagementAuditEvent> {

    override fun eventType() = EntityQuotaManagementAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: EntityQuotaManagementAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Quota entity:*\n`${event.quotaEntity.format()}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: EntityQuotaManagementAuditEvent): List<LayoutBlock> {
        val statusBefore = event.quotaStatusBefore
        val statusAfter = event.quotaStatusAfter
        if (statusBefore == null && statusAfter == null) {
            return emptyList()
        }
        return listOf(
            DividerBlock(),
            SectionBlock.builder()
                .fields(
                    listOfNotNull(
                        statusBefore?.let { "Status before:\n`$it`".asMarkdown() },
                        statusAfter?.let { "Status after:\n`$it`".asMarkdown() },
                        event.quotaBefore?.let { "Quota before:\n${it.format()}".asMarkdown() },
                        event.quotaAfter?.let { "Quota after:\n${it.format()}".asMarkdown() },
                    )
                )
                .build(),
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: EntityQuotaManagementAuditEvent): List<EventLink> {
        val inspectQuotaUrl = link { quotas().showEntity(event.quotaEntity.asID()) }
        return listOf(EventLink(inspectQuotaUrl, "Entity quota", "Show current state"))
    }
}