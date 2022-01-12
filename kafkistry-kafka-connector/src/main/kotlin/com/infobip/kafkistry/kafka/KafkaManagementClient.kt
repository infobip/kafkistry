package com.infobip.kafkistry.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntity
import java.util.concurrent.CompletableFuture

interface KafkaManagementClient : AutoCloseable {

    fun test()
    fun clusterInfo(identifier: KafkaClusterIdentifier): CompletableFuture<ClusterInfo>
    fun describeReplicas(): CompletableFuture<List<TopicPartitionReplica>>
    fun listReAssignments(): CompletableFuture<List<TopicPartitionReAssignment>>
    fun listAllTopicNames(): CompletableFuture<List<TopicName>>
    fun listAllTopics(): CompletableFuture<List<KafkaExistingTopic>>
    fun createTopic(topic: KafkaTopicConfiguration): CompletableFuture<Unit>
    fun deleteTopic(topicName: TopicName): CompletableFuture<Unit>
    fun updateTopicConfig(topicName: TopicName, updatingConfig: TopicConfigMap): CompletableFuture<Unit>
    fun setBrokerConfig(brokerId: BrokerId, config: Map<String, String>): CompletableFuture<Unit>
    fun unsetBrokerConfig(brokerId: BrokerId, configKeys: Set<String>): CompletableFuture<Unit>
    fun updateThrottleRate(brokerId: BrokerId, throttleRate: ThrottleRate): CompletableFuture<Unit>
    fun addTopicPartitions(topicName: TopicName, totalPartitionsCount: Int, newPartitionsAssignments: Map<Partition, List<BrokerId>>): CompletableFuture<Unit>
    fun reAssignPartitions(topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>, throttleBytesPerSec: Int): CompletableFuture<Unit>
    fun reAssignPartitions(topicPartitionsAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>, throttleBytesPerSec: Int): CompletableFuture<Unit>
    fun verifyReAssignPartitions(topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>): String
    fun cancelReAssignments(topicName: TopicName, partitions: List<Partition>): CompletableFuture<Unit>
    fun runPreferredReplicaElection(topicName: TopicName, partitions: List<Partition>)
    fun topicsOffsets(topicNames: List<TopicName>): CompletableFuture<Map<TopicName, Map<Partition, PartitionOffsets>>>
    fun consumerGroups(): CompletableFuture<List<ConsumerGroupId>>
    fun consumerGroup(groupId: ConsumerGroupId): CompletableFuture<ConsumerGroup>
    fun deleteConsumer(groupId: ConsumerGroupId): CompletableFuture<Unit>
    fun deleteConsumerOffsets(groupId: ConsumerGroupId, topicPartitions: Map<TopicName, List<Partition>>): CompletableFuture<Unit>
    fun listAcls(): CompletableFuture<List<KafkaAclRule>>
    fun createAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit>
    fun deleteAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit>
    fun resetConsumerGroup(groupId: ConsumerGroupId, reset: GroupOffsetsReset): CompletableFuture<GroupOffsetResetChange>
    fun sampleRecords(topicPartitionOffsets: TopicPartitionOffsets, samplingPosition: SamplingPosition, recordVisitor: RecordVisitor)
    fun listQuotas(): CompletableFuture<List<ClientQuota>>
    fun setClientQuotas(quotas: List<ClientQuota>): CompletableFuture<Unit>
    fun removeClientQuotas(quotaEntities: List<QuotaEntity>): CompletableFuture<Unit>

}

typealias TopicPartitionOffsets = Map<TopicName, Map<Partition, PartitionOffsets>>

interface RecordVisitor {

    fun visit(record: ConsumerRecord<ByteArray?, ByteArray?>)

    fun compose(consumer: (ConsumerRecord<ByteArray?, ByteArray?>) -> Unit) = of { consumer(it); visit(it) }

    companion object {
        fun of(consumer: (ConsumerRecord<ByteArray?, ByteArray?>) -> Unit) = object : RecordVisitor {
            override fun visit(record: ConsumerRecord<ByteArray?, ByteArray?>) {
                consumer(record)
            }
        }
    }
}

enum class SamplingPosition {
    OLDEST, NEWEST
}
