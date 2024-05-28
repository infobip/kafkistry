package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.NamedType
import com.infobip.kafkistry.service.StatusLevel
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.QuorumInfo
import java.io.Serializable
import java.util.*

data class ConnectionDefinition(
    val connectionString: String,
    val ssl: Boolean,
    val sasl: Boolean,
    val profiles: List<KafkaProfile>,
)

fun KafkaCluster.connectionDefinition() = ConnectionDefinition(connectionString, sslEnabled, saslEnabled, profiles)

data class KafkaTopicConfiguration(
    val name: TopicName,
    val partitionsReplicas: Map<Partition, List<BrokerId>>,
    val config: TopicConfigMap,
)

data class KafkaExistingTopic(
    val name: TopicName,
    val internal: Boolean,
    val config: ExistingConfig,
    val partitionsAssignments: List<PartitionAssignments>,
    val uuid: TopicUUID? = null,
)

data class PartitionAssignments(
    val partition: Partition,
    val replicasAssignments: List<ReplicaAssignment>,
)

fun List<PartitionAssignments>.toPartitionReplicasMap(): Map<Partition, List<BrokerId>> {
    return associate { it.partition to it.replicasAssignments.map { replica -> replica.brokerId } }
}

fun List<PartitionAssignments>.partitionLeadersMap(): Map<Partition, BrokerId> {
    return associate {
        it.partition to it.replicasAssignments
                .filter { replica -> replica.leader }
                .map { replica -> replica.brokerId }.first()
    }
}

data class ReplicaAssignment(
    val brokerId: BrokerId,
    val leader: Boolean,
    val inSyncReplica: Boolean,
    val preferredLeader: Boolean,
    val rank: Int,
)

typealias ExistingConfig = Map<String, ConfigValue>
typealias Partition = Int
typealias BrokerId = Int

data class ConfigValue(
    val value: String?,
    val default: Boolean,
    val readOnly: Boolean,
    val sensitive: Boolean,
    val source: ConfigEntry.ConfigSource,
)

data class ClusterInfo(
    val clusterId: KafkaClusterId,
    val identifier: KafkaClusterIdentifier,
    val config: ExistingConfig,
    val perBrokerConfig: Map<BrokerId, ExistingConfig>,
    val perBrokerThrottle: Map<BrokerId, ThrottleRate>,
    val controllerId: Int,
    val nodeIds: List<BrokerId>,
    val onlineNodeIds: List<BrokerId>,
    val brokers: List<ClusterBroker>,
    val connectionString: String,
    val zookeeperConnectionString: String,
    val clusterVersion: Version?,
    val securityEnabled: Boolean,
    val kraftEnabled: Boolean,
    val features: ClusterFeatures,
    val quorumInfo: ClusterQuorumInfo,
)

data class ClusterBroker(
    val brokerId: BrokerId,
    val host: String,
    val port: Int,
    val rack: String? = null,
)

data class Version(
    val numbers: List<Int>,
) {
    init {
        require(numbers.isNotEmpty()) { "Version needs to have numbers" }
    }

    fun major() = numbers[0]
    fun minor() = numbers[1]

    constructor(vararg numbers: Int) : this(listOf(*numbers.toTypedArray()))

    companion object {
        fun of(version: String) = version.split(".").asSequence()
                .flatMap { Regex("^\\d+").findAll(it).map { m -> m.value } }
                .map { it.toInt() }
                .let { Version(it.toList()) }

        fun parse(version: String) = try {
            of(version)
        } catch (ex: Exception) {
            null
        }
    }

    override fun toString() = numbers.joinToString(".")

    infix operator fun compareTo(other: Version): Int {
        (numbers zip other.numbers).forEach { (thisVer, otherVer) ->
            thisVer.compareTo(otherVer).also { if (it != 0) return it }
        }
        return numbers.size.compareTo(other.numbers.size)
    }

}

typealias ConsumerMemberId = String

data class PartitionOffsets(
    val begin: Long,
    val end: Long,
)

enum class ConsumerGroupStatus(
    override val level: StatusLevel,
    override val valid: Boolean,
    override val doc: String,
) : NamedType {
    UNKNOWN(StatusLevel.WARNING, false, "Not valid state, but instead it's a placeholder for un-parsable API response from kafka"),
    PREPARING_REBALANCE(StatusLevel.INFO, true, "The consumer group is to be rebalanced, triggered by some of following: new member wants to join, member left, member updated, failure in member heartbeat."),
    COMPLETING_REBALANCE(StatusLevel.INFO, true, "All members have joined the group (re-balance timeout reached). Assigning partitions to members."),
    STABLE(StatusLevel.SUCCESS, true, "Members in the consumer group are active and can consume messages normally."),
    DEAD(StatusLevel.ERROR, false, "The consumer group has no members and no metadata. The group is going to be removed from Kafka node soon. It might be due to the inactivity, or the group is being migrated to different group coordinator."),
    EMPTY(StatusLevel.WARNING, false, "The consumer group has metadata but has no joined members. All consumer members are down. If this group won't be used anymore consider deleting it."),
}

data class ConsumerGroup(
    val id: ConsumerGroupId,
    val status: ConsumerGroupStatus,
    val partitionAssignor: String,
    val members: List<ConsumerGroupMember>,
    val offsets: List<TopicPartitionOffset>,
    val assignments: List<TopicPartitionMemberAssignment>,
)

data class ConsumerGroupMember(
        val memberId: ConsumerMemberId,
        val clientId: String,
        val host: String,
)

data class TopicPartitionOffset(
        val topic: TopicName,
        val partition: Partition,
        val offset: Long,
)

data class TopicPartitionMemberAssignment(
        val topic: TopicName,
        val partition: Partition,
        val memberId: ConsumerMemberId,
)

data class KafkaAclRule(
    val principal: PrincipalId,
    val resource: AclResource,
    val host: String,
    val operation: AclOperation,
) {
    override fun toString() = asString()
}

data class GroupOffsetsReset(
    val seek: OffsetSeek,
    val topics: List<TopicSeek>,
)

data class TopicSeek(
    val topic: TopicName,
    val partitions: List<PartitionSeek>? = null,
)

data class PartitionSeek(
    val partition: Partition,
    val seek: OffsetSeek? = null,
)

data class OffsetSeek(
    val type: OffsetSeekType,
    val offset: Long? = null,
    val timestamp: Long? = null,
    val cloneFromConsumerGroup: ConsumerGroupId? = null,
) {
    init {
        when (type) {
            OffsetSeekType.EXPLICIT -> requireNotNull(offset)
            OffsetSeekType.EARLIEST -> requireNotNull(offset)
            OffsetSeekType.LATEST -> requireNotNull(offset)
            OffsetSeekType.RELATIVE -> requireNotNull(offset)
            OffsetSeekType.TIMESTAMP -> requireNotNull(timestamp)
            OffsetSeekType.CLONE -> requireNotNull(cloneFromConsumerGroup)
        }
    }
    fun offset() = offset ?: throw IllegalStateException("Offest not available for type $type")
    fun timestamp() = timestamp ?: throw IllegalStateException("Timestamp not available for type $type")
    fun cloneFromConsumerGroup() = cloneFromConsumerGroup ?: throw IllegalStateException("Cloned consumer group not available for type $type")
}

enum class OffsetSeekType {
    EARLIEST, LATEST, EXPLICIT, TIMESTAMP, RELATIVE, CLONE
}

data class GroupOffsetResetChange(
    val groupId: ConsumerGroupId,
    val changes: List<TopicPartitionOffsetChange>,
    val totalSkip: Long,
    val totalRewind: Long,
    val totalLag: Long,
) : Serializable

data class TopicPartitionOffsetChange(
        val topic: TopicName,
        val partition: Partition,
        val offset: Long,
        val delta: Long?,
        val lag: Long,
) : Serializable

data class TopicPartitionReplica(
    val rootDir: String,
    val brokerId: BrokerId,
    val topic: TopicName,
    val partition: Partition,
    val sizeBytes: Long,
    val offsetLag: Long,
    val isFuture: Boolean,
)

data class TopicPartitionReAssignment(
    val topic: TopicName,
    val partition: Partition,
    val addingReplicas: List<BrokerId>,
    val removingReplicas: List<BrokerId>,
    val allReplicas: List<BrokerId>,
)

data class ThrottleRate(
    val leaderRate: Long? = null,
    val followerRate: Long? = null,
    val alterDirIoRate: Long? = null,
) {
    companion object {
        val NO_THROTTLE = ThrottleRate()
    }
}

data class ClientQuota(
    val entity: QuotaEntity,
    val properties: QuotaProperties,
)

data class ClusterFeatures(
    val finalizedFeatures: Map<String, VersionsRange>,
    val supportedFeatures: Map<String, VersionsRange>,
    val finalizedFeaturesEpoch: Long?,
) {
    companion object {
        val EMPTY = ClusterFeatures(emptyMap(), emptyMap(), null)
    }
}

data class VersionsRange(
    val minVersion: Int,
    val maxVersion: Int,
)

data class ClusterQuorumInfo(
    val leaderId: Int,
    val leaderEpoch: Long,
    val highWatermark: Long,
    val voters: List<QuorumReplicaState>,
    val observers: List<QuorumReplicaState>,
) {
    companion object {
        val EMPTY = ClusterQuorumInfo(0, 0, 0, emptyList(), emptyList())
    }
}

data class QuorumReplicaState(
    val replicaId: Int,
    val logEndOffset: Long,
    val lastFetchTimestamp: Long?,
    val lastCaughtUpTimestamp: Long?,
)

