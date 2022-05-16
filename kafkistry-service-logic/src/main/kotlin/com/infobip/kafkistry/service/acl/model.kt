package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.eachCountDescending
import com.infobip.kafkistry.service.sortedByValueDescending

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
            statusCounts = statuses.groupingBy { it.statusType }.eachCountDescending()
        )
    }

    infix fun merge(other: AclStatus) = AclStatus(
        ok = ok && other.ok,
        statusCounts = (statusCounts.asSequence() + other.statusCounts.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, counts) -> counts.sum() }
            .sortedByValueDescending()
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