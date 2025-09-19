package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.utils.ClusterFilter
import com.infobip.kafkistry.utils.ClusterFilterProperties
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Gauge
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.cluster-metadata")
class ClusterMetadataMetricsProperties {
    var enabled = true

    @NestedConfigurationProperty
    var enabledOn = ClusterFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.cluster-metadata.enabled", matchIfMissing = true)
class ClusterMetadataMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    properties: ClusterMetadataMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>,
) : KafkistryMetricsCollector {

    private val filter = ClusterFilter(properties.enabledOn)

    private val clusterLabelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val hasMetadataGauge = Gauge.build()
        .name(promProperties.prefix + "cluster_metadata_available") //default: kafkistry_cluster_metadata_available
        .help("Status if metadata about cluster is available, 1=available; 0=unavailable")
        .labelNames(this.clusterLabelProvider.labelName())
        .create()

    private val numNodesGauge = Gauge.build()
        .name(promProperties.prefix + "cluster_metadata_node_count") //default: kafkistry_cluster_metadata_node_count
        .help("Number of nodes in cluster")
        .labelNames(this.clusterLabelProvider.labelName())
        .create()

    private val numBrokersGauge = Gauge.build()
        .name(promProperties.prefix + "cluster_metadata_broker_count") //default: kafkistry_cluster_metadata_broker_count
        .help("Number of broker nodes in cluster")
        .labelNames(this.clusterLabelProvider.labelName())
        .create()

    private fun collectAll(): List<MetricFamilySamples> {
        return buildList {
            addAll(hasMetadataGauge.collect())
            addAll(numNodesGauge.collect())
            addAll(numBrokersGauge.collect())
        }
    }

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        refresh(context)
        return collectAll()
    }

    private fun refresh(context: MetricsDataContext) {
        context.clusterInfos.asSequence()
            .filter { filter(it.clusterRef) }
            .forEach { cluster ->
                val clusterLabel = clusterLabelProvider.labelValue(cluster.clusterRef.identifier)
                val statusInfo = cluster.statusInfo
                if (statusInfo != null) {
                    hasMetadataGauge.labels(clusterLabel).set(1.0)
                    numNodesGauge.labels(clusterLabel).set(statusInfo.nodeIds.size.toDouble())
                    numBrokersGauge.labels(clusterLabel).set(statusInfo.brokerIds.size.toDouble())
                } else {
                    hasMetadataGauge.labels(clusterLabel).set(0.0)
                }
            }
    }
}