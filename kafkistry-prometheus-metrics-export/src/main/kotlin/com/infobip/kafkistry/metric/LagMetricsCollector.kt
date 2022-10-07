package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import io.prometheus.client.Collector
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
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
}

@Component
@ConditionalOnProperty("app.metrics.consumer-lag.enabled", matchIfMissing = true)
class LagMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    lagProperties: LagMetricsProperties,
    private val consumersService: ConsumersService,
    private val clustersRegistryService: ClustersRegistryService,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>
) : Collector() {

    //default: kafkistry_consumer_lag
    private val lagMetricName = promProperties.prefix + "consumer_lag"

    private val filter = ClusterTopicConsumerGroupFilter(lagProperties.enabledOn)

    private val labelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val labelNames = listOf(
        labelProvider.labelName(), "consumer_group", "topic", "partition", "consumer_host"
    )

    override fun collect(): List<MetricFamilySamples> {
        val samples = consumersService.allConsumersData()
            .clustersGroups
            .partitionLagSamples()
        return mutableListOf(
            MetricFamilySamples(
                lagMetricName,
                Type.GAUGE,
                "How many messages is consumer lagging behind newest message in topic partition",
                samples
            )
        )
    }

    private fun List<ClusterConsumerGroup>.partitionLagSamples(): List<MetricFamilySamples.Sample> {
        val clusterRefs = clustersRegistryService.listClustersRefs()
            .associateBy { it.identifier }
        return asSequence().flatMap { clusterGroup ->
            val consumerGroup = clusterGroup.consumerGroup
            consumerGroup.topicMembers.asSequence().flatMap { topicMembers ->
                val clusterRef = clusterRefs[clusterGroup.clusterIdentifier]
                if (clusterRef != null && !filter(clusterRef, topicMembers.topicName, consumerGroup.groupId)) {
                    emptySequence()
                } else {
                    topicMembers.partitionMembers.asSequence().mapNotNull { partitionMember ->
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
        ),
        lagAmount.toDouble()
    )

}