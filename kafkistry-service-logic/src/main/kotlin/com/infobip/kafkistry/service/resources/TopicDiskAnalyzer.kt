package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.ExistingTopicInfo
import com.infobip.kafkistry.service.configForCluster
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.propertiesForCluster
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import org.apache.kafka.common.config.TopicConfig
import org.springframework.stereotype.Component

@Component
class TopicDiskAnalyzer(
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val topicsInspectionService: TopicsInspectionService,
    private val replicaDirsService: ReplicaDirsService,
    private val assignor: PartitionsReplicasAssignor,
) {

    fun analyzeTopicDiskUsage(
        topic: TopicName,
        topicDescription: TopicDescription?,
        clusterRef: ClusterRef,
        preferUsingDescriptionProps: Boolean,
    ): TopicClusterDiskUsage {
        val brokerIds: List<BrokerId> = clustersStateProvider.getLatestClusterStateValue(clusterRef.identifier)
            .clusterInfo.nodeIds
        val topicClusterStatus = if (topicDescription != null) {
            topicsInspectionService.inspectTopicOnCluster(topicDescription, clusterRef)
        } else {
            topicsInspectionService.inspectTopicOnCluster(topic, clusterRef)
        }
        val replicaInfos = replicaDirsService.topicReplicaInfos(clusterRef.identifier, topic)
        val existingAssignments = existingAssignments(
            topicClusterStatus.existingTopicInfo, replicaInfos
        )
        val topicProperties = if (preferUsingDescriptionProps) {
            topicDescription?.propertiesForCluster(clusterRef) ?: existingAssignments?.topicProperties()
        } else {
            existingAssignments?.topicProperties() ?: topicDescription?.propertiesForCluster(clusterRef)
        }
        val brokerReplicaCounts = topicProperties
            ?.let { brokerReplicasCount(it, brokerIds, existingAssignments) }
            ?.brokerReplicaCount()
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
        val existingBrokerAssignments = topicClusterStatus.existingTopicInfo
            ?.partitionsAssignments
            ?.flatMap { (partition, replicas) -> replicas.map { it.brokerId to partition } }
            ?.groupBy({ it.first }, { it.second })
        val brokerExistingRetentionBoundedBytes = existingBrokerAssignments
            ?.mapValues { (_, partitions) ->
                existingTopicRetentionBytes?.let { partitions.size * it }
            }
        val brokerOrphanedReplicas = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (brokerId, partitionReplicas) ->
                val assignedPartitions = existingBrokerAssignments?.get(brokerId).orEmpty()
                partitionReplicas.keys.count { it !in assignedPartitions }
            }
        val brokerOrphanedBytes = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (brokerId, partitionReplicas) ->
                val assignedPartitions = existingBrokerAssignments?.get(brokerId).orEmpty()
                partitionReplicas.filter { it.key !in assignedPartitions }.entries.sumOf { it.value.sizeBytes }
            }
        val brokerActualUsage = replicaInfos?.brokerActualUsageBytes()
        val brokerRetentionBoundedBytes = computeBrokerPossibleUsageBytes(
            brokerReplicaCounts, configuredReplicaRetentionBytes
        )
        val brokerUsages = brokerIds.associateWith {
            val retentionBoundedBytes = brokerRetentionBoundedBytes?.run { this[it] ?: 0L }
            val actualUsedBytes = brokerActualUsage?.run { this[it] ?: 0L }
            TopicDiskUsage(
                replicasCount = brokerReplicaCounts?.get(it) ?: 0,
                orphanedReplicasCount = brokerOrphanedReplicas?.get(it) ?: 0,
                actualUsedBytes = actualUsedBytes,
                expectedUsageBytes = topicClusterStatus.resourceRequiredUsages.value?.diskUsagePerBroker,
                retentionBoundedBytes = retentionBoundedBytes,
                existingRetentionBoundedBytes = brokerExistingRetentionBoundedBytes?.get(it),
                orphanedUsedBytes = brokerOrphanedBytes?.get(it) ?: 0L,
                unboundedUsageBytes = if (configuredReplicaRetentionBytes == INF_RETENTION) actualUsedBytes else 0L,
            )
        }
        val combinedUsage = brokerUsages.values.reduce(TopicDiskUsage::plus)
        return TopicClusterDiskUsage(
            unboundedSizeRetention = configuredReplicaRetentionBytes == INF_RETENTION,
            configuredReplicaRetentionBytes = configuredReplicaRetentionBytes ?: 0L,
            combined = combinedUsage,
            brokerUsages = brokerUsages,
        )
    }

    private fun computeBrokerPossibleUsageBytes(
        brokerReplicaCounts: Map<BrokerId, Int>?,
        configuredReplicaRetentionBytes: Long?
    ) = if (brokerReplicaCounts != null && configuredReplicaRetentionBytes?.takeIf { it != INF_RETENTION } != null) {
        brokerReplicaCounts.mapValues { (_, replicas) -> configuredReplicaRetentionBytes * replicas }
    } else {
        null
    }

    private fun existingAssignments(
        existingTopicInfo: ExistingTopicInfo?,
        replicaInfos: TopicReplicaInfos?,
    ): Map<Partition, List<BrokerId>>? {
        if (existingTopicInfo != null) {
            return existingTopicInfo.partitionsAssignments.associate { partitionAssignments ->
                partitionAssignments.partition to partitionAssignments.replicasAssignments.map { it.brokerId }
            }
        }
        if (replicaInfos != null) {
            return replicaInfos
                .partitionBrokerReplicas
                .mapValues { (_, brokerReplicas) -> brokerReplicas.keys.toList() }
        }
        return null
    }

    private fun TopicReplicaInfos.brokerActualUsageBytes(): Map<BrokerId, Long> = brokerPartitionReplicas
        .mapValues { (_, partitionReplicas) ->
            partitionReplicas.entries.sumOf { it.value.sizeBytes }
        }

    private fun brokerReplicasCount(
        topicProperties: TopicProperties,
        brokerIds: List<BrokerId>,
        existingAssignments: Map<Partition, List<BrokerId>>?,
    ): Map<Partition, List<BrokerId>> {
        if (existingAssignments == null) {
            //topic doesn't exist, generate all assignments
            return assignor.assignNewPartitionReplicas(
                emptyMap(), brokerIds,
                topicProperties.partitionCount, topicProperties.replicationFactor,
                emptyMap()
            ).newAssignments
        }
        val existingTopicProperties = existingAssignments.topicProperties()
        val correctRFAssignments = when {
            topicProperties.replicationFactor < existingTopicProperties.replicationFactor -> assignor
                .reduceReplicationFactor(existingAssignments, topicProperties.replicationFactor)
                .newAssignments
            topicProperties.replicationFactor > existingTopicProperties.replicationFactor -> assignor
                .assignPartitionsNewReplicas(
                    existingAssignments, brokerIds,
                    replicationFactorIncrease = topicProperties.replicationFactor - existingTopicProperties.replicationFactor,
                    emptyMap()
                ).newAssignments
            else -> existingAssignments
        }
        return if (topicProperties.partitionCount > existingTopicProperties.partitionCount) {
            assignor.assignNewPartitionReplicas(
                correctRFAssignments, brokerIds,
                numberOfNewPartitions = topicProperties.partitionCount - existingTopicProperties.partitionCount,
                topicProperties.replicationFactor, emptyMap()
            ).newAssignments
        } else {
            correctRFAssignments
        }
    }

    private fun Map<Partition, List<BrokerId>>.brokerReplicaCount(): Map<BrokerId, Int> = asSequence()
        .flatMap { (_, replicas) -> replicas.map { it } }
        .groupingBy { it }
        .eachCount()

    private fun Map<Partition, List<BrokerId>>.topicProperties() = TopicProperties(
        partitionCount = size,
        replicationFactor = values.maxOf { it.size },
    )

}

