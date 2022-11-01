package com.infobip.kafkistry.service.history

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.*

interface PendingRequest {
    val branch: Branch
    val commitChanges: List<CommitChange>
    val type: ChangeType
    val errorMsg: String?
}

data class TopicRequest(
    override val branch: Branch,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    val topicName: TopicName,
    override val errorMsg: String?,
    val topic: TopicDescription?
) : PendingRequest

data class ClusterRequest(
    override val branch: Branch,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    val identifier: KafkaClusterIdentifier,
    override val errorMsg: String?,
    val cluster: KafkaCluster?
) : PendingRequest

data class AclsRequest(
    override val branch: Branch,
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

data class BranchRequests<PR : PendingRequest>(
    val branch: Branch,
    val requests: List<PR>,
    val commits: List<CommitChanges>,
)