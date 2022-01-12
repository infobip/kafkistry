package com.infobip.kafkistry.slack

import com.github.seratch.jslack.api.methods.request.chat.ChatPostMessageRequest
import com.github.seratch.jslack.api.model.Attachment
import com.github.seratch.jslack.api.model.block.ContextBlock
import com.github.seratch.jslack.api.model.block.DividerBlock
import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.SectionBlock
import com.github.seratch.jslack.api.model.block.composition.MarkdownTextObject
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.google.common.base.CaseFormat
import com.infobip.kafkistry.audit.AuditEvent
import com.infobip.kafkistry.repository.config.GitBrowseProperties
import com.infobip.kafkistry.webapp.jira.JiraProperties
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.slack.enabled")
class SlackMessageFormatter(
    private val properties: SlackProperties,
    private val formatters: List<AuditEventSlackFormatter<out AuditEvent>>,
    gitBrowseProperties: GitBrowseProperties,
    jiraProperties: JiraProperties,
    registryUrlFormatter: RegistryUrlFormatter,
) {

    private val context = Context(gitBrowseProperties, jiraProperties, registryUrlFormatter)

    class Context(
        private val gitBrowseProperties: GitBrowseProperties,
        private val jiraProperties: JiraProperties,
        private val registryUrlFormatter: RegistryUrlFormatter,
    ) {
        private val jiraPattern = Regex("(?<=$|[/(:\\s,.])[A-Z]+-[0-9]+(?=$|[\\s,.?/)])")

        fun String.asMarkdown(): TextObject = MarkdownTextObject.builder().text(this).build()

        fun gitBrowseBranchBaseUrl(): String? = gitBrowseProperties.branchBaseUrl.takeIf { it.isNotBlank() }

        fun String?.extractJiraLinks(): List<EventLink> {
            if (this == null) return emptyList()
            val jiraBaseUrl = jiraProperties.baseUrl.takeIf { it.isNotBlank() } ?: return emptyList()
            return jiraPattern.findAll(this)
                .map { EventLink(jiraBaseUrl + it.value, "Jira", it.value) }
                .toList()
        }

        fun link(path: AppUrl.() -> String) = registryUrlFormatter.generateUrl(path)

    }

    fun generateSlackMessage(event: AuditEvent): ChatPostMessageRequest {
        val blocks = sequence {
            yield(context.formatMainSection(event))
            invokeFormatters(event) { context.generateBlocks(it) }.flatten().also { yieldAll(it) }
            val links = invokeFormatters(event) { context.linksToApp(it) }
                .flatten()
                .map { with(context) { "*${it.title}*\n<${it.url}|${it.label}>".asMarkdown() } }
            if (links.isNotEmpty()) {
                yield(DividerBlock())
                yield(SectionBlock.builder().fields(links).build())
            }
            yieldAll(context.formatException(event))
            yield(context.formatContextFooter())
        }.toList()
        return ChatPostMessageRequest.builder()
            .mrkdwn(true)
            .text("*New application event*")
            .attachments(
                mutableListOf(
                    Attachment.builder()
                        .color(selectColor(event))
                        .blocks(blocks)
                        .build()
                )
            )
            .asUser(false)
            .channel(properties.channelId)
            .build()
    }

    private fun <R> invokeFormatters(
        event: AuditEvent,
        call: AuditEventSlackFormatter<AuditEvent>.(AuditEvent) -> R
    ): List<R> = formattersFor(event).map { it.call(event) }

    private fun formattersFor(event: AuditEvent) =
        formatters
            .filter { it.eventType().isAssignableFrom(event.javaClass) }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as AuditEventSlackFormatter<AuditEvent>
            }

    private fun Context.formatMainSection(event: AuditEvent): LayoutBlock {
        val action = event.methodName.formatAction()
        val basicFields = listOf(
            "*Environment:*\n`${properties.environmentName}`".asMarkdown(),
            "*User:*\n${event.user.fullName}".asMarkdown(),
            "*Action:*\n`${action}`".asMarkdown(),
        )
        val customFields = invokeFormatters(event) { headSections(it) }.flatten()
        return SectionBlock.builder().fields(basicFields + customFields).build()
    }

    private fun Context.formatException(event: AuditEvent): List<LayoutBlock> {
        return event.encounteredException?.let {
            listOf(
                DividerBlock(), SectionBlock.builder()
                    .text("Encountered exception:\n```$it```".asMarkdown())
                    .build()
            )
        } ?: emptyList()
    }

    private fun Context.formatContextFooter(): ContextBlock {
        val appHomeUrl = link { main().url() }
        val footerText = "Message sent by <$appHomeUrl|Kafkistry> on ${properties.environmentName}"
        return ContextBlock.builder().elements(listOf(footerText.asMarkdown())).build()
    }

    /**
     * Converts for example "addCluster" -> "ADD CLUSTER"
     */
    private fun String.formatAction(): String {
        return CaseFormat.LOWER_CAMEL
            .converterTo(CaseFormat.UPPER_UNDERSCORE)
            .convert(this)!!
            .replace('_', ' ')
    }

    private fun selectColor(event: AuditEvent): String {
        val preferredColor = formattersFor(event).mapNotNull { it.preferredHexColor(event) }.firstOrNull()
        return with(event) {
            when {
                encounteredException != null -> "#A50000" //red
                preferredColor != null -> preferredColor
                listOf("TopicsApi", "AclsApi", "QuotasApi").any { it in serviceClass } -> "#5E8C62" //green-gray
                "ManagementApi" in serviceClass -> "#616E8C" //purple-gray
                else -> "#807157"   //gray
            }
        }
    }

}