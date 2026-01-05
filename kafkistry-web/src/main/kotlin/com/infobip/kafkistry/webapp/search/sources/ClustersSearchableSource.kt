package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

@Component
class ClustersSearchableSource(
    private val clustersRegistryService: ClustersRegistryService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "CLUSTERS",
        displayName = "Clusters",
        icon = "icon-cluster",
        defaultPriority = 20
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allClusters = clustersRegistryService.listClusters()
        return allClusters.map { cluster ->
            SearchableItem(
                title = cluster.identifier,
                subtitle = null,
                description = null,
                url = appUrl.clusters().showCluster(cluster.identifier),
                category = category,
                metadata = mapOf(
                    "tags" to cluster.tags.joinToString(" "),
                    "connection" to cluster.connectionString,
                    "clusterId" to cluster.clusterId,
                )
            )
        }
    }
}
