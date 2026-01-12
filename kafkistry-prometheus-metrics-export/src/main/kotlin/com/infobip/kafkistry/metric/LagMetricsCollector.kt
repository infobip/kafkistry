package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.consumers.*
import com.infobip.kafkistry.utils.ClusterTopicConsumerGroupFilter
import com.infobip.kafkistry.utils.ClusterTopicConsumerGroupFilterProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.consumer-lag")
class LagMetricsProperties {
    var enabled = true

    @NestedConfigurationProperty
    var enabledOn = ClusterTopicConsumerGroupFilterProperties()

    var includeZeroSamples = true
    var includeSinglePartitionOnAllZeroSamples = false
}

@Component
@ConditionalOnProperty("app.metrics.consumer-lag.enabled", matchIfMissing = true)
class LagMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    lagProperties: LagMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>,
    additionalLabelsProvider: ObjectProvider<LagMetricsAdditionalLabels>,
) : KafkistryMetricsCollector {

    //default: kafkistry_consumer_lag
    private val lagMetricName = promProperties.prefix + "consumer_lag"

    private val filter = ClusterTopicConsumerGroupFilter(lagProperties.enabledOn)

    private val labelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val additionalLabels = additionalLabelsProvider.getIfAvailable {
        EmptyLagMetricsAdditionalLabels()
    }

    private val labelNames = listOf(
        labelProvider.labelName(), "consumer_group", "topic", "partition", "consumer_host"
    ) + additionalLabels.labelNames()

    private val takeZeros = lagProperties.includeZeroSamples
    private val includeSingleOnAllZeros = lagProperties.includeSinglePartitionOnAllZeroSamples

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val samples = context.partitionLagSamples()
        return mutableListOf(
            MetricFamilySamples(
                lagMetricName,
                Type.GAUGE,
                "How many messages is consumer lagging behind newest message in topic partition",
                samples
            )
        )
    }

    private fun MetricsDataContext.partitionLagSamples(): List<MetricFamilySamples.Sample> {
        return clustersGroups.asSequence().flatMap { clusterGroup ->
            val consumerGroup = clusterGroup.consumerGroup
            consumerGroup.topicMembers.asSequence().flatMap { topicMembers ->
                val clusterRef = clusters[clusterGroup.clusterIdentifier]
                if (clusterRef != null && !filter(clusterRef, topicMembers.topicName, consumerGroup.groupId)) {
                    emptySequence()
                } else {
                    val topicSamples = topicMembers.partitionMembers.mapNotNull { partitionMember ->
                        partitionMember.lag.amount?.let { lag ->
                            lagSample(
                                clusterIdentifier = clusterGroup.clusterIdentifier,
                                consumerGroup = clusterGroup.consumerGroup,
                                topicMembers = topicMembers,
                                partitionMember = partitionMember,
                                lagAmount = lag
                            )
                        }
                    }
                    if (takeZeros) {
                        topicSamples.asSequence()
                    } else {
                        val withoutZeros = topicSamples.filter { it.value != 0.0 }
                        if (withoutZeros.isEmpty() && includeSingleOnAllZeros) {
                            topicSamples.asSequence().take(1)
                        } else {
                            withoutZeros.asSequence()
                        }
                    }
                }
            }
        }.toList()
    }

    private fun lagSample(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroup: KafkaConsumerGroup,
        topicMembers: TopicMembers,
        partitionMember: ConsumerTopicPartitionMember,
        lagAmount: Long
    ) = MetricFamilySamples.Sample(
        lagMetricName,
        labelNames,
        listOf(
            labelProvider.labelValue(clusterIdentifier),
            consumerGroup.groupId,
            topicMembers.topicName,
            partitionMember.partition.toString(),
            partitionMember.member?.host ?: "unassigned"
        ) + additionalLabels.labelValues(
            clusterIdentifier = clusterIdentifier,
            topic = topicMembers.topicName,
            consumerGroupId = consumerGroup.groupId,
            partitionMember = partitionMember,
        ),
        lagAmount.toDouble()
    )

}