package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.kafkastate.TopicReplicaInfos
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.StatusLevel.*
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.AssignmentsDisbalance
import com.infobip.kafkistry.service.resources.TopicResourceRequiredUsages
import com.infobip.kafkistry.service.topic.IssueCategory.*
import java.io.Serializable

data class TopicStatuses(
    val topicName: TopicName,
    val topicDescription: TopicDescription?,
    val aggStatusFlags: StatusFlags,
    val statusPerClusters: List<TopicClusterStatus>,
    val topicsStatusCounts: List<NamedTypeQuantity<TopicInspectionResultType, Int>>?,
    val availableActions: List<AvailableAction>,
)

data class ExistingTopicInfo(
    val uuid: TopicUUID?,
    val name: TopicName,
    val properties: TopicProperties,
    val config: ExistingConfig,
    val partitionsAssignments: List<PartitionAssignments>,
    val assignmentsDisbalance: AssignmentsDisbalance,
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
    val topicDescription: TopicDescription?,
    val lastRefreshTime: Long,
    val clusterIdentifier: KafkaClusterIdentifier,
    val clusterTags: List<Tag>,
    val existingTopicInfo: ExistingTopicInfo?,
    val configEntryStatuses: Map<String, ValueInspection>?,
    val resourceRequiredUsages: OptionalValue<TopicResourceRequiredUsages>,
    val currentTopicReplicaInfos: TopicReplicaInfos?,
    val currentReAssignments: Map<Partition, TopicPartitionReAssignment>,
    val externInspectInfo: Map<String, Any>,
) {
    companion object {
        fun unavailable() = TopicClusterStatus(
            status = TopicOnClusterInspectionResult.Builder()
                .addResultType(TopicInspectionResultType.UNAVAILABLE)
                .build(),
            topicDescription = null,
            lastRefreshTime = System.currentTimeMillis() / 1000,
            configEntryStatuses = null,
            existingTopicInfo = null,
            clusterIdentifier = "",
            clusterTags = emptyList(),
            resourceRequiredUsages = OptionalValue.absent("unavailable"),
            currentTopicReplicaInfos = null,
            currentReAssignments = emptyMap(),
            externInspectInfo = emptyMap(),
        )
    }
}

fun TopicClusterStatus.clusterRef() = ClusterRef(clusterIdentifier, clusterTags)

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
    val topicsStatusCounts: List<NamedTypeQuantity<TopicInspectionResultType, Int>>?,
)

data class ClusterTopicStatus(
    val topicName: TopicName,
    val status: TopicOnClusterInspectionResult,
    val existingTopicInfo: ExistingTopicInfo?
)

data class TopicOnClusterInspectionResult(
    val types: List<TopicInspectionResultType>,
    val flags: StatusFlags,
    val exists: Boolean? = null,
    val wrongValues: List<WrongValueAssertion>? = null,
    val updateValues: List<WrongValueAssertion>? = null,
    val ruleViolations: List<RuleViolationIssue>? = null,
    val currentConfigRuleViolations: List<RuleViolationIssue>? = null,
    val typeDescriptions: List<NamedTypeCauseDescription<TopicInspectionResultType>>,
    val availableActions: List<AvailableAction>,
    val affectingAclRules: List<KafkaAclRule>,
) {
    data class Builder(
        private var types: MutableList<TopicInspectionResultType> = mutableListOf(),
        private var exists: Boolean? = null,
        private var wrongValues: MutableList<WrongValueAssertion>? = null,
        private var updateValues: MutableList<WrongValueAssertion>? = null,
        private var ruleViolations: MutableList<RuleViolationIssue>? = null,
        private var currentConfigRuleViolations: MutableList<RuleViolationIssue>? = null,
        private var typeDescriptions: MutableList<NamedTypeCauseDescription<TopicInspectionResultType>>? = null,
        private var availableActions: List<AvailableAction> = emptyList(),
        private var affectingAclRules: List<KafkaAclRule> = emptyList()
    ) {
        fun types() = types.toList()

        fun exists(exists: Boolean): Builder = this.also { this.exists = exists }

        fun addOkResultType(): Builder = this.also { types.add(0, TopicInspectionResultType.OK) }

        fun addResultType(resultType: TopicInspectionResultType): Builder = this.also {
            types.add(resultType)
        }

        fun addWrongValue(wrongValue: WrongValueAssertion): Builder = addWrongValues(listOf(wrongValue))

        fun addWrongValues(wrongValues: List<WrongValueAssertion>): Builder = this.also {
            this.wrongValues = (this.wrongValues ?: mutableListOf()).also {
                it.addAll(wrongValues)
            }
        }

        fun addUpdateValue(updateValue: WrongValueAssertion): Builder = addUpdateValues(listOf(updateValue))

        fun addUpdateValues(updateValues: List<WrongValueAssertion>): Builder = this.also {
            this.updateValues = (this.updateValues ?: mutableListOf()).also {
                it.addAll(updateValues)
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

        fun addTypeDescription(typeDescription: NamedTypeCauseDescription<TopicInspectionResultType>): Builder = this.also {
            this.typeDescriptions = (this.typeDescriptions ?: mutableListOf()).also {
                it.add(typeDescription)
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
                disabled = types.any { it == TopicInspectionResultType.CLUSTER_DISABLED }
            ),
            wrongValues = wrongValues?.toList(),
            updateValues = updateValues?.toList(),
            ruleViolations = ruleViolations?.toList(),
            currentConfigRuleViolations = currentConfigRuleViolations?.toList(),
            typeDescriptions = typeDescriptions?.toList().orEmpty(),
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
    val type: TopicInspectionResultType,
    val key: String,
    val expectedDefault: Boolean,
    val expected: String?,
    val actual: String?,
    val message: String? = null
) {
    constructor(
        type: TopicInspectionResultType,
        key: String,
        expectedDefault: Boolean,
        expected: Any?,
        actual: Any?,
        message: String? = null
    ) : this(type, key, expectedDefault, expected?.toString(), actual?.toString(), message)
}

data class RuleViolationIssue(
    val type: TopicInspectionResultType,
    val violation: RuleViolation,
)

enum class IssueCategory(
    val ok: Boolean
) {
    NONE(true),
    VISIBILITY(false),
    CONFIGURATION_MISMATCH(false),
    CONFIGURATION_UPDATE(true),
    RULE_CHECK_VIOLATION(false),
    RUNTIME_ISSUE(false),
    INVALID_REQUEST(false)
}


data class TopicInspectionResultType(
    override val name: String,
    override val level: StatusLevel,
    val category: IssueCategory,
    override val doc: String,
) : NamedType {

    override val valid: Boolean get() = category.ok

    companion object {
        /**
         * Everything is ok with specific topic on specific cluster
         */
        val OK =  TopicInspectionResultType(
            "OK", SUCCESS, NONE, NamedTypeDoc.OK,
        )

        /**
         * Internal topic of kafka
         */
        val INTERNAL =  TopicInspectionResultType(
            "INTERNAL", INFO, NONE, "Internal topic of kafka itself used for persistent storage of state",
        )

        /**
         * Topic does not exist on cluster, and it is expected because it's configured not to be present on specific cluster
         */
        val NOT_PRESENT_AS_EXPECTED =  TopicInspectionResultType(
            "NOT_PRESENT_AS_EXPECTED", IGNORE, NONE, NamedTypeDoc.NOT_PRESENT_AS_EXPECTED,
        )

        /**
         * Currently cluster is unreachable, can't conclude what is an actual status of topic on this cluster
         */
        val CLUSTER_UNREACHABLE =  TopicInspectionResultType(
            "CLUSTER_UNREACHABLE", CRITICAL, VISIBILITY, NamedTypeDoc.CLUSTER_UNREACHABLE,
        )

        /**
         * Cluster is disabled by configuration, can't conclude what is an actual status of topic on this cluster
         */
        val CLUSTER_DISABLED =  TopicInspectionResultType(
            "CLUSTER_DISABLED", IGNORE, NONE, NamedTypeDoc.CLUSTER_DISABLED,
        )

        /**
         * Topic is configured to exist on cluster, but it does not
         */
        val MISSING =  TopicInspectionResultType(
            "MISSING", ERROR, CONFIGURATION_MISMATCH, NamedTypeDoc.MISSING,
        )

        /**
         * Topic needs to get created on cluster
         */
        val TO_CREATE =  TopicInspectionResultType(
            "TO_CREATE", INFO, CONFIGURATION_UPDATE, NamedTypeDoc.TO_CREATE,
        )

        /**
         * Topic needs to get deleted from cluster
         */
        val TO_DELETE =  TopicInspectionResultType(
            "TO_DELETE", INFO, CONFIGURATION_UPDATE, NamedTypeDoc.TO_DELETE,
        )

        /**
         * Topic is configured not to be present on cluster but actually it does exist
         */
        val UNEXPECTED =  TopicInspectionResultType(
            "UNEXPECTED", ERROR, CONFIGURATION_MISMATCH, NamedTypeDoc.UNEXPECTED,
        )

        /**
         * Topic exist on cluster, but it is not present in registry's repository of topic configurations
         */
        val UNKNOWN =  TopicInspectionResultType(
            "UNKNOWN", WARNING, CONFIGURATION_MISMATCH, NamedTypeDoc.UNKNOWN,
        )

        /**
         * Actual partition count on cluster differs from configured partition count for this cluster
         */
        val WRONG_PARTITION_COUNT =  TopicInspectionResultType(
            "WRONG_PARTITION_COUNT", ERROR, CONFIGURATION_MISMATCH,
            "Configured partition count differs from actual partition count on existing topic",
        )

        /**
         * Actual number of partition replicas differs from configured replication factor for this cluster
         */
        val WRONG_REPLICATION_FACTOR =  TopicInspectionResultType(
            "WRONG_REPLICATION_FACTOR", ERROR, CONFIGURATION_MISMATCH,
            "Configured replication factor differs from actual partition count on existing topic",
        )

        /**
         * Actual partition count on cluster differs from configured partition count for this cluster and needs update
         */
        val CHANGE_PARTITION_COUNT =  TopicInspectionResultType(
            "CHANGE_PARTITION_COUNT", INFO, CONFIGURATION_UPDATE,
            "Partition count needs an update comparing to actual partition count on existing topic",
        )

        /**
         * Actual number of partition replicas differs from configured replication factor for this cluster and needs update
         */
        val CHANGE_REPLICATION_FACTOR =  TopicInspectionResultType(
            "CHANGE_REPLICATION_FACTOR", INFO, CONFIGURATION_UPDATE,
            "Replication factor needs an update comparing to  actual partition count on existing topic",
        )

        /**
         * There are non-default configuration values on actual cluster topic which differ from configured
         * non-default config values for this specific cluster
         */
        val WRONG_CONFIG =  TopicInspectionResultType(
            "WRONG_CONFIG", ERROR, CONFIGURATION_MISMATCH,
            "Some configuration value(s) do not match expected value(s)",
        )

        /**
         * There are non-default configuration values on actual cluster topic which differ from configured
         * non-default config values for this specific cluster and such config properties need an update/alter
         */
        val UPDATE_CONFIG =  TopicInspectionResultType(
            "UPDATE_CONFIG", INFO, CONFIGURATION_UPDATE,
            "Some configuration value(s) need to alter to match expected value(s)",
        )

        /**
         * Wanted configuration for cluster violate some validation rules
         * @see com.infobip.kafkistry.service.RuleViolation
         */
        val CONFIG_RULE_VIOLATIONS =  TopicInspectionResultType(
            "CONFIG_RULE_VIOLATIONS", WARNING, RULE_CHECK_VIOLATION,
            "Kafkistry has some set of rules for checking bad configuration combinations. " +
                    "This warning shows that topic configuration in registry violates some of thore rules",
        )

        /**
         * Actual topic's on cluster configuration for cluster violate some validation rules
         * @see com.infobip.kafkistry.service.RuleViolation
         */
        val CURRENT_CONFIG_RULE_VIOLATIONS =  TopicInspectionResultType(
            "CURRENT_CONFIG_RULE_VIOLATIONS", WARNING, RULE_CHECK_VIOLATION,
            "Kafkistry has some set of rules for checking bad configuration combinations. " +
                    "This warning shows that 'current' topic on cluster violates some of thore rules",
        )

        /**
         * Actual topic on cluster has un-even distribution of replicas per brokers in cluster
         */
        val PARTITION_REPLICAS_DISBALANCE =  TopicInspectionResultType(
            "PARTITION_REPLICAS_DISBALANCE", WARNING, RUNTIME_ISSUE,
            "Actual topic assignment has un-even number of partition replicas across broker nodes in cluster",
        )

        /**
         * Actual topic on cluster has un-even distribution of leader replicas per brokers in cluster
         */
        val PARTITION_LEADERS_DISBALANCE =  TopicInspectionResultType(
            "PARTITION_LEADERS_DISBALANCE", WARNING, RUNTIME_ISSUE,
            "Actual topic assignment has un-even number of partition leader replicas across broker nodes in cluster",
        )

        /**
         * It means that it is that there has been re-assignment that is completed and not yet
         * verified manually by executing verify action
         */
        val HAS_UNVERIFIED_REASSIGNMENTS =  TopicInspectionResultType(
            "HAS_UNVERIFIED_REASSIGNMENTS", WARNING, RUNTIME_ISSUE,
            "It means that there has been re-assignment which is completed and not yet verified manually by executing verify action which clears throttle",
        )

        /**
         * Topic is configured to throttle leader-follower replication rate for some partition-broker pairs
         */
        val HAS_REPLICATION_THROTTLING =  TopicInspectionResultType(
            "HAS_REPLICATION_THROTTLING", WARNING, RUNTIME_ISSUE,
            "Topic is configured to throttle leader-follower replication rate for some partition-broker pairs",
        )

        /**
         * Topic currently has partitions with replica re-assignments in progress
         */
        val RE_ASSIGNMENT_IN_PROGRESS =  TopicInspectionResultType(
            "RE_ASSIGNMENT_IN_PROGRESS", INFO, RUNTIME_ISSUE,
            "Topic currently has partitions with replica re-assignments in progress",
        )

        /**
         * Topic has some preferred leaders which are not leaders and has some leaders which are not preferred leaders
         */
        val NEEDS_LEADER_ELECTION =  TopicInspectionResultType(
            "NEEDS_LEADER_ELECTION", WARNING, RUNTIME_ISSUE,
            "Topic has some preferred leaders which are not leaders and has some leaders which are not preferred leaders",
        )

        /**
         * Some partition replicas are out of sync with leader. It can be caused by runtime issues like network issues,
         * increased traffic, cluster node issues, etc. and it is expected when there is re-assignment in progress which
         * involves migration of some replicas
         */
        val HAS_OUT_OF_SYNC_REPLICAS =  TopicInspectionResultType(
            "HAS_OUT_OF_SYNC_REPLICAS", WARNING, RUNTIME_ISSUE,
            "Some partition replicas are out of sync with leader. " +
                    "It can be caused by runtime issues like network issues, increased traffic, cluster node issues, etc... " +
                    "and it is expected when there is re-assignment in progress which involves migration of some replicas",
        )

        /**
         * Requested topic inspection does not exist in registry at all, nor it does not exist on cluster as unknown
         */
        val UNAVAILABLE =  TopicInspectionResultType(
            "UNAVAILABLE", IGNORE, INVALID_REQUEST, NamedTypeDoc.UNAVAILABLE,
        )
    }

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
    val excludedBrokerIds: List<BrokerId>,
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