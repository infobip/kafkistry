package com.infobip.kafkistry.service.tags

import com.infobip.kafkistry.model.Tag
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.cluster-tags")
class ClusterTagClassifierProperties {
    var presenceTagNamePattern: String = ""
    var overrideTagNamePattern: String = ""
}

@Component
class ClusterTagClassifier(
    properties: ClusterTagClassifierProperties,
) {

    private val presenceRegex = properties.presenceTagNamePattern.takeIf { it.isNotEmpty() }?.let { Regex(it) }
    private val overrideRegex = properties.overrideTagNamePattern.takeIf { it.isNotEmpty() }?.let { Regex(it) }

    fun isPresence(tag: Tag): Boolean = presenceRegex?.matches(tag) ?: true
    fun isOverride(tag: Tag): Boolean = overrideRegex?.matches(tag) ?: true
}