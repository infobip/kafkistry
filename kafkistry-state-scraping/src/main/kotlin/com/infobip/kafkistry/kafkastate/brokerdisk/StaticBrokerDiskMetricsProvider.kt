package com.infobip.kafkistry.kafkastate.brokerdisk

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.ClusterBroker
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.kafka.metrics.static")
class StaticBrokerDiskMetricsProperties {
    var enabled = false
    var clusters: Map<KafkaClusterIdentifier, TotalMetrics> = mutableMapOf()

    class TotalMetrics {
        var totalDefault: Long? = null
        var brokerTotal: Map<BrokerId, Long> = mutableMapOf()
    }
}

/**
 * Provider which always returns fixed values of total disk. Values are coming from app configuration.
 */
@Component
@ConditionalOnProperty("app.kafka.metrics.static.enabled")
class StaticBrokerDiskMetricsProvider(
    private val properties: StaticBrokerDiskMetricsProperties
) : BrokerDiskMetricsProvider {

    override fun canHandle(clusterIdentifier: KafkaClusterIdentifier): Boolean {
        return clusterIdentifier in properties.clusters
    }

    override fun brokersDisk(
        clusterIdentifier: KafkaClusterIdentifier,
        brokers: List<ClusterBroker>
    ): Map<BrokerId, BrokerDiskMetric> {
        val metrics = properties.clusters[clusterIdentifier] ?: return emptyMap()
        return brokers.associate {
            val brokerTotal = metrics.brokerTotal[it.brokerId] ?: metrics.totalDefault
            it.brokerId to BrokerDiskMetric(total = brokerTotal, free = null)
        }
    }
}