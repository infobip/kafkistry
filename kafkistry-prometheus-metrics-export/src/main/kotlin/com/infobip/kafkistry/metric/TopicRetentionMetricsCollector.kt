package com.infobip.kafkistry.metric

import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.utils.ClusterTopicFilter
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.topic-retention")
class RetentionMetricsProperties {
    var enabled = true
    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.topic-retention.enabled", matchIfMissing = true)
class RetentionMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    properties: RetentionMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>
) : KafkistryMetricsCollector {

    companion object {
        const val INF_RETENTION = -1L
    }

    //default: kafkistry_topic_effective_retention_ms
    private val effectiveRetentionMetricName = promProperties.prefix + "topic_effective_retention_ms"

    //default: kafkistry_topic_time_retention_usage
    private val timeRetentionUsageMetricName = promProperties.prefix + "topic_time_retention_usage"

    //default: kafkistry_topic_size_retention_usage
    private val sizeRetentionUsageMetricName = promProperties.prefix + "topic_size_retention_usage"

    private val labelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val filter = ClusterTopicFilter(properties.enabledOn)

    private val labelNames = listOf(labelProvider.labelName(), "topic", "partition")

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val allTopicPartitionStats = context.getAllTopicPartitionStats()
        val timeRetentionUsageSamples = allTopicPartitionStats.mapNotNull {
            MetricFamilySamples.Sample(
                timeRetentionUsageMetricName, labelNames,
                listOf(labelProvider.labelValue(it.clusterIdentifier), it.topic, it.partition.toString()),
                it.timeUsage ?: return@mapNotNull null
            )
        }
        val sizeRetentionUsageSamples = allTopicPartitionStats.mapNotNull {
            MetricFamilySamples.Sample(
                sizeRetentionUsageMetricName, labelNames,
                listOf(labelProvider.labelValue(it.clusterIdentifier), it.topic, it.partition.toString()),
                it.sizeUsage ?: return@mapNotNull null
            )
        }
        val effectiveRetentionSamples = allTopicPartitionStats.mapNotNull {
            MetricFamilySamples.Sample(
                effectiveRetentionMetricName, labelNames,
                listOf(labelProvider.labelValue(it.clusterIdentifier), it.topic, it.partition.toString()),
                it.oldestRecordAgeMs?.toDouble() ?: return@mapNotNull null
            )
        }
        return mutableListOf(
            MetricFamilySamples(
                timeRetentionUsageMetricName, Type.GAUGE,
                "Ratio of oldest record age against retention.ms",
                timeRetentionUsageSamples
            ),
            MetricFamilySamples(
                sizeRetentionUsageMetricName, Type.GAUGE,
                "Ratio of partition size against retention.bytes",
                sizeRetentionUsageSamples
            ),
            MetricFamilySamples(
                effectiveRetentionMetricName, Type.GAUGE,
                "How old in millis is last message in partition",
                effectiveRetentionSamples
            ),
        )
    }

    private fun MetricsDataContext.getAllTopicPartitionStats(): List<TopicPartitionStats> {
        return topicInspections.flatMap { topicStatuses ->
            val topicName = topicStatuses.topicName
            topicStatuses.statusPerClusters.flatMap TopicCluster@{ topicStatus ->
                val existingTopic = topicStatus.existingTopicInfo ?: return@TopicCluster emptyList()
                val clusterIdentifier = topicStatus.clusterIdentifier
                if (!filter(topicStatus.clusterRef, topicName)) {
                    return@flatMap emptyList()
                }
                val retentionMs = existingTopic.config[TopicConfig.RETENTION_MS_CONFIG]?.value?.toLongOrNull()
                    ?: return@TopicCluster emptyList()
                val retentionBytes = existingTopic.config[TopicConfig.RETENTION_BYTES_CONFIG]?.value?.toLongOrNull()
                    ?: return@TopicCluster emptyList()
                val oldestRecordAges = allClustersTopicOldestAges[clusterIdentifier]?.get(topicName).orEmpty()
                val replicaInfos =
                    allClustersTopicReplicaInfos[clusterIdentifier]?.get(topicName)?.partitionBrokerReplicas.orEmpty()
                existingTopic.partitionsAssignments.mapNotNull { (partition, assignments) ->
                    val leader = assignments.find { it.leader } ?: return@mapNotNull null
                    val sizeBytes = replicaInfos[partition]?.get(leader.brokerId)?.sizeBytes ?: return@mapNotNull null
                    val oldestRecordAgeMs = oldestRecordAges[partition]
                    TopicPartitionStats(
                        clusterIdentifier, topicName, partition,
                        retentionMs, retentionBytes, sizeBytes, oldestRecordAgeMs,
                        sizeUsage = sizeBytes.toDouble().div(retentionBytes).takeIf { retentionBytes != INF_RETENTION },
                        timeUsage = oldestRecordAgeMs?.toDouble()?.div(retentionMs)
                            ?.takeIf { retentionMs != INF_RETENTION },
                    )
                }
            }
        }
    }

    private data class TopicPartitionStats(
        val clusterIdentifier: KafkaClusterIdentifier,
        val topic: TopicName,
        val partition: Partition,
        val retentionMs: Long,
        val retentionBytes: Long,
        val sizeBytes: Long,
        val oldestRecordAgeMs: Long?,
        val sizeUsage: Double?,
        val timeUsage: Double?,
    )

}