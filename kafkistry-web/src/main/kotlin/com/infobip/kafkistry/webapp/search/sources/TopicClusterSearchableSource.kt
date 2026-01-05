package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

/**
 * Searchable source for topic-cluster combinations.
 * Returns more granular results showing specific topic on specific cluster.
 */
@Component
class TopicClusterSearchableSource(
    private val topicsInspectionService: TopicsInspectionService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "TOPIC_CLUSTERS",
        displayName = "Topics on Clusters",
        icon = "icon-topic-cluster",
        defaultPriority = 15
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allTopics = topicsInspectionService.inspectAllTopics() +
                topicsInspectionService.inspectUnknownTopics()
        return allTopics.flatMap { topicStatuses ->
            topicStatuses.statusPerClusters.map { clusterStatus ->
                SearchableItem(
                    title = topicStatuses.topicName,
                    subtitle = "@${clusterStatus.clusterIdentifier}",
                    description = "Status: ${if (clusterStatus.status.flags.allOk) "ALL OK" else "HAS ISSUES"}",
                    url = appUrl.topics().showInspectTopicOnCluster(
                        topicStatuses.topicName,
                        clusterStatus.clusterIdentifier
                    ),
                    category = category,
                    metadata = mapOf(
                        "owner" to (topicStatuses.topicDescription?.owner ?: ""),
                        "producer" to (topicStatuses.topicDescription?.producer ?: ""),
                        "cluster" to clusterStatus.clusterIdentifier,
                        "status" to (clusterStatus.status.types.joinToString(", ")),
                    ),
                    scoreFactor = if (clusterStatus.status.exists == true) 1.0 else 0.5,
                )
            }
        }
    }
}
