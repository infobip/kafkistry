package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.CommitChange
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.storage.Commit
import com.infobip.kafkistry.service.topic.DataMigration
import com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus
import com.infobip.kafkistry.service.topic.ReBalanceMode
import com.infobip.kafkistry.service.topic.ReBalanceSuggestion
import java.io.Serializable

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

enum class AclInspectionResultType(
        val valid: Boolean
) {
    OK(true),
    MISSING(false),
    UNEXPECTED(false),
    NOT_PRESENT_AS_EXPECTED(true),
    UNKNOWN(false),
    CLUSTER_DISABLED(true),
    CLUSTER_UNREACHABLE(false),
    SECURITY_DISABLED(false),
    UNAVAILABLE(false)
}

data class AclStatus(
        val ok: Boolean,
        val statusCounts: Map<AclInspectionResultType, Int>
) {
    companion object {
        val EMPTY_OK = AclStatus(true, emptyMap())

        fun from(statuses: List<AclRuleStatus>) = AclStatus(
                ok = statuses.map { it.statusType.valid }.fold(true, Boolean::and),
                statusCounts = statuses.groupingBy { it.statusType }.eachCount()
        )
    }

    infix fun merge(other: AclStatus) = AclStatus(
            ok = ok && other.ok,
            statusCounts = (statusCounts.asSequence() + other.statusCounts.asSequence())
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, counts) -> counts.sum() }
    )
}

fun Iterable<AclStatus>.aggregate(): AclStatus = fold(AclStatus.EMPTY_OK, AclStatus::merge)

data class AclRuleStatus(
    val statusType: AclInspectionResultType,
    val rule: KafkaAclRule,
    val affectedTopics: List<TopicName>,
    val affectedConsumerGroups: List<ConsumerGroupId>,
    val availableOperations: List<AvailableAclOperation>
)

data class PrincipalAclsClusterInspection(
    val principal: PrincipalId,
    val clusterIdentifier: KafkaClusterIdentifier,
    val statuses: List<AclRuleStatus>,
    val status: AclStatus,
    val availableOperations: List<AvailableAclOperation>,
    val affectingQuotaEntities: List<QuotaEntity>,
)

data class PrincipalAclsInspection(
    val principal: PrincipalId,
    val principalAcls: PrincipalAclRules?,
    val clusterInspections: List<PrincipalAclsClusterInspection>,
    val status: AclStatus,
    val availableOperations: List<AvailableAclOperation>,
    val affectingQuotaEntities: Map<QuotaEntity, Presence>,
)

data class ClusterAclsInspection(
    val clusterIdentifier: KafkaClusterIdentifier,
    val principalAclsInspections: List<PrincipalAclsClusterInspection>,
    val status: AclStatus
)

data class AclRuleClustersInspection(
    val aclRule: KafkaAclRule,
    val clusterStatuses: Map<KafkaClusterIdentifier, AclRuleStatus>,
    val status: AclStatus,
    val availableOperations: List<AvailableAclOperation>
)

data class PrincipalAclsClustersPerRuleInspection(
    val principal: PrincipalId,
    val principalAcls: PrincipalAclRules?,
    val statuses: List<AclRuleClustersInspection>,
    val status: AclStatus,
    val availableOperations: List<AvailableAclOperation>,
    val clusterAffectingQuotaEntities: Map<KafkaClusterIdentifier, List<QuotaEntity>>,
    val affectingQuotaEntities: Map<QuotaEntity, Presence>,
)

enum class AvailableAclOperation {
    CREATE_MISSING_ACLS,
    DELETE_UNWANTED_ACLS,
    EDIT_PRINCIPAL_ACLS,
    IMPORT_PRINCIPAL
}

data class ThrottleBrokerTopicPartitions(
    val clusterIdentifier: KafkaClusterIdentifier,
    val brokerIds: List<BrokerId>,
    val topicNames: List<TopicName>
): Serializable

data class ThrottleBrokerTopicPartitionsSuggestion(
    val throttleRequest: ThrottleBrokerTopicPartitions,
    val topicThrottleConfigs: Map<TopicName, TopicConfigMap>,
    val maximumDataMigrations: Map<TopicName, DataMigration>,
    val totalMaximumDataMigration: DataMigration,
): Serializable

data class BulkReAssignmentOptions(
    val reBalanceMode: ReBalanceMode,
    val includeTopicNamePattern: String?,
    val excludeTopicNamePattern: String?,
    val topicSelectOrder: TopicSelectOrder,
    val topicBy: TopicBy,
    val topicCountLimit: Int,
    val topicPartitionCountLimit: Int,
    val totalMigrationBytesLimit: Long,
) {
    enum class TopicBy {
        MIGRATION_BYTES,
        RE_ASSIGNED_PARTITIONS_COUNT
    }

    enum class TopicSelectOrder {
        TOP, BOTTOM
    }
}

data class BulkReAssignmentSuggestion(
    val clusterInfo: ClusterInfo,
    val topicsReBalanceSuggestions: Map<TopicName, ReBalanceSuggestion>,
    val topicsReBalanceStatuses: Map<TopicName, PartitionsAssignmentsStatus>,
    val totalDataMigration: DataMigration,
    val selectionLimitedBy: List<SelectionLimitedCause>,
) {

    enum class SelectionLimitedCause {
        TOPIC_COUNT,
        PARTITION_COUNT,
        MIGRATION_BYTES,
        INCLUSION_FILTERED,
        EXCLUSION_FILTERED,
    }
}
