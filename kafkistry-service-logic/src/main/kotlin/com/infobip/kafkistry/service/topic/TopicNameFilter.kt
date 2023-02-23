package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.model.TopicName

class TopicNameFilter(
    includeTopicNamePattern: String?,
    excludeTopicNamePattern: String?,
): (TopicName) -> Boolean {

    val includeFilter = includeTopicNamePattern
        ?.takeIf { it.isNotEmpty() }
        ?.toRegex()
        ?.let { regex -> { topic: TopicName -> regex.containsMatchIn(topic) } }
        ?: { true }
    val excludeFilter = excludeTopicNamePattern
        ?.takeIf { it.isNotEmpty() }
        ?.toRegex()
        ?.let { regex -> { topic: TopicName -> !regex.containsMatchIn(topic) } }
        ?: { true }

    override fun invoke(topicName: TopicName): Boolean {
        return includeFilter(topicName) && excludeFilter(topicName)
    }

}
