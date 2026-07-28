package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.GroupState
import org.apache.kafka.common.GroupType
import com.infobip.kafkistry.shaded.org.apache.kafka.clients.admin.ListConsumerGroupsOptions as LegacyConsumerGroupsOptions
import com.infobip.kafkistry.shaded.org.apache.kafka.common.GroupType as LegacyGroupType
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.GroupIdNotFoundException
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

class ConsumerGroupOps(
    clientCtx: ClientCtx,
): BaseOps(clientCtx) {

    fun consumerGroups(): CompletableFuture<List<ConsumerGroupId>> {
        return if (clusterVersion < VERSION_4_0) {
            @Suppress("DEPRECATION")
            legacyAdminClient
                .listConsumerGroups(LegacyConsumerGroupsOptions().withReadTimeout())
                .valid()
                .asCompletableFutureLegacy("list consumer groups legacy")
                .thenApply { groups ->
                    groups
                        .map {
                            val type = it.type().getOrNull() ?: LegacyGroupType.UNKNOWN
                            KafkaGroup(it.groupId(), type.convert()) }
                        .sortedBy { it.groupId }
                }
        } else {
            adminClient.listGroups(ListGroupsOptions().withReadTimeout())
                .valid()
                .asCompletableFuture("list groups")
                .thenApply { groups ->
                    groups
                        .map { KafkaGroup(it.groupId(), it.type().getOrNull() ?: GroupType.UNKNOWN) }
                        .filter { it.type == GroupType.CONSUMER || it.type == GroupType.CLASSIC }
                        .sortedBy { it.groupId }
                }
        }.thenApply { groups -> groups.map { it.groupId } }
    }

    fun consumerGroup(groupId: ConsumerGroupId): CompletableFuture<ConsumerGroup> {
        val groupDescriptionFuture = adminClient
            .describeConsumerGroups(listOf(groupId), DescribeConsumerGroupsOptions().withReadTimeout())
            .describedGroups()[groupId]!!
            .asCompletableFuture("describe consumer group")
            .exceptionally { ex: Throwable ->
                if (ex is KafkaClusterManagementException && ex.cause is GroupIdNotFoundException) {
                    //TODO rethink - previous versions did not throw GroupIdNotFoundException, and had simply returned semantically empty description
                    ConsumerGroupDescription(
                        groupId, true, emptyList(), "",
                        GroupType.UNKNOWN, GroupState.UNKNOWN, null,
                        emptySet(), Optional.empty(), Optional.empty(),
                    )
                } else {
                    throw ex
                }
            }
        val topicPartitionOffsetsFuture = adminClient
            .listConsumerGroupOffsets(groupId, ListConsumerGroupOffsetsOptions().withReadTimeout())
            .partitionsToOffsetAndMetadata()
            .asCompletableFuture("list consumer group offsets")
            .thenApply { topicsOffsets -> topicsOffsets.mapValues { it.value?.offset() } }
        return groupDescriptionFuture.thenCombine(topicPartitionOffsetsFuture) { groupDescription, topicPartitionOffsets ->
            combineGroupData(groupId, groupDescription, topicPartitionOffsets)
        }
    }

    fun consumerGroups(groupIds: List<ConsumerGroupId>): CompletableFuture<List<ConsumerGroup>> {
        if (clusterVersion < VERSION_3_0) {
            val groupsFutures = groupIds.map { consumerGroup(it) }
            return CompletableFuture.allOf(*groupsFutures.toTypedArray())
                .thenApply { groupsFutures.map { it.get() } }
        }
        val groupsDescriptionFuture = adminClient
            .describeConsumerGroups(groupIds, DescribeConsumerGroupsOptions().withReadTimeout())
            .all()
            .asCompletableFuture("describe consumer groups")
        val groupTopicPartitionOffsetsFuture = adminClient
            .listConsumerGroupOffsets(
                groupIds.associateWith { ListConsumerGroupOffsetsSpec() },
                ListConsumerGroupOffsetsOptions().withReadTimeout()
            )
            .all()
            .asCompletableFuture("list consumer groups offsets")
            .thenApply { groupTopicsOffsets ->
                groupTopicsOffsets.mapValues { (_, topicsOffsets) ->
                    topicsOffsets.mapValues { it.value?.offset() }
                }
            }
        return groupsDescriptionFuture.thenCombine(groupTopicPartitionOffsetsFuture) { groupDescriptions, groupTopicPartitionOffsets ->
            groupIds.map { groupId ->
                combineGroupData(groupId, groupDescriptions.getValue(groupId), groupTopicPartitionOffsets.getValue(groupId))
            }
        }
    }

    private fun combineGroupData(
        groupId: ConsumerGroupId,
        groupDescription: ConsumerGroupDescription,
        topicPartitionOffsets: Map<TopicPartition, Long?>,
    ): ConsumerGroup {
        val members = groupDescription.members().map {
            ConsumerGroupMember(
                memberId = it.consumerId(),
                clientId = it.clientId(),
                host = it.host()
            )
        }.sortedBy { it.memberId }
        val offsets = topicPartitionOffsets
            .mapNotNull { (tp, offset) ->
                offset?.let { TopicPartitionOffset(tp.topic(), tp.partition(), it) }
            }
            .sortedBy { it.topic + it.partition }
        val assignments = groupDescription.members()
            .flatMap { member ->
                member.assignment().topicPartitions().map {
                    TopicPartitionMemberAssignment(
                        topic = it.topic(),
                        partition = it.partition(),
                        memberId = member.consumerId(),
                    )
                }
            }
            .sortedBy { it.topic + it.partition }
        return ConsumerGroup(
            id = groupId,
            status = groupDescription.groupState().convert(),
            partitionAssignor = groupDescription.partitionAssignor(),
            members = members,
            offsets = offsets,
            assignments = assignments,
        )
    }

    fun deleteConsumer(groupId: ConsumerGroupId): CompletableFuture<Unit> {
        return adminClient
            .deleteConsumerGroups(listOf(groupId), DeleteConsumerGroupsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete consumer group")
            .thenApply { }
    }

    fun deleteConsumerOffsets(
        groupId: ConsumerGroupId, topicPartitions: Map<TopicName, List<Partition>>
    ): CompletableFuture<Unit> {
        val topicPartitionsSet = topicPartitions.flatMap { (topic, partitions) ->
            partitions.map { TopicPartition(topic, it) }
        }.toSet()
        if (topicPartitionsSet.isEmpty()) {
            return CompletableFuture.completedFuture(Unit)
        }
        return adminClient
            .deleteConsumerGroupOffsets(groupId, topicPartitionsSet, DeleteConsumerGroupOffsetsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete consumer group offsets")
            .thenApply { }
    }

}