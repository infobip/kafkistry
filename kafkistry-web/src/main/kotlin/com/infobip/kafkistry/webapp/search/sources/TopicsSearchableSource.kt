package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

@Component
class TopicsSearchableSource(
    private val topicsRegistryService: TopicsRegistryService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "TOPICS",
        displayName = "Topics",
        icon = "icon-topic",
        defaultPriority = 10,
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allTopics = topicsRegistryService.listTopics()
        return allTopics.map { topic ->
            SearchableItem(
                title = topic.name,
                subtitle = topic.owner,
                description = null,
                url = appUrl.topics().showTopic(topic.name),
                category = category,
                metadata = mapOf(
                    "owner" to topic.owner,
                    "producer" to topic.producer,
                    "description" to topic.description,
                    "labels" to topic.labels.joinToString(", ") {
                        it.category + ": " + it.name
                    },
                )
            )
        }
    }
}
