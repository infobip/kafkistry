package com.infobip.kafkistry.slack.formatters

import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.PrincipalAclsUpdateAuditEvent
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.acl.toKafkaAclRule
import com.infobip.kafkistry.slack.AuditEventSlackFormatter
import com.infobip.kafkistry.slack.EventLink
import com.infobip.kafkistry.slack.SlackMessageFormatter
import org.springframework.stereotype.Component

@Component
class PrincipalAclsUpdateSlackFormatter(
    private val aclsRegistryService: AclsRegistryService,
) : AuditEventSlackFormatter<PrincipalAclsUpdateAuditEvent> {

    override fun eventType() = PrincipalAclsUpdateAuditEvent::class.java

    override fun SlackMessageFormatter.Context.headSections(event: PrincipalAclsUpdateAuditEvent): List<TextObject> =
        listOfNotNull(
            "*Principal:*\n`${event.principal}`".asMarkdown(),
            event.principalAcls?.owner?.let { "*Owner*\n`$it`".asMarkdown() },
            event.message?.let { "*Reason message:*\n>>>$it".asMarkdown() },
        )

    override fun SlackMessageFormatter.Context.generateBlocks(event: PrincipalAclsUpdateAuditEvent): List<LayoutBlock> {
        val principalAcls = event.principalAcls ?: return emptyList()
        return listOf(
            SectionBlock.builder()
                .text("Principal ACLs description:\n```${principalAcls.description}```\n".asMarkdown())
                .build(),
            SectionBlock.builder()
                .text(
                    principalAcls.rules
                        .map { it.toKafkaAclRule(event.principal).asString() }
                        .joinToString(separator = "\n") { "- `$it`" }
                        .asMarkdown()
                )
                .build(),
        )
    }

    override fun SlackMessageFormatter.Context.linksToApp(event: PrincipalAclsUpdateAuditEvent): List<EventLink> {
        return sequence {
            if (aclsRegistryService.findPrincipalAcls(event.principal) != null) {
                EventLink(
                    url = link { acls().showAllPrincipalAcls(event.principal) },
                    title = "Principal ACLs in registry",
                    label = "Open current",
                ).also { yield(it) }
            }
            aclsRegistryService.findPendingRequests(event.principal).forEach { pendingRequest ->
                gitBrowseBranchBaseUrl()?.also { gitBranchBaseUrl ->
                    EventLink(
                        url = gitBranchBaseUrl + pendingRequest.branch,
                        title = "Branch on git",
                        label = pendingRequest.branch,
                    ).also { yield(it) }
                }
                EventLink(
                    url = link { acls().showEditPrincipalOnBranch(event.principal, pendingRequest.branch) },
                    title = "Edit on branch in registry",
                    label = pendingRequest.branch,
                ).also { yield(it) }
            }
            yieldAll(event.message.extractJiraLinks())
            yieldAll(event.principalAcls?.description.extractJiraLinks())
        }.distinct().toList()
    }
}