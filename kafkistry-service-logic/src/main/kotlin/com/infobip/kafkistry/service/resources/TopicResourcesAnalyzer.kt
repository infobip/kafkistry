package com.infobip.kafkistry.service.resources

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.generator.balance.percentageOf
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import org.springframework.stereotype.Service

@Service
class TopicResourcesAnalyzer(
    private val clusterResourcesAnalyzer: ClusterResourcesAnalyzer,
    private val topicsInspectionService: TopicsInspectionService,
    private val replicaDirsService: ReplicaDirsService,
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val topicsRegistryService: TopicsRegistryService,
    private val clustersRegistryService: ClustersRegistryService,
    private val enabledFilter: ClusterEnabledFilter,
    private val usageLevelClassifier: UsageLevelClassifier,
    private val assignor: PartitionsReplicasAssignor,
) {

    fun topicOnClusterDiskUsage(
        topic: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
    ): TopicDiskUsage {
        val cluster = clustersRegistryService.getCluster(clusterIdentifier)
        val topicDescription = topicsRegistryService.findTopic(topic)
        return analyzeTopicDiskUsage(topic, topicDescription, cluster.ref(), false)
    }

    fun topicDryRunDiskUsage(
        topicDescription: TopicDescription
    ): Map<KafkaClusterIdentifier, OptionalValue<TopicDiskUsage>> {
        return clustersRegistryService.listClustersRefs()
            .filter { enabledFilter.enabled(it.identifier) }
            .filter { topicDescription.presence.needToBeOnCluster(it) }
            .associate { clusterRef ->
                val topicDiskUsage = try {
                    analyzeTopicDiskUsage(topicDescription.name, topicDescription, clusterRef, true)
                        .let { OptionalValue.of(it) }
                } catch (e: Exception) {
                    OptionalValue.absent(e.deepToString())
                }
                clusterRef.identifier to topicDiskUsage
            }
    }

    private fun analyzeTopicDiskUsage(
        topic: TopicName,
        topicDescription: TopicDescription?,
        clusterRef: ClusterRef,
        preferUsingDescriptionProps: Boolean,
    ): TopicDiskUsage {
        val brokerIds: List<BrokerId> = clustersStateProvider.getLatestClusterStateValue(clusterRef.identifier)
            .clusterInfo.nodeIds
        val clusterDiskUsage = clusterResourcesAnalyzer.clusterDiskUsage(clusterRef.identifier)
        val topicClusterStatus = if (topicDescription != null) {
            topicsInspectionService.inspectTopicOnCluster(topicDescription, clusterRef)
        } else {
            topicsInspectionService.inspectTopicOnCluster(topic, clusterRef)
        }
        val replicaInfos = replicaDirsService.topicReplicaInfos(clusterRef.identifier, topic)
        val brokerActualUsage = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (_, partitionReplicas) ->
                partitionReplicas.entries.sumOf { it.value.sizeBytes }
            }
        val brokerReplicaCounts = replicaInfos?.brokerPartitionReplicas
            ?.takeIf { !preferUsingDescriptionProps }
            ?.mapValues { (_, partitionReplicas) -> partitionReplicas.size }
            ?: topicDescription
                ?.propertiesForCluster(clusterRef)
                ?.let { properties ->
                    //topic either does not exist so let's see possible assignment/impact if it were created
                    //or we prefer using props fro topic's description
                    assignor
                        .assignNewPartitionReplicas(
                            emptyMap(), brokerIds,
                            properties.partitionCount, properties.replicationFactor,
                            emptyMap()
                        )
                        .newAssignments
                        .flatMap { it.value }
                        .groupingBy { it }
                        .eachCount()
                }
        val expectedUsageBytes = topicClusterStatus.resourceRequiredUsages.value?.diskUsagePerBroker
        val existingTopicRetentionBytes = topicClusterStatus.existingTopicInfo?.config
            ?.get(TopicConfig.RETENTION_BYTES_CONFIG)?.value
            ?.toLongOrNull()
        val describedTopicRetentionBytes = topicDescription?.configForCluster(clusterRef)
            ?.get(TopicConfig.RETENTION_BYTES_CONFIG)
            ?.toLongOrNull()
        val configuredReplicaRetentionBytes = if (preferUsingDescriptionProps) {
            describedTopicRetentionBytes ?: existingTopicRetentionBytes
        } else {
            existingTopicRetentionBytes ?: describedTopicRetentionBytes
        }
        val brokerRetentionBoundedBytes =
            if (brokerReplicaCounts != null && configuredReplicaRetentionBytes?.takeIf { it != INF_RETENTION } != null) {
                brokerReplicaCounts.mapValues { (_, replicas) -> configuredReplicaRetentionBytes * replicas }
            } else {
                null
            }
        val existingAssignments = topicClusterStatus.existingTopicInfo
            ?.partitionsAssignments
            ?.flatMap { (partition, replicas) -> replicas.map { it.brokerId to partition } }
            ?.groupBy({ it.first }, { it.second })
        val brokerOrphanedReplicas = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (brokerId, partitionReplicas) ->
                val assignedPartitions = existingAssignments?.get(brokerId) ?: return@mapValues 0
                partitionReplicas.keys.count { it !in assignedPartitions }
            }
        val brokerUsages = brokerIds.associateWith {
            val retentionBoundedBytes = brokerRetentionBoundedBytes?.run { this[it] ?: 0L }
            val actualUsedBytes = brokerActualUsage?.run { this[it] ?: 0L }
            val brokerTotalUsedBytes = clusterDiskUsage.brokerUsages[it]?.usage?.totalUsedBytes
            val brokerPossibleUsedBytes = clusterDiskUsage.brokerUsages[it]?.usage?.boundedSizePossibleUsedBytes
            DiskUsage(
                replicasCount = brokerReplicaCounts?.get(it) ?: 0,
                orphanedReplicasCount = brokerOrphanedReplicas?.get(it) ?: 0,
                actualUsedBytes = actualUsedBytes,
                expectedUsageBytes = expectedUsageBytes,
                retentionBoundedBytes = retentionBoundedBytes,
                retentionBoundedBrokerTotalBytes = retentionBoundedBytes plusNullable -actualUsedBytes.orElse(0) plusNullable brokerTotalUsedBytes,
                retentionBoundedBrokerPossibleBytes = retentionBoundedBytes plusNullable -existingTopicRetentionBytes.orElse(
                    0
                ) plusNullable brokerPossibleUsedBytes,
            )
        }
        val combinedUsage = brokerUsages.values.reduce(DiskUsage::plus)
        return TopicDiskUsage(
            unboundedSizeRetention = configuredReplicaRetentionBytes == INF_RETENTION,
            configuredReplicaRetentionBytes = configuredReplicaRetentionBytes ?: 0L,
            combined = combinedUsage,
            combinedPortions = combinedUsage.portionOf(clusterDiskUsage.combined.usage),
            brokerUsages = brokerUsages,
            brokerPortions = brokerUsages.mapValues { (broker, usage) ->
                usage.portionOf(clusterDiskUsage.brokerUsages[broker]?.usage ?: BrokerDiskUsage.ZERO)
            },
            clusterDiskUsage = clusterDiskUsage,
        )
    }

    private fun DiskUsage.portionOf(brokerUsage: BrokerDiskUsage): UsagePortions {
        val retentionBoundedBrokerTotalBytesPercentOfCapacity =
            retentionBoundedBrokerTotalBytes percentageOfNullable brokerUsage.totalCapacityBytes
        val retentionBoundedBrokerPossibleBytesPercentOfCapacity =
            retentionBoundedBrokerPossibleBytes percentageOfNullable brokerUsage.totalCapacityBytes
        return UsagePortions(
            replicasPercent = replicasCount percentageOfNullable brokerUsage.replicasCount,
            orphanedReplicasPercent = orphanedReplicasCount percentageOf brokerUsage.orphanedReplicasCount,
            actualUsedBytesPercentOfBrokerTotal = actualUsedBytes percentageOfNullable brokerUsage.totalUsedBytes,
            actualUsedBytesPercentOfBrokerCapacity = actualUsedBytes percentageOfNullable brokerUsage.totalCapacityBytes,
            actualUsedBytesPercentOfExpected = actualUsedBytes percentageOfNullable expectedUsageBytes,
            retentionBoundedBytesPercentOfBrokerTotal = retentionBoundedBytes percentageOfNullable brokerUsage.totalUsedBytes?.takeIf { it > 0 },
            retentionBoundedBytesPercentOfBrokerCapacity = retentionBoundedBytes percentageOfNullable brokerUsage.totalCapacityBytes,
            retentionBoundedBytesPercentOfExpected = retentionBoundedBytes percentageOfNullable expectedUsageBytes,
            retentionBoundedBrokerTotalBytesPercentOfCapacity = retentionBoundedBrokerTotalBytesPercentOfCapacity,
            retentionBoundedBrokerPossibleBytesPercentOfCapacity = retentionBoundedBrokerPossibleBytesPercentOfCapacity,
            possibleClusterUsageLevel = usageLevelClassifier.determineLevel(retentionBoundedBrokerTotalBytesPercentOfCapacity),
            totalPossibleClusterUsageLevel = usageLevelClassifier.determineLevel(retentionBoundedBrokerPossibleBytesPercentOfCapacity),
        )
    }

    private fun <T : Number> T?.orElse(default: T): T = this ?: default

}

