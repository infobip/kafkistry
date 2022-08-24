package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.BrokerDiskMetricsStateProvider
import com.infobip.kafkistry.kafkastate.KafkaReplicasInfoProvider
import com.infobip.kafkistry.kafkastate.ReplicaDirs
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.ClusterTopicStatus
import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.OptionalValue
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.utils.deepToString
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

    fun dryRunClusterDiskUsage(clusterRef: ClusterRef): ClusterDiskUsage {
        return clusterDiskUsage(clusterRef.identifier, clusterRef.tags)
    }

    private fun clusterDiskUsage(
        clusterIdentifier: KafkaClusterIdentifier,
        tags: List<Tag>?,
    ): ClusterDiskUsage {
        val context = collectData(clusterIdentifier, tags)
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
                portions = combinedDiskUsage.portionsOf(combinedDiskMetrics, usageLevelClassifier)
            ),
            brokerUsages = brokerUsages,
            topicDiskUsages = context.topicsDisks,
            errors = context.topicsDisks.mapNotNull { (topicName, optionalDisk) ->
                optionalDisk.absentReason?.let { errorMsg -> "Topic: '$topicName', error: $errorMsg" }
            }
        )
    }

    private fun ContextData.computeBrokerUsages(): Map<BrokerId, BrokerDisk> {
        return topicsDisks.values
            .mapNotNull { it.value }
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
                    portions = usage.portionsOf(brokersMetrics[brokerId], usageLevelClassifier),
                )
            }
    }

    private fun collectData(
        clusterIdentifier: KafkaClusterIdentifier,
        tags: List<Tag>?,
    ): ContextData {
        val clusterRef = tags?.let { ClusterRef(clusterIdentifier, tags) }
        val clusterStatuses = if (clusterRef == null) {
            topicsInspectionService.inspectClusterTopics(clusterIdentifier)
        } else {
            topicsInspectionService.inspectClusterTopics(clusterRef)
        }
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
        val topicNames = if (clusterRef != null) {
            topicStatuses
                .filter {
                    topicsRegistry.findTopic(it.topicName)?.presence?.needToBeOnCluster(clusterRef) ?: false
                }
                .map { it.topicName }
        } else {
            replicaDirs.replicas.keys.distinct()
        }
        val topicsDisks = topicNames.associateWith { topicName ->
            try {
                topicDiskAnalyzer.analyzeTopicDiskUsage(
                    topic = topicName, topicDescription = topicsRegistry.findTopic(topicName),
                    clusterRef = clusterRef ?: clusterStatuses.cluster.ref(), preferUsingDescriptionProps = clusterRef != null
                ).let { OptionalValue.of(it) }
            } catch (ex: KafkistryException) {
                OptionalValue.absent(ex.deepToString())
            }
        }
        return ContextData(topicStatuses, brokerIds, replicaDirs, brokersMetrics, topicsDisks)
    }

    private data class ContextData(
        val topicStatuses: List<ClusterTopicStatus>,
        val brokerIds: List<BrokerId>,
        val replicaDirs: ReplicaDirs,
        val brokersMetrics: Map<BrokerId, BrokerDiskMetric>,
        val topicsDisks: Map<TopicName, OptionalValue<TopicClusterDiskUsage>>,
    )

}



