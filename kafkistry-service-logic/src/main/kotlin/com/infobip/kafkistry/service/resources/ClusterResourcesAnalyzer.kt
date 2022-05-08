package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.BrokerDiskMetricsStateProvider
import com.infobip.kafkistry.kafkastate.KafkaReplicasInfoProvider
import com.infobip.kafkistry.kafkastate.ReplicaDirs
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.ClusterTopicStatus
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springframework.stereotype.Service

@Service
class ClusterResourcesAnalyzer(
    private val replicasInfoProvider: KafkaReplicasInfoProvider,
    private val brokerDiskMetricsStateProvider: BrokerDiskMetricsStateProvider,
    private val topicsInspectionService: TopicsInspectionService,
    private val usageLevelClassifier: UsageLevelClassifier,
    private val topicsRegistry: TopicsRegistryService,
    private val topicDiskAnalyzer: TopicDiskAnalyzer,
) {

    fun clusterDiskUsage(clusterIdentifier: KafkaClusterIdentifier): ClusterDiskUsage {
        return clusterDiskUsage(clusterIdentifier, null)
    }

    fun dryRunClusterDiskUsage(kafkaCluster: KafkaCluster): ClusterDiskUsage {
        return clusterDiskUsage(kafkaCluster.identifier, kafkaCluster)
    }

    private fun clusterDiskUsage(
        clusterIdentifier: KafkaClusterIdentifier,
        kafkaCluster: KafkaCluster?,
    ): ClusterDiskUsage {
        val context = collectData(clusterIdentifier, kafkaCluster)
        val brokerUsages = context.computeBrokerUsages()
        val combinedDiskUsage = brokerUsages.values.map { it.usage }.fold(BrokerDiskUsage.ZERO, BrokerDiskUsage::plus)
        val combinedDiskMetrics = context.brokersMetrics.values
            .reduceOrNull { acc, metrics ->
                BrokerDiskMetric(
                    total = acc.total plusNullable metrics.total,
                    free = acc.free plusNullable metrics.free
                )
            }
        return ClusterDiskUsage(
            combined = BrokerDisk(
                usage = combinedDiskUsage,
                portions = combinedDiskUsage.portionsOf(combinedDiskMetrics)
            ),
            brokerUsages = brokerUsages,
        )
    }

    private fun ContextData.computeBrokerUsages(): Map<BrokerId, BrokerDisk> {
        return topicsDisks.values
            .flatMap { it.brokerUsages.entries }
            .groupBy({ it.key }, { it.value })
            .let { brokerDisks ->
                brokerIds.associateWith { brokerDisks[it].orEmpty() }
            }
            .mapValues { (brokerId, topicsDisks) ->
                val usage = BrokerDiskUsage(
                    replicasCount = topicsDisks.sumOf { it.replicasCount },
                    totalUsedBytes = topicsDisks.sumOf { it.actualUsedBytes ?: it.retentionBoundedBytes ?: 0L },
                    boundedReplicasCount = topicsDisks.sumOf { if (it.retentionBoundedBytes != null) it.replicasCount else 0 },
                    boundedSizePossibleUsedBytes = topicsDisks.sumOf { it.retentionBoundedBytes ?: 0L },
                    unboundedReplicasCount = topicsDisks.sumOf { if (it.unboundedUsageBytes != null && it.unboundedUsageBytes > 0) it.replicasCount else 0 },
                    unboundedSizeUsedBytes = topicsDisks.sumOf { it.unboundedUsageBytes ?: 0L },
                    orphanedReplicasCount = topicsDisks.sumOf { it.orphanedReplicasCount },
                    orphanedReplicasSizeUsedBytes = topicsDisks.sumOf { it.orphanedUsedBytes },
                    totalCapacityBytes = brokersMetrics[brokerId]?.total,
                    freeCapacityBytes = brokersMetrics[brokerId]?.free,
                )
                BrokerDisk(
                    usage = usage,
                    portions = usage.portionsOf(brokersMetrics[brokerId]),
                )
            }
    }

    private fun BrokerDiskUsage.portionsOf(diskMetric: BrokerDiskMetric?): BrokerDiskPortions {
        val usedPercentOfCapacity = totalUsedBytes percentageOfNullable diskMetric?.total
        val possibleUsedPercentOfCapacity = boundedSizePossibleUsedBytes percentageOfNullable diskMetric?.total
        return BrokerDiskPortions(
            usedPercentOfCapacity = usedPercentOfCapacity,
            usageLevel = usageLevelClassifier.determineLevel(usedPercentOfCapacity),
            possibleUsedPercentOfCapacity = possibleUsedPercentOfCapacity,
            possibleUsageLevel = usageLevelClassifier.determineLevel(possibleUsedPercentOfCapacity),
            unboundedUsedPercentOfTotalUsed = unboundedSizeUsedBytes percentageOfNullable totalUsedBytes,
        )
    }

    private fun collectData(
        clusterIdentifier: KafkaClusterIdentifier,
        kafkaCluster: KafkaCluster?,
    ): ContextData {
        val clusterStatuses = if (kafkaCluster == null) {
            topicsInspectionService.inspectCluster(clusterIdentifier)
        } else {
            topicsInspectionService.inspectCluster(kafkaCluster)
        }
        val clusterRef = (kafkaCluster ?: clusterStatuses.cluster).ref()
        val topicStatuses = clusterStatuses.statusPerTopics
        val brokerIds = clusterStatuses.clusterInfo?.nodeIds
        if (topicStatuses == null || brokerIds == null) {
            throw KafkistryIllegalStateException(
                "Can't analyze resources of cluster '$clusterIdentifier' because it's state is ${clusterStatuses.clusterState}"
            )
        }
        val replicaDirs = replicasInfoProvider.getLatestStateValue(clusterIdentifier)
        val brokersMetrics = brokerDiskMetricsStateProvider.getLatestState(clusterIdentifier)
            .valueOrNull()
            ?.brokersMetrics
            .orEmpty()
        val topicNames = if (kafkaCluster != null) {
            topicStatuses
                .filter {
                    topicsRegistry.findTopic(it.topicName)?.presence?.needToBeOnCluster(kafkaCluster.ref()) ?: false
                }
                .map { it.topicName }
        } else {
            replicaDirs.replicas.map { it.topic }.distinct()
        }
        val topicsDisks = topicNames.associateWith { topicName ->
            topicDiskAnalyzer.analyzeTopicDiskUsage(
                topic = topicName, topicDescription = topicsRegistry.findTopic(topicName),
                clusterRef = clusterRef, preferUsingDescriptionProps = kafkaCluster != null
            )
        }
        return ContextData(topicStatuses, brokerIds, replicaDirs, brokersMetrics, topicsDisks)
    }

    private data class ContextData(
        val topicStatuses: List<ClusterTopicStatus>,
        val brokerIds: List<BrokerId>,
        val replicaDirs: ReplicaDirs,
        val brokersMetrics: Map<BrokerId, BrokerDiskMetric>,
        val topicsDisks: Map<TopicName, TopicClusterDiskUsage>,
    )

}



