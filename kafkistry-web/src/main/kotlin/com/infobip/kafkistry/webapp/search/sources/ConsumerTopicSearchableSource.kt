package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

/**
 * Searchable source for consumer-topic-cluster combinations.
 * Returns granular results showing which consumer groups consume which topics on which clusters.
 */
@Component
class ConsumerTopicSearchableSource(
    private val consumersService: ConsumersService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "CONSUMER_TOPICS",
        displayName = "Consumer Topics",
        icon = "icon-consumer-topic",
        defaultPriority = 55
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allConsumers = consumersService.allConsumersData()
        return allConsumers.clustersGroups.flatMap { clusterGroups ->
            val groupId = clusterGroups.consumerGroup.groupId
            val cluster = clusterGroups.clusterIdentifier
            val groupStatus = clusterGroups.consumerGroup.status
            clusterGroups.consumerGroup.topicMembers
                .map { it.topicName }
                .distinct()
                .map { topicName ->
                    SearchableItem(
                        title = groupId,
                        subtitle = "Topic: $topicName @$cluster",
                        description = "Consumer status: $groupStatus",
                        url = appUrl.consumerGroups().showConsumerGroup(cluster, groupId),
                        category = category,
                        metadata = mapOf(
                            "cluster" to cluster,
                            "consumer" to groupId,
                            "topic" to topicName,
                            "status" to groupStatus.name,
                        )
                    )
                }
        }
    }
}
