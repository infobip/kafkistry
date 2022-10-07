package com.infobip.kafkistry.metric

import io.prometheus.client.Collector
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.kafkastate.ClusterTopicOffsets
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.ClusterRef
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
    promProperties: PrometheusMetricsProperties,
    properties: TopicOffsetsMetricsProperties,
    private val topicOffsetsService: TopicOffsetsService,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>
) : Collector() {

    //default: kafkistry_topic_begin_offset
    private val beginOffsetMetricName = promProperties.prefix + "topic_begin_offset"

    //default: kafkistry_topic_end_offset
    private val endOffsetMetricName = promProperties.prefix + "topic_end_offset"

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
                    beginOffsetMetricName, labelNames,
                    listOf(labelProvider.labelValue(cluster.identifier), topic, partition.toString()),
                    partitionOffsets.begin.toDouble()
                )
            }
        val endOffsetSamples = allClustersTopicsOffsets.metricSamplesBy { cluster, topic, partition, partitionOffsets ->
            MetricFamilySamples.Sample(
                endOffsetMetricName, labelNames,
                listOf(labelProvider.labelValue(cluster.identifier), topic, partition.toString()),
                partitionOffsets.end.toDouble()
            )
        }
        return mutableListOf(
            MetricFamilySamples(
                beginOffsetMetricName, Type.GAUGE,
                "Value of earliest (begin) offset per topic partition",
                beginOffsetSamples
            ),
            MetricFamilySamples(
                endOffsetMetricName, Type.GAUGE,
                "Value of latest (end) offset per topic partition",
                endOffsetSamples
            )
        )
    }

    private inline fun Map<ClusterRef, ClusterTopicOffsets>.metricSamplesBy(
        crossinline sampleExtractor: (ClusterRef, TopicName, Partition, PartitionOffsets) -> MetricFamilySamples.Sample
    ): List<MetricFamilySamples.Sample> {
        return sequence {
            forEach { (clusterRef, clusterTopicOffsets) ->
                clusterTopicOffsets.topicsOffsets.forEach { (topic, partitionOffsets) ->
                    if (filter(clusterRef, topic)) {
                        partitionOffsets.forEach { (partition, offsets) ->
                            val sample = sampleExtractor(
                                clusterRef, topic, partition, offsets
                            )
                            yield(sample)
                        }
                    }
                }
            }
        }.toList()
    }

}