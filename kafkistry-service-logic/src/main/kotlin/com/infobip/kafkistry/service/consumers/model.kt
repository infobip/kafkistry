package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.kafka.ConsumerGroupMember
import com.infobip.kafkistry.kafka.ConsumerGroupStatus
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

data class ClusterConsumerGroups(
    val clusterIdentifier: KafkaClusterIdentifier,
    val clusterStateType: StateType,
    val lastRefreshTime: Long,
    val consumerGroups: List<KafkaConsumerGroup>
)

data class KafkaConsumerGroup(
    val groupId: ConsumerGroupId,
    val status: ConsumerGroupStatus,
    val partitionAssignor: String,
    val lag: Lag,
    val topicMembers: List<TopicMembers>,
    val affectingAclRules: List<KafkaAclRule>
)

data class TopicMembers(
        val topicName: TopicName,
        val lag: Lag,
        val partitionMembers: List<ConsumerTopicPartitionMember>
)

data class ConsumerTopicPartitionMember(
        val member: ConsumerGroupMember?,
        val partition: Int,
        val offset: Long?,
        val lag: Lag,
)

data class AllConsumersData(
        val clustersDataStatuses: List<ClusterDataStatus>,
        val consumersStats: ConsumersStats,
        val clustersGroups: List<ClusterConsumerGroup>
)

data class ClusterConsumerGroup(
    val clusterIdentifier: KafkaClusterIdentifier,
    val consumerGroup: KafkaConsumerGroup
)

enum class LagStatus(val level: Int) {
    NO_LAG(0),
    UNKNOWN(1),
    MINOR_LAG(2),
    HAS_LAG(3),
    OVERFLOW(4),
}

data class ClusterDataStatus(
    val clusterIdentifier: KafkaClusterIdentifier,
    val clusterStatus: StateType,
    val lastRefreshTime: Long
)

data class ConsumersStats(
    val clusterCounts: Map<KafkaClusterIdentifier, Int>,
    val lagStatusCounts: Map<LagStatus, Int>,
    val consumerStatusCounts: Map<ConsumerGroupStatus, Int>,
    val partitionAssignorCounts: Map<String, Int>
)

