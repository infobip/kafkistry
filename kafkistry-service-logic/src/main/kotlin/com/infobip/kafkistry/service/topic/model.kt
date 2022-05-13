package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.OptionalValue
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.AssignmentsDisbalance
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.service.resources.TopicResourceRequiredUsages
import com.infobip.kafkistry.service.topic.IssueCategory.*
import com.infobip.kafkistry.service.topic.validation.rules.Placeholder
import com.infobip.kafkistry.service.topic.validation.rules.RuleViolation
import java.io.Serializable

data class TopicStatuses(
    val topicName: TopicName,
    val topicDescription: TopicDescription?,
    val aggStatusFlags: StatusFlags,
    val statusPerClusters: List<TopicClusterStatus>,
    val topicsStatusCounts: Map<InspectionResultType, Int>?,
    val availableActions: List<AvailableAction>,
)

data class ExistingTopicInfo(
    val name: TopicName,
    val properties: TopicProperties,
    val config: ExistingConfig,
    val partitionsAssignments: List<PartitionAssignments>,
    val assignmentsDisbalance: AssignmentsDisbalance
)

data class ReBalanceSuggestion(
    val existingTopicInfo: ExistingTopicInfo,
    val assignmentsChange: AssignmentsChange,
    val oldDisbalance: AssignmentsDisbalance,
    val newDisbalance: AssignmentsDisbalance,
    val dataMigration: DataMigration
)

data class ExpectedTopicInfo(
    val name: TopicName,
    val properties: TopicProperties,
    val config: TopicConfigMap,
    val resourceRequirements: ResourceRequirements?
)

data class TopicClusterStatus(
    val status: TopicOnClusterInspectionResult,
    val lastRefreshTime: Long,
    val clusterIdentifier: KafkaClusterIdentifier,
    val clusterTags: List<Tag>,
    val existingTopicInfo: ExistingTopicInfo?,
    val configEntryStatuses: Map<String, ValueInspection>?,
    val resourceRequiredUsages: OptionalValue<TopicResourceRequiredUsages>,
    val currentTopicReplicaInfos: TopicReplicaInfos?,
    val currentReAssignments: Map<Partition, TopicPartitionReAssignment>,
) {
    companion object {
        fun unavailable() = TopicClusterStatus(
            status = TopicOnClusterInspectionResult.Builder()
                .addResultType(InspectionResultType.UNAVAILABLE)
                .build(),
            lastRefreshTime = System.currentTimeMillis() / 1000,
            configEntryStatuses = null,
            existingTopicInfo = null,
            clusterIdentifier = "",
            clusterTags = emptyList(),
            resourceRequiredUsages = OptionalValue.absent("unavailable"),
            currentTopicReplicaInfos = null,
            currentReAssignments = emptyMap(),
        )
    }
}

data class ValueInspection(
    val valid: Boolean,
    val currentValue: String?,
    val expectedValue: String?,
    val expectingClusterDefault: Boolean
)

enum class AvailableAction {
    CREATE_TOPIC,
    SUGGESTED_EDIT,
    FIX_VIOLATIONS_EDIT,
    MANUAL_EDIT,
    DELETE_TOPIC_ON_KAFKA,
    IMPORT_TOPIC,
    ALTER_TOPIC_CONFIG,
    ALTER_PARTITION_COUNT,
    ALTER_REPLICATION_FACTOR,
    RE_BALANCE_ASSIGNMENTS,
    INSPECT_TOPIC
}

data class ClusterTopicsStatuses(
    val lastRefreshTime: Long,
    val cluster: KafkaCluster,
    val clusterInfo: ClusterInfo?,
    val clusterState: StateType,
    val aggStatusFlags: StatusFlags,
    val statusPerTopics: List<ClusterTopicStatus>?,
    val topicsStatusCounts: Map<InspectionResultType, Int>?
)

data class ClusterTopicStatus(
    val topicName: TopicName,
    val status: TopicOnClusterInspectionResult,
    val existingTopicInfo: ExistingTopicInfo?
)

data class TopicOnClusterInspectionResult(
    val types: List<InspectionResultType>,
    val flags: StatusFlags,
    val exists: Boolean? = null,
    val wrongValues: List<WrongValueAssertion>? = null,
    val ruleViolations: List<RuleViolationIssue>? = null,
    val currentConfigRuleViolations: List<RuleViolationIssue>? = null,
    val availableActions: List<AvailableAction>,
    val affectingAclRules: List<KafkaAclRule>
) {
    data class Builder(
        private var types: MutableList<InspectionResultType> = mutableListOf(),
        private var exists: Boolean? = null,
        private var wrongValues: MutableList<WrongValueAssertion>? = null,
        private var ruleViolations: MutableList<RuleViolationIssue>? = null,
        private var currentConfigRuleViolations: MutableList<RuleViolationIssue>? = null,
        private var availableActions: List<AvailableAction> = emptyList(),
        private var affectingAclRules: List<KafkaAclRule> = emptyList()
    ) {
        fun types() = types.toList()

        fun exists(exists: Boolean): Builder = this.also { this.exists = exists }

        fun addResultType(resultType: InspectionResultType): Builder = this.also {
            types.add(resultType)
        }

        fun addWrongValue(wrongValue: WrongValueAssertion): Builder = addWrongValues(listOf(wrongValue))

        fun addWrongValues(wrongValues: List<WrongValueAssertion>): Builder = this.also {
            this.wrongValues = (this.wrongValues ?: mutableListOf()).also {
                it.addAll(wrongValues)
            }
        }

        fun addRuleViolations(ruleViolations: List<RuleViolationIssue>): Builder = this.also {
            this.ruleViolations = (this.ruleViolations ?: mutableListOf()).also {
                it.addAll(ruleViolations)
            }
        }

        fun addCurrentConfigRuleViolations(ruleViolations: List<RuleViolationIssue>): Builder = this.also {
            this.currentConfigRuleViolations = (this.currentConfigRuleViolations ?: mutableListOf()).also {
                it.addAll(ruleViolations)
            }
        }

        fun availableActions(availableActions: List<AvailableAction>): Builder = this.also {
            this.availableActions = availableActions
        }

        fun affectingAclRules(aclRules: List<KafkaAclRule>): Builder = this.also {
            affectingAclRules = aclRules
        }

        fun build() = TopicOnClusterInspectionResult(
            types = types.toList(),
            exists = exists,
            flags = StatusFlags(
                allOk = types.all { it.category.ok },
                visibleOk = types.none { it.category == VISIBILITY },
                configOk = types.none { it.category == CONFIGURATION_MISMATCH },
                ruleCheckOk = types.none { it.category == RULE_CHECK_VIOLATION },
                runtimeOk = types.none { it.category == RUNTIME_ISSUE },
                disabled = types.any { it == InspectionResultType.CLUSTER_DISABLED }
            ),
            wrongValues = wrongValues?.toList(),
            ruleViolations = ruleViolations?.toList(),
            currentConfigRuleViolations = currentConfigRuleViolations?.toList(),
            availableActions = availableActions,
            affectingAclRules = affectingAclRules
        )
    }
}

data class StatusFlags(
    val allOk: Boolean,
    val visibleOk: Boolean,
    val configOk: Boolean,
    val ruleCheckOk: Boolean,
    val runtimeOk: Boolean,
    val disabled: Boolean
) {
    companion object {
        val ALL_OK = StatusFlags(
            allOk = true,
            visibleOk = true,
            configOk = true,
            ruleCheckOk = true,
            runtimeOk = true,
            disabled = false
        )
        val NON_VISIBLE = StatusFlags(
            allOk = false,
            visibleOk = false,
            configOk = false,
            ruleCheckOk = false,
            runtimeOk = false,
            disabled = false
        )
        val DISABLED = StatusFlags(
            allOk = true,
            visibleOk = false,
            configOk = false,
            ruleCheckOk = false,
            runtimeOk = true,
            disabled = true
        )
    }

    infix fun merge(other: StatusFlags) = StatusFlags(
        allOk = allOk && other.allOk,
        visibleOk = visibleOk && other.visibleOk,
        configOk = configOk && other.configOk,
        ruleCheckOk = ruleCheckOk && other.ruleCheckOk,
        runtimeOk = runtimeOk && other.runtimeOk,
        disabled = disabled || other.disabled
    )
}

fun Iterable<StatusFlags>.aggregate(): StatusFlags = fold(StatusFlags.ALL_OK, StatusFlags::merge)

data class WrongValueAssertion(
    val type: InspectionResultType,
    val key: String,
    val expectedDefault: Boolean,
    val expected: String?,
    val actual: String?,
    val message: String? = null
) {
    constructor(
        type: InspectionResultType,
        key: String,
        expectedDefault: Boolean,
        expected: Any?,
        actual: Any?,
        message: String? = null
    ) : this(type, key, expectedDefault, expected?.toString(), actual?.toString(), message)
}

data class RuleViolationIssue(
    val type: InspectionResultType,
    val ruleClassName: String,
    val severity: RuleViolation.Severity,
    val message: String,
    val placeholders: Map<String, Placeholder> = emptyMap()
)

enum class IssueCategory(
    val ok: Boolean
) {
    NONE(true),
    VISIBILITY(false),
    CONFIGURATION_MISMATCH(false),
    RULE_CHECK_VIOLATION(false),
    RUNTIME_ISSUE(false),
    INVALID_REQUEST(false)
}

enum class InspectionResultType(
    val category: IssueCategory
) {
    /**
     * Everything is ok with specific topic on specific cluster
     */
    OK(NONE),

    /**
     * Internal topic of kafka
     */
    INTERNAL(NONE),

    /**
     * Topic does not exist on cluster, and it is expected because it's configured not to be present on specific cluster
     */
    NOT_PRESENT_AS_EXPECTED(NONE),

    /**
     * Currently cluster is unreachable, can't conclude what is an actual status of topic on this cluster
     */
    CLUSTER_UNREACHABLE(VISIBILITY),

    /**
     * Cluster is disabled by configuration, can't conclude what is an actual status of topic on this cluster
     */
    CLUSTER_DISABLED(NONE),

    /**
     * Topic is configured to exist on cluster, but it does not
     */
    MISSING(CONFIGURATION_MISMATCH),

    /**
     * Topic is configured not to be present on cluster but actually it does exist
     */
    UNEXPECTED(CONFIGURATION_MISMATCH),

    /**
     * Topic exist on cluster, but it is not present in registry's repository of topic configurations
     */
    UNKNOWN(CONFIGURATION_MISMATCH),

    /**
     * Actual partition count on cluster differs from configured partition count for this cluster
     */
    WRONG_PARTITION_COUNT(CONFIGURATION_MISMATCH),

    /**
     * Actual number of partition replicas differs from configured replication factor for this cluster
     */
    WRONG_REPLICATION_FACTOR(CONFIGURATION_MISMATCH),

    /**
     * There are non-default configuration values on actual cluster topic which differ from configured
     * non-default config values for this specific cluster
     */
    WRONG_CONFIG(CONFIGURATION_MISMATCH),

    /**
     * Wanted configuration for cluster violate some validation rules
     * @see com.infobip.kafkistry.service.topic.validation.rules.RuleViolation
     */
    CONFIG_RULE_VIOLATIONS(RULE_CHECK_VIOLATION),

    /**
     * Actual topic's on cluster configuration for cluster violate some validation rules
     * @see com.infobip.kafkistry.service.topic.validation.rules.RuleViolation
     */
    CURRENT_CONFIG_RULE_VIOLATIONS(RULE_CHECK_VIOLATION),

    /**
     * Actual topic on cluster has un-even distribution of replicas per brokers in cluster
     */
    PARTITION_REPLICAS_DISBALANCE(RUNTIME_ISSUE),

    /**
     * Actual topic on cluster has un-even distribution of leader replicas per brokers in cluster
     */
    PARTITION_LEADERS_DISBALANCE(RUNTIME_ISSUE),

    /**
     * It means that it is that there has been re-assignment that is completed and not yet
     * verified manually by executing verify action
     */
    HAS_UNVERIFIED_REASSIGNMENTS(RUNTIME_ISSUE),

    /**
     * Topic is configured to throttle leader-follower replication rate for some partition-broker pairs
     */
    HAS_REPLICATION_THROTTLING(RUNTIME_ISSUE),

    /**
     * Topic currently has partitions with replica re-assignments in progress
     */
    RE_ASSIGNMENT_IN_PROGRESS(RUNTIME_ISSUE),

    /**
     * Topic has some preferred leaders which are not leaders and has some leaders which are not preferred leaders
     */
    NEEDS_LEADER_ELECTION(RUNTIME_ISSUE),

    /**
     * Some partition replicas are out of sync with leader. It can be caused by runtime issues like network issues,
     * increased traffic, cluster node issues, etc. and it is expected when there is re-assignment in progress which
     * involves migration of some replicas
     */
    HAS_OUT_OF_SYNC_REPLICAS(RUNTIME_ISSUE),

    /**
     * Requested topic inspection does not exist in registry at all, nor it does not exist on cluster as unknown
     */
    UNAVAILABLE(INVALID_REQUEST)

}

data class ConfigValueChange(
    val key: String,
    val oldValue: String?,
    val newValue: String?,
    val newToDefault: Boolean
)

data class PartitionsAssignmentsStatus(
    val partitions: List<PartitionAssignmentsStatus>,
    val newReplicasCount: Int,
    val movedReplicasCount: Int,
    val reElectedLeadersCount: Int
)

data class PartitionAssignmentsStatus(
    val partition: Partition,
    val brokerReplicas: List<BrokerReplicaAssignmentStatus>,
    val newReplicasCount: Int,
    val movedReplicasCount: Int,
    val reElectedLeadersCount: Int
)

data class BrokerReplicaAssignmentStatus(
    val brokerId: BrokerId,
    val currentStatus: ReplicaAssignment?,
    val added: Boolean,
    val removed: Boolean,
    val newLeader: Boolean,
    val exLeader: Boolean,
    val rank: Int
)

data class PartitionPropertiesChanges(
    val existingTopicInfo: ExistingTopicInfo,
    val partitionCountChange: PartitionPropertyChange,
    val replicationFactorChange: PartitionPropertyChange
)

data class PartitionPropertyChange(
    val type: PropertiesChangeType,
    val currentAssignments: List<PartitionAssignments>,
    val impossibleReason: String? = null,
    val change: AssignmentsChange? = null,
    val dataMigration: DataMigration? = null
) {
    companion object {

        fun noNeed(
            currentAssignments: List<PartitionAssignments>
        ) = PartitionPropertyChange(PropertiesChangeType.NOTHING, currentAssignments)

        fun impossible(
            reason: String, currentAssignments: List<PartitionAssignments>
        ) = PartitionPropertyChange(PropertiesChangeType.IMPOSSIBLE, currentAssignments, impossibleReason = reason)

        fun change(
            change: AssignmentsChange,
            currentAssignments: List<PartitionAssignments>,
            dataMigration: DataMigration
        ) = PartitionPropertyChange(
            PropertiesChangeType.CHANGE,
            currentAssignments,
            change = change,
            dataMigration = dataMigration
        )

    }
}

data class DataMigration(
    val reAssignedPartitions: Int,
    val totalIOBytes: Long,
    val totalAddBytes: Long,
    val totalReleaseBytes: Long,
    val perBrokerTotalIOBytes: Map<BrokerId, Long>,
    val perBrokerInputBytes: Map<BrokerId, Long>,
    val perBrokerOutputBytes: Map<BrokerId, Long>,
    val perBrokerReleasedBytes: Map<BrokerId, Long>,
    val maxBrokerIOBytes: Long,
) : Serializable

enum class PropertiesChangeType {
    /**
     * Nothing needs to be altered, actual properties already match "wanted" (partition count, replication factor)
     */
    NOTHING,

    /**
     * Topic cannot be altered to match wanted properties. For example, partition count cannot be reduced.
     */
    IMPOSSIBLE,

    CHANGE
}

enum class ReBalanceMode {
    REPLICAS,
    LEADERS,
    REPLICAS_THEN_LEADERS,
    LEADERS_THEN_REPLICAS,
    ROUND_ROBIN,
}