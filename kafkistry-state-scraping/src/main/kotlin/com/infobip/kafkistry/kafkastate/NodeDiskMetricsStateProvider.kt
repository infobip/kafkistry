package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafkastate.brokerdisk.NodeDiskMetricsProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component
import java.util.*

@Component
class NodeDiskMetricsStateProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    issuesRegistry: BackgroundJobIssuesRegistry,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    nodeDiskMetricsProviders: Optional<List<NodeDiskMetricsProvider>>,
    private val clustersStateProvider: KafkaClustersStateProvider,
) : AbstractKafkaStateProvider<ClusterNodeMetrics>(
    clustersRepository, clusterFilter, poolingProperties, promProperties, issuesRegistry
) {
    companion object {
        const val NODES_DISK_METRICS = "nodes_disk_metrics"
    }

    override val stateTypeName = NODES_DISK_METRICS

    private val brokerDiskMetricsProviders = nodeDiskMetricsProviders.orElse(emptyList())
    override fun fetchState(kafkaCluster: KafkaCluster): ClusterNodeMetrics {
        val clusterState = clustersStateProvider.getLatestClusterState(kafkaCluster.identifier)
        if (clusterState.stateType == StateType.UNKNOWN) {
            return ClusterNodeMetrics(emptyMap(), emptyMap())
        }
        val nodes = clusterState.value().clusterInfo.nodes
        val provider = brokerDiskMetricsProviders.find { it.canHandle(kafkaCluster.identifier) }
            ?: NodeDiskMetricsProvider.NoOp
        val nodeMetrics = provider.nodesDisk(kafkaCluster.identifier, nodes)
        val brokerIds = clusterState.value().clusterInfo.brokerIds.toSet()
        val brokerMetrics = nodeMetrics.filterKeys { it in brokerIds }
        return ClusterNodeMetrics(nodeMetrics, brokerMetrics)
    }
}