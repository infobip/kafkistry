package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.StateType.*
import com.infobip.kafkistry.kafkastate.brokerdisk.NodeDiskMetric
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.service.NamedType
import com.infobip.kafkistry.service.StatusLevel

enum class StateType(
    override val level: StatusLevel,
    override val valid: Boolean,
    override val doc: String,
) : NamedType {
    VISIBLE(StatusLevel.SUCCESS, true, "Responsive on API calls"),
    UNREACHABLE(StatusLevel.ERROR, false, "Having failed API calls"),
    UNKNOWN(StatusLevel.WARNING, false, "Not having definition for this cluster in registry"),
    INVALID_ID(StatusLevel.CRITICAL, false, "Working with cluster with custer ID different than expected by definition in registry"),
    DISABLED(StatusLevel.IGNORE, true, "Disabled in configuration not to attempt to connect at all"),
}

data class StateData<T>(
    val stateType: StateType,
    val clusterIdentifier: KafkaClusterIdentifier,
    val stateTypeName: String,
    val lastRefreshTime: Long,
    private val value: T? = null
) {
    init {
        when (stateType) {
            VISIBLE -> {
                if (value == null) throw IllegalArgumentException("State data value must not be null when cluster is $stateType")
            }
            UNREACHABLE, UNKNOWN, INVALID_ID, DISABLED -> {
                if (value != null) throw IllegalArgumentException("State data value should be null when cluster is $stateType")
            }
        }
    }

    fun valueOrNull(): T? = value

    fun value(): T {
        return value ?: throw KafkistryIllegalStateException(
            "Can't get data value of '$stateTypeName' for kafka cluster '$clusterIdentifier' since it's state is $stateType"
        )
    }

}

data class KafkaClusterState(
        val clusterInfo: ClusterInfo,
        val topics: List<KafkaExistingTopic>,
        val acls: List<KafkaAclRule>
) {
    val allTopics = topics.associateBy { it.name }
}

data class ClusterConsumerGroups(
    val consumerGroups: Map<ConsumerGroupId, Maybe<ConsumerGroup>>,
    val topicConsumerGroups: Map<TopicName, Map<ConsumerGroupId, ConsumerGroup>> = consumerGroups.toTopicGroups(),
)

private fun Map<ConsumerGroupId, Maybe<ConsumerGroup>>.toTopicGroups(): Map<TopicName, Map<ConsumerGroupId, ConsumerGroup>> {
    return values.asSequence()
        .mapNotNull { it.getOrNull() }
        .flatMap { group ->
            sequence {
                yieldAll(group.offsets.map { it.topic })
                yieldAll(group.assignments.map { it.topic })
            }.distinct().map { it to group }
        }
        .groupBy ({ it.first }, { it.second })
        .mapValues { (_, groups) -> groups.associateBy { it.id } }
}

data class ClusterTopicOffsets(
        val topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
)

data class TopicReplicaInfos(
    val topic: TopicName,
    val partitionBrokerReplicas: Map<Partition, Map<BrokerId, TopicPartitionReplica>>,
    val brokerPartitionReplicas: Map<BrokerId, Map<Partition, TopicPartitionReplica>>,
    val brokerTotalSizes: Map<BrokerId, Long>,
    val totalSizeBytes: Long,
)

data class ReplicaDirs(
    val replicas: Map<TopicName, TopicReplicaInfos>,
)

data class OldestRecordsAges(
    val earliestRecordAges: Map<TopicName, Map<Partition, Long>>,
)

data class TopicPartitionReAssignments(
    val topicReAssignments: List<TopicPartitionReAssignment>,
)

data class ClusterQuotas(
    val quotas: Map<QuotaEntity, QuotaProperties>,
)

data class ClusterNodeMetrics(
    val nodesMetrics: Map<NodeId, NodeDiskMetric>,
    val brokersMetrics: Map<BrokerId, NodeDiskMetric>,
)

sealed class Maybe<out V> {

    fun <R> fold(ifResult: (V) -> R, ifAbsent: (Throwable) -> R): R = when (this) {
        is Result -> ifResult(value)
        is Absent -> ifAbsent(exception)
    }

    fun <R> map(mapper: (V) -> R): Maybe<R> = when (this) {
        is Result -> Result(mapper(value))
        is Absent -> this
    }

    fun getOrNull(): V? = fold({ it }, { null })
    fun getOrThrow(): V = fold({ it }, { throw it })

    data class Result<V>(val value: V) : Maybe<V>()
    data class Absent(val exception: Throwable) : Maybe<Nothing>()
}
