package com.infobip.kafkistry.slack

import com.github.seratch.jslack.api.model.block.LayoutBlock
import com.github.seratch.jslack.api.model.block.composition.TextObject
import com.infobip.kafkistry.audit.AuditEvent

/**
 * Allows customization of slack message content for specific type [AuditEvent]
 */
interface AuditEventSlackFormatter<E : AuditEvent> {

    fun eventType(): Class<E>

    fun preferredHexColor(event: E): String? = null

    fun SlackMessageFormatter.Context.headSections(event: E): List<TextObject> = emptyList()
    fun SlackMessageFormatter.Context.generateBlocks(event: E): List<LayoutBlock> = emptyList()
    fun SlackMessageFormatter.Context.linksToApp(event: E): List<EventLink> = emptyList()

}

data class EventLink(
    val url: String,
    /**
     * Text prior to link
     */
    val title: String,
    /**
     * Clickable text
     */
    val label: String,
)
