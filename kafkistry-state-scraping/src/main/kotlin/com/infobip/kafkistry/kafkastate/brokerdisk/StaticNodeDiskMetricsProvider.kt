package com.infobip.kafkistry.kafkastate.brokerdisk

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.ClusterNode
import com.infobip.kafkistry.kafka.NodeId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.kafka.metrics.static")
class StaticNodeDiskMetricsProperties {
    var enabled = false
    var clusters: Map<KafkaClusterIdentifier, TotalMetrics> = mutableMapOf()

    class TotalMetrics {
        var totalDefault: Long? = null
        var brokerTotal: Map<NodeId, Long> = mutableMapOf()
    }
}

/**
 * Provider which always returns fixed values of total disk. Values are coming from app configuration.
 */
@Component
@ConditionalOnProperty("app.kafka.metrics.static.enabled")
class StaticNodeDiskMetricsProvider(
    private val properties: StaticNodeDiskMetricsProperties
) : NodeDiskMetricsProvider {

    override fun canHandle(clusterIdentifier: KafkaClusterIdentifier): Boolean {
        return clusterIdentifier in properties.clusters
    }

    override fun nodesDisk(
        clusterIdentifier: KafkaClusterIdentifier,
        nodes: List<ClusterNode>
    ): Map<BrokerId, NodeDiskMetric> {
        val metrics = properties.clusters[clusterIdentifier] ?: return emptyMap()
        return nodes.associate {
            val brokerTotal = metrics.brokerTotal[it.nodeId] ?: metrics.totalDefault
            it.nodeId to NodeDiskMetric(total = brokerTotal, free = null)
        }
    }
}