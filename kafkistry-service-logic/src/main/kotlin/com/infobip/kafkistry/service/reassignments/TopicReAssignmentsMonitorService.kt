package com.infobip.kafkistry.service.reassignments

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReAssignment
import com.infobip.kafkistry.kafkastate.KafkaTopicReAssignmentsProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Service

@Service
class TopicReAssignmentsMonitorService(
        private val topicReAssignmentsProvider: KafkaTopicReAssignmentsProvider
) {

    fun topicReAssignments(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): Map<Partition, TopicPartitionReAssignment> {
        return topicReAssignmentsProvider.getLatestState(clusterIdentifier)
                .valueOrNull()
                ?.topicReAssignments?.filter { it.topic == topicName }
                ?.takeIf { it.isNotEmpty() }
                ?.associateBy { it.partition }
                ?: emptyMap()
    }
    fun clusterTopicsReAssignments(
            clusterIdentifier: KafkaClusterIdentifier
    ): Map<TopicName, Map<Partition, TopicPartitionReAssignment>> {
        return topicReAssignmentsProvider.getLatestState(clusterIdentifier)
                .valueOrNull()
                ?.topicReAssignments
                ?.groupBy { it.topic }
                ?.mapValues { (_, partitionReAssignments) ->
                    partitionReAssignments.associateBy { it.partition }
                }
                ?: emptyMap()
    }

}