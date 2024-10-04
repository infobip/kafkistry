package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.kafka.ConsumerGroupMember
import com.infobip.kafkistry.kafka.ConsumerGroupStatus
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.NamedType
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.eachCountDescending

data class ClusterConsumerGroups(
    val clusterIdentifier: KafkaClusterIdentifier,
    val clusterStateType: StateType,
    val lastRefreshTime: Long,
    val consumerGroups: List<KafkaConsumerGroup>,
    val consumersStats: ConsumersStats,
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
    val clustersGroups: List<ClusterConsumerGroup>,
)

data class ClusterConsumerGroup(
    val clusterIdentifier: KafkaClusterIdentifier,
    val consumerGroup: KafkaConsumerGroup
)

enum class LagStatus(
    override val level: StatusLevel,
    override val valid: Boolean,
    override val doc: String,
): NamedType {
    NO_LAG(StatusLevel.SUCCESS, true, "Zero lag or small lag comparing to traffic rate"),
    UNKNOWN(StatusLevel.WARNING, false, "Not enough info about topic end offsets and/or consumer's last committed offsets"),
    MINOR_LAG(StatusLevel.WARNING, false, "Has minor lag relative to traffic rate"),
    HAS_LAG(StatusLevel.ERROR, false, "Has significant lag relative to traffic rate"),
    OVERFLOW(StatusLevel.CRITICAL, false, "Last committed offset by consumer is older than begin offset (oldest) in topic partition"),
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
    val partitionAssignorCounts: Map<String, Int>,
)

fun List<ClusterConsumerGroup>.computeStats() = ConsumersStats(
    clusterCounts = groupingBy { it.clusterIdentifier }.eachCountDescending(),
    lagStatusCounts = groupingBy { it.consumerGroup.lag.status }.eachCountDescending(),
    partitionAssignorCounts = groupingBy { it.consumerGroup.partitionAssignor }.eachCountDescending(),
    consumerStatusCounts = groupingBy { it.consumerGroup.status }.eachCountDescending()
)


