package com.infobip.kafkistry.metric

import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.utils.ClusterFilter
import com.infobip.kafkistry.utils.ClusterFilterProperties
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.cluster-statuses")
class ClusterStatusesMetricsProperties {
    var enabled = true
    var includeDisabledClusters = false
    var omitStatusNames = mutableSetOf<String>()

    @NestedConfigurationProperty
    var enabledOn = ClusterFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.cluster-statuses.enabled", matchIfMissing = true)
class ClusterStatusesMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    private val properties: ClusterStatusesMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>,
) : KafkistryMetricsCollector {

    //default: kafkistry_cluster_status
    private val statusMetricName = promProperties.prefix + "cluster_status"

    private val filter = ClusterFilter(properties.enabledOn)

    private val clusterLabelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val labelNames = listOf(
        this.clusterLabelProvider.labelName(), "status", "valid", "level"
    )

    private data class ClusterStatusEntry(
        val clusterLabel: String,
        val statusName: String,
        val valid: Boolean,
        val level: StatusLevel,
    )

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val statusSamplesSeq = context.clusterStatuses.asSequence()
            .filter { filter(it.clusterRef) }
            .filter { it.stateType != StateType.DISABLED || properties.includeDisabledClusters }
            .flatMap { cluster ->
                val state = cluster.stateType
                val clusterLabel = clusterLabelProvider.labelValue(cluster.clusterRef.identifier)
                listOf(
                    ClusterStatusEntry(clusterLabel, state.name, state.valid, state.level)
                ) + cluster.issues.map { issue ->
                    ClusterStatusEntry(clusterLabel, issue.name, issue.valid, issue.level)
                }
            }
            .filter { it.statusName !in properties.omitStatusNames }
        val statusSamples = statusSamplesSeq.map {
            MetricFamilySamples.Sample(
                statusMetricName, labelNames,
                listOf(it.clusterLabel, it.statusName, it.valid.toString(), it.level.name),
                1.0,
            )
        }.toList()
        return mutableListOf(
            MetricFamilySamples(
                statusMetricName,
                Type.STATE_SET,
                "Individual state type per cluster",
                statusSamples,
            )
        )
    }
}