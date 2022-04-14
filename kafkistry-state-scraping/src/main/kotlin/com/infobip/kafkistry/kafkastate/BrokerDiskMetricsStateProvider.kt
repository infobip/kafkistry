package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetricsProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component
import java.util.*

@Component
class BrokerDiskMetricsStateProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    issuesRegistry: BackgroundJobIssuesRegistry,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    brokerDiskMetricsProviders: Optional<List<BrokerDiskMetricsProvider>>,
    private val clustersStateProvider: KafkaClustersStateProvider,
) : AbstractKafkaStateProvider<ClusterBrokerMetrics>(
    clustersRepository, clusterFilter, poolingProperties, promProperties, issuesRegistry
) {

    override val stateTypeName = "brokers_disk_metrics"
    private val brokerDiskMetricsProviders = brokerDiskMetricsProviders.orElse(emptyList())

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterBrokerMetrics {
        val clusterState = clustersStateProvider.getLatestClusterState(kafkaCluster.identifier)
        if (clusterState.stateType == StateType.UNKNOWN) {
            return ClusterBrokerMetrics(emptyMap())
        }
        val brokers = clusterState.value().clusterInfo.brokers
        val provider = brokerDiskMetricsProviders.find { it.canHandle(kafkaCluster.identifier) }
            ?: BrokerDiskMetricsProvider.NoOp
        val metrics = provider.brokersDisk(kafkaCluster.identifier, brokers)
        return ClusterBrokerMetrics(metrics)
    }
}