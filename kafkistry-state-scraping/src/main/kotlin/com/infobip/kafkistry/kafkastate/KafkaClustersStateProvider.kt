package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * This component responsibility it to provide latest seen metadata of each tracked kafka cluster.
 * It will periodically scrape each tracked (saved in clusters repository) cluster and cache result which
 * can then be requested by other components to do inspections.
 */
@Component
class KafkaClustersStateProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<KafkaClusterState>(
    clustersRepository, clusterFilter, poolingProperties, promProperties, issuesRegistry,
) {

    override val stateTypeName = "cluster_state"

    fun getLatestClusterState(kafkaClusterIdentifier: KafkaClusterIdentifier) = getLatestState(kafkaClusterIdentifier)
    fun getLatestClusterStateValue(kafkaClusterIdentifier: KafkaClusterIdentifier) = getLatestStateValue(kafkaClusterIdentifier)
    fun getAllLatestClusterStates() = getAllLatestStates()
    fun listAllLatestClusterStates() = listAllLatestStates()

    override fun fetchState(kafkaCluster: KafkaCluster): KafkaClusterState {
        val clusterInfo = clientProvider.doWithClient(kafkaCluster) { it.clusterInfo(kafkaCluster.identifier).get() }
        return if (clusterInfo.clusterId != kafkaCluster.clusterId) {
            log.warn("Cluster $kafkaCluster reports to have actual cluster id: '${clusterInfo.clusterId}'")
            throw InvalidClusterIdException(
                    "Cluster '${kafkaCluster.identifier}' is expected to have id '${kafkaCluster.clusterId}', " +
                            "but actual is is '${clusterInfo.clusterId}'"
            )
        } else {
            val (topics, acls) = clientProvider.doWithClient(kafkaCluster) {
                val topicsFuture = it.listAllTopics()
                val aclsFuture = if (clusterInfo.securityEnabled) {
                    it.listAcls().exceptionally { ex ->
                        log.warn("Got unexpected exception while fetching acls from '${kafkaCluster.identifier}'", ex)
                        emptyList()
                    }
                } else {
                    //don't even try to fetch acls because security on kafka cluster is disabled
                    CompletableFuture.completedFuture(emptyList())
                }
                CompletableFuture.allOf(topicsFuture, aclsFuture)
                        .thenApply { topicsFuture.get() to aclsFuture.get() }
                        .get()
            }
            KafkaClusterState(clusterInfo, topics, acls)
        }
    }

}
