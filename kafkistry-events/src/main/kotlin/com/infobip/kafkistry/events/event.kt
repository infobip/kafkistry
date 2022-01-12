package com.infobip.kafkistry.events

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.GroupOffsetResetChange
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.QuotaEntityID
import java.io.Serializable

interface KafkistryEvent : Serializable

abstract class TopicEvent : KafkistryEvent {
    abstract val clusterIdentifier: KafkaClusterIdentifier
    abstract val topicName: TopicName
}

data class TopicCreatedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val topicName: TopicName) : TopicEvent()
data class TopicDeletedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val topicName: TopicName) : TopicEvent()
data class TopicUpdatedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val topicName: TopicName) : TopicEvent()

abstract class RepositoryEvent : KafkistryEvent

data class RepositoryRefreshEvent(val externallyTriggered: Boolean = true) : RepositoryEvent()
data class ClustersRepositoryEvent(val clusterIdentifier: KafkaClusterIdentifier?) : RepositoryEvent()
data class TopicsRepositoryEvent(val topicName: TopicName?) : RepositoryEvent()
data class AclsRepositoryEvent(val principalId: PrincipalId?) : RepositoryEvent()
data class QuotasRepositoryEvent(val quotaEntityID: QuotaEntityID?) : RepositoryEvent()

abstract class ConsumerGroupEvent : KafkistryEvent {
    abstract val clusterIdentifier: KafkaClusterIdentifier
    abstract val consumerGroupId: ConsumerGroupId
}

data class ConsumerGroupDeletedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val consumerGroupId: ConsumerGroupId) : ConsumerGroupEvent()
data class ConsumerGroupResetEvent(
    override val clusterIdentifier: KafkaClusterIdentifier,
    override val consumerGroupId: ConsumerGroupId,
    val resetChange: GroupOffsetResetChange
) : ConsumerGroupEvent()
data class ConsumerGroupOffsetsDeletedEvent(
    override val clusterIdentifier: KafkaClusterIdentifier,
    override val consumerGroupId: ConsumerGroupId,
    val topicPartitions: Map<TopicName, List<Partition>>,
) : ConsumerGroupEvent()

abstract class AclEvent : KafkistryEvent {
    abstract val clusterIdentifier: KafkaClusterIdentifier
    abstract val principalId: PrincipalId
}

data class AclCreatedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val principalId: PrincipalId) : AclEvent()
data class AclDeletedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val principalId: PrincipalId) : AclEvent()

abstract class ClusterEvent : KafkistryEvent {
    abstract val clusterIdentifier: KafkaClusterIdentifier
}

data class ClusterThrottleUpdateEvent(
    override val clusterIdentifier: KafkaClusterIdentifier,
    val brokerId: BrokerId? = null
) : ClusterEvent()
data class ClusterTopicsReAssignmentEvent(
    override val clusterIdentifier: KafkaClusterIdentifier,
    val topicNames: List<TopicName>
) : ClusterEvent()

abstract class QuotasEvent : KafkistryEvent {
    abstract val clusterIdentifier: KafkaClusterIdentifier
    abstract val entityID: QuotaEntityID
}

data class QuotasCreatedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val entityID: QuotaEntityID) : QuotasEvent()
data class QuotasRemovedEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val entityID: QuotaEntityID) : QuotasEvent()
data class QuotasAlteredEvent(override val clusterIdentifier: KafkaClusterIdentifier, override val entityID: QuotaEntityID) : QuotasEvent()


