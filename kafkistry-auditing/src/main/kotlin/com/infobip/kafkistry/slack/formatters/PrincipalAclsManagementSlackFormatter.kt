package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.PrincipalAclsManagementAuditEvent
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.service.AclStatus
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class PrincipalAclsManagementSlackFormatter : AuditEventSlackFormatter<PrincipalAclsManagementAuditEvent> {

    override fun eventType() = PrincipalAclsManagementAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: PrincipalAclsManagementAuditEvent): List<TextObject> =
        listOf(
            "*Cluster:*\n`${event.clusterIdentifier}`".asMarkdown(),
            "*Principal:*\n`${event.principal}`".asMarkdown(),
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: PrincipalAclsManagementAuditEvent): List<LayoutBlock> {
        val statusBefore = event.principalStatusBefore
        val statusAfter = event.principalStatusAfter
        if (statusBefore == null && statusAfter == null) {
            return emptyList()
        }
        fun AclStatus?.format(): String {
            if (this == null) {
                return "`null`"
            }
            val result = StringBuilder()
            result.append("OK: ").append(if (ok) "`yes`" else "`no`").append("\n")
            statusCounts.forEach { (status, count) ->
                result.append("`$status`: $count\n")
            }
            return result.toString()
        }
        return listOf(
            DividerBlock(),
            SectionBlock.builder()
                .text(
                    event.aclRules.orEmpty()
                        .map { it.asString() }
                        .joinToString(separator = "\n") { "- `$it`" }
                        .asMarkdown()
                )
                .build(),
            SectionBlock.builder()
                .fields(
                    listOf(
                        "Status counts before:\n${statusBefore.format()}".asMarkdown(),
                        "Status counts after:\n${statusAfter.format()}".asMarkdown(),
                    )
                )
                .build()
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: PrincipalAclsManagementAuditEvent): List<EventLink> {
        val inspectPrincipalUrl = link {
            acls().showAllPrincipalAclsCluster(event.principal, event.clusterIdentifier)
        }
        return listOf(EventLink(inspectPrincipalUrl, "Principal ACLs on cluster", "Inspect principal"))
    }
}