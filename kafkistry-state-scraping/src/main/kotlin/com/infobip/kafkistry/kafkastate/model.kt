package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.StateType.*
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties

enum class StateType {
    VISIBLE,
    UNREACHABLE,
    UNKNOWN,
    INVALID_ID,
    DISABLED
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
)

data class ClusterConsumerGroups(
    val consumerGroups: Map<ConsumerGroupId, Maybe<ConsumerGroup>>,
)

data class ClusterTopicOffsets(
        val topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
)

data class ReplicaDirs(
        val replicas: List<TopicPartitionReplica>
)

data class OldestRecordsAges(
        val earliestRecordAges: Map<TopicName, Map<Partition, Long>>
)

data class TopicPartitionReAssignments(
        val topicReAssignments: List<TopicPartitionReAssignment>
)

data class ClusterQuotas(
        val quotas: Map<QuotaEntity, QuotaProperties>
)

data class ClusterBrokerMetrics(
        val brokersMetrics: Map<BrokerId, BrokerDiskMetric>
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
