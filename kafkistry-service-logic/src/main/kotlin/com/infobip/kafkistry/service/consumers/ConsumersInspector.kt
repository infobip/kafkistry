package com.infobip.kafkistry.service.consumers

import org.apache.kafka.common.TopicPartition
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.ClusterTopicOffsets
import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.kafkastate.KafkaTopicOffsetsProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.eachCountDescending
import com.infobip.kafkistry.service.topic.offsets.TopicsRateProvider
import org.springframework.stereotype.Component
import com.infobip.kafkistry.kafkastate.ClusterConsumerGroups as ClusterConsumerGroupsState

@Component
class ConsumersInspector(
        private val lagInspection: LagInspection,
        private val consumerGroupsProvider: KafkaConsumerGroupsProvider,
        private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
        private val topicsRateProvider: TopicsRateProvider,
        private val aclLinkResolver: AclLinkResolver
) {

    fun listClusterConsumerGroupIds(clusterIdentifier: KafkaClusterIdentifier): List<ConsumerGroupId> {
        val clusterConsumerGroups = consumerGroupsProvider.getLatestState(clusterIdentifier)
        return clusterConsumerGroups.allConsumerGroupIds()
    }

    fun inspectClusterConsumerGroups(clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName): List<KafkaConsumerGroup> {
        val clusterConsumerGroups = consumerGroupsProvider.getLatestState(clusterIdentifier)
        val clusterTopicOffsets = topicOffsetsProvider.getLatestState(clusterIdentifier)
        return clusterConsumerGroups
            .allConsumerGroupIdsForTopic(topicName)
            .mapNotNull { it.inspectGroup(clusterIdentifier, clusterConsumerGroups, clusterTopicOffsets) }
    }

    fun inspectClusterConsumerGroups(clusterIdentifier: KafkaClusterIdentifier): ClusterConsumerGroups {
        val clusterConsumerGroups = consumerGroupsProvider.getLatestState(clusterIdentifier)
        val clusterTopicOffsets = topicOffsetsProvider.getLatestState(clusterIdentifier)
        val consumerGroups = clusterConsumerGroups
                .allConsumerGroupIds()
                .mapNotNull { it.inspectGroup(clusterIdentifier, clusterConsumerGroups, clusterTopicOffsets) }
        val consumersStats = ConsumersStats(
            clusterCounts = mapOf(clusterIdentifier to 1),
            lagStatusCounts = consumerGroups.groupingBy { it.lag.status }.eachCountDescending(),
            partitionAssignorCounts = consumerGroups.groupingBy { it.partitionAssignor }.eachCountDescending(),
            consumerStatusCounts = consumerGroups.groupingBy { it.status }.eachCountDescending(),
        )
        return ClusterConsumerGroups(
            clusterIdentifier = clusterIdentifier,
            clusterStateType = clusterConsumerGroups.stateType,
            lastRefreshTime = clusterConsumerGroups.lastRefreshTime,
            consumerGroups = consumerGroups,
            consumersStats = consumersStats,
        )
    }

    fun inspectClusterConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ): KafkaConsumerGroup? {
        val consumersAndOffsets = consumerGroupsProvider.getLatestState(clusterIdentifier)
        val clusterTopicOffsets = topicOffsetsProvider.getLatestState(clusterIdentifier)
        return consumerGroupId.inspectGroup(clusterIdentifier, consumersAndOffsets, clusterTopicOffsets)
    }

    private fun ConsumerGroupId.inspectGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        clusterConsumerGroups: StateData<ClusterConsumerGroupsState>,
        clusterTopicOffsets: StateData<ClusterTopicOffsets>
    ): KafkaConsumerGroup? {
        val consumerGroup = clusterConsumerGroups.valueOrNull()?.consumerGroups?.get(this)?.getOrNull()
            ?: return null
        val members = consumerGroup.members.associateBy { it.memberId }
        val assignments = consumerGroup.assignments.associate { TopicPartition(it.topic, it.partition) to it.memberId }
        val offsets = consumerGroup.offsets.associate { TopicPartition(it.topic, it.partition) to it.offset }
        val topicMembers = (assignments.keys + offsets.keys)
            .map { it.topic() }
            .distinct()
            .sorted()
            .map { topic ->
                topicMembers(
                    clusterIdentifier = clusterIdentifier,
                    topicName = topic,
                    allGroupMembers = members,
                    offsets = offsets.filterKeys { it.topic() == topic },
                    assignments = assignments.filterKeys { it.topic() == topic },
                    topicOffsets = clusterTopicOffsets.valueOrNull()?.topicsOffsets?.get(topic),
                )
            }
        return KafkaConsumerGroup(
                groupId = this,
                status = consumerGroup.status,
                partitionAssignor = consumerGroup.partitionAssignor.takeIf { it.isNotBlank() } ?: "[none]",
                lag = topicMembers.map { it.lag }.let { lagInspection.aggregate(it) },
                topicMembers = topicMembers,
                affectingAclRules = aclLinkResolver.findConsumerGroupAffectingAclRules(this, clusterIdentifier)
        )
    }

    private fun topicMembers(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        allGroupMembers: Map<ConsumerMemberId, ConsumerGroupMember>,
        offsets: Map<TopicPartition, Long>,
        assignments: Map<TopicPartition, ConsumerMemberId>,
        topicOffsets: Map<Partition, PartitionOffsets>?
    ): TopicMembers {
        val partitionMembers = (assignments.keys + offsets.keys)
            .distinct()
            .sortedBy { it.partition() }
            .mapNotNull {
                val member = assignments[it]?.let { memberId -> allGroupMembers[memberId] }
                val partitionOffsets = topicOffsets?.let { offsets -> offsets[it.partition()] }
                val partitionRate = topicsRateProvider.topicPartitionMessageRate(clusterIdentifier, topicName, it.partition())
                val committedOffset = offsets[it]
                ConsumerTopicPartitionMember(
                    member = member,
                    partition = it.partition(),
                    offset = committedOffset,
                    lag = lagInspection.inspectLag(
                        partitionOffsets, committedOffset, partitionRate.upTo15MinRate,
                        memberAssigned = member != null,
                    ),
                ).takeIf { member != null || committedOffset != null }
            }
        return TopicMembers(
                topicName = topicName,
                lag = lagInspection.aggregate(partitionMembers.map { it.lag }),
                partitionMembers = partitionMembers,
        )
    }

    private fun StateData<ClusterConsumerGroupsState>.allConsumerGroupIds(): List<ConsumerGroupId> {
        return valueOrNull()?.consumerGroups?.keys?.toList().orEmpty()
    }
    private fun StateData<ClusterConsumerGroupsState>.allConsumerGroupIdsForTopic(topicName: TopicName): List<ConsumerGroupId> {
        return valueOrNull()?.topicConsumerGroups?.get(topicName)?.keys?.toList().orEmpty()
    }

}
