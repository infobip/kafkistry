package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.kafkastate.brokerdisk.NodeDiskMetric
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.Branch
import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.OptionalValue
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.utils.deepToString
import org.springframework.stereotype.Service

@Service
class ClusterResourcesAnalyzer(
    private val replicasInfoProvider: KafkaReplicasInfoProvider,
    private val nodeDiskMetricsStateProvider: NodeDiskMetricsStateProvider,
    private val topicsInspectionService: TopicsInspectionService,
    private val usageLevelClassifier: UsageLevelClassifier,
    private val topicsRegistry: TopicsRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val clusterStateProvider: KafkaClustersStateProvider,
    private val clusterEnabledFilter: ClusterEnabledFilter,
    private val topicDiskAnalyzer: TopicDiskAnalyzer,
) {

    fun dryRunClustersDiskUsageForBranch(branch: Branch): Map<KafkaClusterIdentifier, OptionalValue<ClusterDiskUsage>> {
        val allBranchTopics = topicsRegistry.listTopicsAt(branch).associateBy { it.name }
        val allBranchClusters = clustersRegistry.listClustersAt(branch)
        return allBranchClusters
            .filter { clusterEnabledFilter.enabled(it.ref()) }
            .associate { cluster ->
                val usage = try {
                    val context = collectDataForBranch(cluster.ref(), allBranchTopics)
                    val diskUsage = computeDiskUsageOf(context)
                    OptionalValue.of(diskUsage)
                } catch(e: Exception) {
                    OptionalValue.absent("Unable to compute disk usage of '${cluster.identifier}', reason: ${e.deepToString()}")
                }
                cluster.identifier to usage
            }
    }

    fun dryRunClusterDiskUsageForBranch(clusterRef: ClusterRef, branch: Branch): ClusterDiskUsage {
        val allBranchTopics = topicsRegistry.listTopicsAt(branch).associateBy { it.name }
        val context = collectDataForBranch(clusterRef, allBranchTopics)
        return computeDiskUsageOf(context)
    }

    fun clustersDiskUsage(): Map<KafkaClusterIdentifier, OptionalValue<ClusterDiskUsage>> {
        return clustersRegistry.listClustersRefs()
            .filter { clusterEnabledFilter.enabled(it) }
            .associate {
                val usage = try {
                    val diskUsage = clusterDiskUsage(it.identifier)
                    OptionalValue.of(diskUsage)
                } catch(e: Exception) {
                    OptionalValue.absent("Unable to compute disk usage of '${it.identifier}', reason: ${e.deepToString()}")
                }
                it.identifier to usage
            }
    }

    fun clusterDiskUsage(clusterIdentifier: KafkaClusterIdentifier): ClusterDiskUsage {
        val context = collectData(clusterIdentifier, null)
        return computeDiskUsageOf(context)
    }

    fun dryRunClusterDiskUsage(clusterRef: ClusterRef): ClusterDiskUsage {
        val context = collectData(clusterRef.identifier, clusterRef.tags)
        return computeDiskUsageOf(context)
    }

    private fun computeDiskUsageOf(
        context: ContextData,
    ): ClusterDiskUsage {
        val brokerUsages = context.computeBrokerUsages()
        val combinedDiskUsage = brokerUsages.values.map { it.usage }.fold(BrokerDiskUsage.ZERO, BrokerDiskUsage::plus)
        val combinedDiskMetrics = context.brokersMetrics.values
            .reduceOrNull { acc, metrics ->
                NodeDiskMetric(
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
            worstCurrentUsageLevel = brokerUsages.worstUsageLevel { usageLevel },
            worstPossibleUsageLevel = brokerUsages.worstUsageLevel { possibleUsageLevel },
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
                    totalUsedBytes = topicsDisks.sumOf { it.actualUsedBytes ?: 0L },
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
        val brokerIds = clusterStatuses.clusterInfo?.brokerIds
        if (topicStatuses == null || brokerIds == null) {
            throw KafkistryIllegalStateException(
                "Can't analyze resources of cluster '$clusterIdentifier' because it's state is ${clusterStatuses.clusterState}"
            )
        }
        val replicaDirs = replicasInfoProvider.getLatestStateValue(clusterIdentifier)
        val brokersMetrics = nodeDiskMetricsStateProvider.getLatestState(clusterIdentifier)
            .valueOrNull()
            ?.brokersMetrics
            .orEmpty()
        val topicsExpectedToExist = topicStatuses.asSequence()
            .filter {
                topicsRegistry.findTopic(it.topicName)
                    ?.presence?.needToBeOnCluster(clusterRef ?: clusterStatuses.cluster.ref())
                    ?: false
            }
            .map { it.topicName }
            .toSet()
        val topicNames = if (clusterRef != null) {
            topicsExpectedToExist
        } else {
            topicsExpectedToExist + replicaDirs.replicas.keys
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
        return ContextData(brokerIds, replicaDirs, brokersMetrics, topicsDisks)
    }

    private fun collectDataForBranch(
        clusterRef: ClusterRef,
        allBranchTopics: Map<TopicName, TopicDescription>,
    ): ContextData {
        val clusterState = clusterStateProvider.getLatestClusterState(clusterRef.identifier).value()
        val brokerIds = clusterState.clusterInfo.brokerIds
        val replicaDirs = replicasInfoProvider.getLatestStateValue(clusterRef.identifier)
        val brokersMetrics = nodeDiskMetricsStateProvider.getLatestState(clusterRef.identifier)
            .valueOrNull()
            ?.brokersMetrics
            .orEmpty()
        val topicsExpectedToExist = allBranchTopics.values.asSequence()
            .filter { it.presence.needToBeOnCluster(clusterRef) }
            .map { it.name }
            .toSet()
        val topicNames = topicsExpectedToExist + replicaDirs.replicas.keys
        val topicsDisks = topicNames.associateWith { topicName ->
            try {
                topicDiskAnalyzer.analyzeTopicDiskUsage(
                    topic = topicName, topicDescription = allBranchTopics[topicName],
                    clusterRef = clusterRef, preferUsingDescriptionProps = true,
                ).let { OptionalValue.of(it) }
            } catch (ex: KafkistryException) {
                OptionalValue.absent(ex.deepToString())
            }
        }
        return ContextData(brokerIds, replicaDirs, brokersMetrics, topicsDisks)
    }

    private data class ContextData(
        //val topicStatuses: List<ClusterTopicStatus>,
        val brokerIds: List<BrokerId>,
        val replicaDirs: ReplicaDirs,
        val brokersMetrics: Map<BrokerId, NodeDiskMetric>,
        val topicsDisks: Map<TopicName, OptionalValue<TopicClusterDiskUsage>>,
    )

}



