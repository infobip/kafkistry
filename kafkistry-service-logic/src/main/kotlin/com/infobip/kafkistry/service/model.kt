package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.CommitChange
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.storage.Commit

data class OptionalValue<V>(
    val value: V?,
    val absentReason: String?
) {
    companion object {
        fun <V> of(value: V): OptionalValue<V> = OptionalValue(value, null)
        fun <V> absent(reason: String): OptionalValue<V> = OptionalValue(null, reason)
    }
}

data class ExistingValues(
    val kafkaProfiles: List<KafkaProfile>,
    val clusterIdentifiers: List<KafkaClusterIdentifier>,
    val clusterRefs: List<ClusterRef>,
    val tagClusters: Map<Tag, List<KafkaClusterIdentifier>>,
    val commonTopicConfig: ExistingConfig,
    val topicConfigDoc: Map<String, String>,
    val brokerConfigDoc: Map<String, String>,
    val owners: List<String>,
    val producers: List<String>,
    val topics: List<TopicName>,
    val consumerGroups: List<ConsumerGroupId>,
    val users: List<KafkaUser>,
)

interface PendingRequest {
    val branch: String
    val commitChanges: List<CommitChange>
    val type: ChangeType
    val errorMsg: String?
}

data class TopicRequest(
    override val branch: String,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    val topicName: TopicName,
    override val errorMsg: String?,
    val topic: TopicDescription?
) : PendingRequest

data class ClusterRequest(
    override val branch: String,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    val identifier: KafkaClusterIdentifier,
    override val errorMsg: String?,
    val cluster: KafkaCluster?
) : PendingRequest

data class AclsRequest(
    override val branch: String,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    override val errorMsg: String?,
    val principal: PrincipalId,
    val principalAcls: PrincipalAclRules?
) : PendingRequest

data class ChangeCommit<C : Change>(
        val commit: Commit,
        val changes: List<C>
)

interface Change {
    val changeType: ChangeType
    val oldContent: String?
    val newContent: String?
    val errorMsg: String?
}

data class TopicChange(
    override val changeType: ChangeType,
    override val oldContent: String?,
    override val newContent: String?,
    override val errorMsg: String?,
    val topicName: TopicName,
    val topic: TopicDescription?
) : Change

data class ClusterChange(
    override val changeType: ChangeType,
    override val oldContent: String?,
    override val newContent: String?,
    override val errorMsg: String?,
    val identifier: KafkaClusterIdentifier,
    val cluster: KafkaCluster?
) : Change

data class AclsChange(
    override val changeType: ChangeType,
    override val oldContent: String?,
    override val newContent: String?,
    override val errorMsg: String?,
    val principal: PrincipalId,
    val principalAcls: PrincipalAclRules?
) : Change

