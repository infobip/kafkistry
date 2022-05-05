package com.infobip.kafkistry.service.resources

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.kafkastate.BrokerDiskMetricsStateProvider
import com.infobip.kafkistry.kafkastate.KafkaReplicasInfoProvider
import com.infobip.kafkistry.kafkastate.ReplicaDirs
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.ClusterTopicStatus
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable
import org.springframework.stereotype.Service

@Service
class ClusterResourcesAnalyzer(
    private val replicasInfoProvider: KafkaReplicasInfoProvider,
    private val brokerDiskMetricsStateProvider: BrokerDiskMetricsStateProvider,
    private val topicsInspectionService: TopicsInspectionService,
    private val usageLevelClassifier: UsageLevelClassifier,
) {

    private data class ReplicaKey(
        val brokerId: BrokerId,
        val topic: TopicName,
        val partition: Partition,
    )

    fun clusterDiskUsage(clusterIdentifier: KafkaClusterIdentifier): ClusterDiskUsage {
        val context = collectData(clusterIdentifier)
        val replicaRetentions = extractReplicaRetentions(context.topicStatuses)
        val replicaSizes = extractReplicaSizes(context.replicaDirs.replicas)

        val brokerTotalBytes = replicaSizes.sumPerBroker()
        val (infiniteRetentions, finiteRetentions) = replicaRetentions.partition { it.value == INF_RETENTION }

        val finiteTotalRetentionBytes = finiteRetentions.sumPerBroker()
        val brokerUnboundedRetentionsBytes = infiniteRetentions.mapValues { replicaSizes[it.key] ?: 0L }.sumPerBroker()

        val brokerReplicas = replicaSizes.countPerBroker()
        val boundedReplicas = finiteRetentions.countPerBroker()
        val unboundedReplicas = infiniteRetentions.countPerBroker()
        val (_, orphanedReplicaSizes) = replicaSizes.partition { it.key in replicaRetentions }
        val orphanedReplicas = orphanedReplicaSizes.countPerBroker()
        val brokerOrphanedSizes = orphanedReplicaSizes.sumPerBroker()

        val brokerUsages = context.brokerIds.associateWith {
            val usage = BrokerDiskUsage(
                replicasCount = brokerReplicas[it] ?: 0,
                totalUsedBytes =  brokerTotalBytes[it] ?: 0,
                boundedReplicasCount = boundedReplicas[it] ?: 0,
                boundedSizePossibleUsedBytes = finiteTotalRetentionBytes[it] ?: 0,
                unboundedReplicasCount = unboundedReplicas[it] ?: 0,
                unboundedSizeUsedBytes = brokerUnboundedRetentionsBytes[it] ?: 0,
                orphanedReplicasCount = orphanedReplicas[it] ?: 0,
                orphanedReplicasSizeUsedBytes = brokerOrphanedSizes[it] ?: 0,
                totalCapacityBytes = context.brokersMetrics[it]?.total,
                freeCapacityBytes = context.brokersMetrics[it]?.free,
            )
            BrokerDisk(
                usage = usage,
                portions = usage.portionsOf(context.brokersMetrics[it]),
            )
        }
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

    private fun collectData(clusterIdentifier: KafkaClusterIdentifier): ContextData {
        val clusterStatuses = topicsInspectionService.inspectCluster(clusterIdentifier)
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
        return ContextData(topicStatuses, brokerIds, replicaDirs, brokersMetrics)
    }

    private fun extractReplicaRetentions(topicStatuses: List<ClusterTopicStatus>): Map<ReplicaKey, Long> =
        topicStatuses.mapNotNull { it.existingTopicInfo }
            .flatMap { topic ->
                val retentionBytes = topic.config[TopicConfig.RETENTION_BYTES_CONFIG]
                    ?.value?.toLongOrNull()
                    ?: return@flatMap emptyList()
                topic.partitionsAssignments
                    .flatMap { assignments ->
                        assignments.replicasAssignments
                            .map { ReplicaKey(it.brokerId, topic.name, assignments.partition) to retentionBytes }
                    }
            }
            .toMap()

    private fun extractReplicaSizes(replicaInfos: List<TopicPartitionReplica>): Map<ReplicaKey, Long> =
        replicaInfos.associate { ReplicaKey(it.brokerId, it.topic, it.partition) to it.sizeBytes }

    private fun Map<ReplicaKey, Long>.sumPerBroker() = entries
        .groupingBy { it.key.brokerId }
        .fold(0L) { accumulator, entry -> accumulator + entry.value }

    private fun Map<ReplicaKey, Long>.countPerBroker() = entries
        .groupingBy { it.key.brokerId }
        .eachCount()

    private fun <K, V> Map<K, V>.partition(predicate: (Map.Entry<K, V>) -> Boolean): Pair<Map<K, V>, Map<K, V>> {
        return entries.partition(predicate).let { (p1, p2) ->
            p1.associate { it.toPair() } to p2.associate { it.toPair() }
        }
    }

    private data class ContextData(
        val topicStatuses: List<ClusterTopicStatus>,
        val brokerIds: List<BrokerId>,
        val replicaDirs: ReplicaDirs,
        val brokersMetrics: Map<BrokerId, BrokerDiskMetric>,
    )

}



