package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.service.topic.TopicInspectionResultType
import com.infobip.kafkistry.service.topic.clusterRef
import com.infobip.kafkistry.utils.ClusterTopicFilter
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.topic-statuses")
class TopicStatusesMetricsProperties {
    var enabled = true
    var includeDisabledClusters = false
    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.topic-statuses.enabled", matchIfMissing = true)
class TopicStatusesMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    private val properties: TopicStatusesMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>,
) : KafkistryMetricsCollector {

    //default: kafkistry_topic_status
    private val statusMetricName = promProperties.prefix + "topic_status"

    private val filter = ClusterTopicFilter(properties.enabledOn)

    private val clusterLabelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val labelNames = listOf(
        this.clusterLabelProvider.labelName(), "topic", "status", "valid", "category", "level", "owners",
    )

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val statusSamples = context.topicInspections.asSequence()
            .flatMap { topicStatuses ->
                val owner = topicStatuses.topicDescription?.owner
                    ?.replace(" ", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: "unknown"
                topicStatuses.statusPerClusters.asSequence()
                    .filter { filter.filter(it.clusterRef(), topicStatuses.topicName) }
                    .flatMap { clusterStatus ->
                        val clusterDisabled = TopicInspectionResultType.CLUSTER_DISABLED in clusterStatus.status.types
                        if (properties.includeDisabledClusters || !clusterDisabled) {
                            val clusterLabel = clusterLabelProvider.labelValue(clusterStatus.clusterIdentifier)
                            clusterStatus.status.types
                                .map {
                                    MetricFamilySamples.Sample(
                                        statusMetricName, labelNames,
                                        listOf(
                                            clusterLabel, topicStatuses.topicName, it.name, it.valid.toString(),
                                            it.category.name, it.level.name, owner,
                                        ),
                                        1.0,
                                    )
                                }
                        } else {
                            emptyList()
                        }
                    }
            }
            .toList()
        return mutableListOf(
            MetricFamilySamples(
                statusMetricName,
                Type.STATE_SET,
                "Individual state type per topic on cluster",
                statusSamples,
            )
        )
    }
}