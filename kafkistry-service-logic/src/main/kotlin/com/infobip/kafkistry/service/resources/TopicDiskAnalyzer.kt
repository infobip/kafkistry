package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.configForCluster
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.propertiesForCluster
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
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
                    //or we prefer using props from topic's description
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
        val brokerExistingRetentionBoundedBytes = existingAssignments
            ?.mapValues { (_, partitions) ->
                existingTopicRetentionBytes?.let { partitions.size * it }
            }
        val brokerOrphanedReplicas = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (brokerId, partitionReplicas) ->
                val assignedPartitions = existingAssignments?.get(brokerId).orEmpty()
                partitionReplicas.keys.count { it !in assignedPartitions }
            }
        val brokerOrphanedBytes = replicaInfos?.brokerPartitionReplicas
            ?.mapValues { (brokerId, partitionReplicas) ->
                val assignedPartitions = existingAssignments?.get(brokerId).orEmpty()
                partitionReplicas.filter { it.key !in assignedPartitions }.entries.sumOf { it.value.sizeBytes }
            }
        val brokerUsages = brokerIds.associateWith {
            val retentionBoundedBytes = brokerRetentionBoundedBytes?.run { this[it] ?: 0L }
            val actualUsedBytes = brokerActualUsage?.run { this[it] ?: 0L }
            TopicDiskUsage(
                replicasCount = brokerReplicaCounts?.get(it) ?: 0,
                orphanedReplicasCount = brokerOrphanedReplicas?.get(it) ?: 0,
                actualUsedBytes = actualUsedBytes,
                expectedUsageBytes = expectedUsageBytes,
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

}

