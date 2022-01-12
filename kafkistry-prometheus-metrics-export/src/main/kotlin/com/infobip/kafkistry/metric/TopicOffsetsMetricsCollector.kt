package com.infobip.kafkistry.metric

import io.prometheus.client.Collector
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.kafkastate.ClusterTopicOffsets
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import com.infobip.kafkistry.utils.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.topic-offsets")
class TopicOffsetsMetricsProperties {
    var enabled = true
    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.topic-offsets.enabled", matchIfMissing = true)
class TopicOffsetsCollector(
    properties: TopicOffsetsMetricsProperties,
    private val topicOffsetsService: TopicOffsetsService,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>
) : Collector() {

    companion object {
        const val BEGIN_OFFSET_METRIC_NAME = "kafkistry_topic_begin_offset"
        const val END_OFFSET_METRIC_NAME = "kafkistry_topic_end_offset"
    }

    private val filter = ClusterTopicFilter(properties.enabledOn)

    private val labelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val labelNames = listOf(
        labelProvider.labelName(), "topic", "partition"
    )

    override fun collect(): List<MetricFamilySamples> {
        val allClustersTopicsOffsets = topicOffsetsService.allClustersTopicsOffsets()
        val beginOffsetSamples =
            allClustersTopicsOffsets.metricSamplesBy { cluster, topic, partition, partitionOffsets ->
                MetricFamilySamples.Sample(
                    BEGIN_OFFSET_METRIC_NAME,
                    labelNames,
                    listOf(labelProvider.labelValue(cluster), topic, partition.toString()),
                    partitionOffsets.begin.toDouble()
                )
            }
        val endOffsetSamples = allClustersTopicsOffsets.metricSamplesBy { cluster, topic, partition, partitionOffsets ->
            MetricFamilySamples.Sample(
                END_OFFSET_METRIC_NAME,
                labelNames,
                listOf(labelProvider.labelValue(cluster), topic, partition.toString()),
                partitionOffsets.end.toDouble()
            )
        }
        return mutableListOf(
            MetricFamilySamples(
                BEGIN_OFFSET_METRIC_NAME,
                Type.GAUGE,
                "Value of earliest (begin) offset per topic partition",
                beginOffsetSamples
            ),
            MetricFamilySamples(
                END_OFFSET_METRIC_NAME,
                Type.GAUGE,
                "Value of latest (end) offset per topic partition",
                endOffsetSamples
            )
        )
    }

    private inline fun Map<KafkaClusterIdentifier, ClusterTopicOffsets>.metricSamplesBy(
        crossinline sampleExtractor: (KafkaClusterIdentifier, TopicName, Partition, PartitionOffsets) -> MetricFamilySamples.Sample
    ): List<MetricFamilySamples.Sample> {
        return sequence {
            forEach { (clusterIdentifier, clusterTopicOffsets) ->
                clusterTopicOffsets.topicsOffsets.forEach { (topic, partitionOffsets) ->
                    if (filter(clusterIdentifier, topic)) {
                        partitionOffsets.forEach { (partition, offsets) ->
                            val sample = sampleExtractor(
                                clusterIdentifier, topic, partition, offsets
                            )
                            yield(sample)
                        }
                    }
                }
            }
        }.toList()
    }

}