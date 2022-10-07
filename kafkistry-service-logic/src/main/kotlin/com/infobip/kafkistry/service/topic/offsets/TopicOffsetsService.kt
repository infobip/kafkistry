package com.infobip.kafkistry.service.topic.offsets

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.kafkastate.ClusterTopicOffsets
import com.infobip.kafkistry.kafkastate.KafkaTopicOffsetsProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service

@Service
class TopicOffsetsService(
    private val clustersRegistryService: ClustersRegistryService,
    private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
    private val topicsRateProvider: TopicsRateProvider,
) {

    fun allClustersTopicsOffsets(): Map<ClusterRef, ClusterTopicOffsets> {
        return clustersRegistryService.listClustersRefs()
            .mapNotNull { clusterRef ->
                topicOffsetsProvider.getLatestState(clusterRef.identifier)
                    .valueOrNull()
                    ?.let { clusterRef to it }
            }
            .toMap()
    }

    fun clusterTopicsOffsets(clusterIdentifier: KafkaClusterIdentifier): Map<TopicName, TopicOffsets> {
        return topicOffsetsProvider.getLatestStateValue(clusterIdentifier)
            .topicsOffsets
            .mapValues { (topic, partitionOffsets) ->
                topicOffsets(partitionOffsets, clusterIdentifier, topic)
            }
    }

    fun topicOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): TopicOffsets? {
        val topicOffsets = topicOffsetsProvider
            .getLatestState(clusterIdentifier)
            .valueOrNull()
            ?.topicsOffsets?.get(topicName)
            ?: return null
        return topicOffsets(topicOffsets, clusterIdentifier, topicName)
    }

    private fun topicOffsets(
        partitionOffsets: Map<Partition, PartitionOffsets>,
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): TopicOffsets {
        val size = partitionOffsets.values.fold(0L) { acc, offsets ->
            acc + offsets.end - offsets.begin
        }
        return TopicOffsets(
            empty = size == 0L,
            size = size,
            messagesRate = topicsRateProvider.topicMessageRate(clusterIdentifier, topicName),
            partitionsOffsets = partitionOffsets,
            partitionMessageRate = partitionOffsets.keys.associateWith {
                topicsRateProvider.topicPartitionMessageRate(clusterIdentifier, topicName, it)
            }
        )
    }
}