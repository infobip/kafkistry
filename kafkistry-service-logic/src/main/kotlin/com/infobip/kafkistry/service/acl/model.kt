package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.StatusLevel.*

data class AclInspectionResultType(
    override val name: String,
    override val level: StatusLevel,
    val valid: Boolean,
) : NamedType {
    companion object {
        val OK = AclInspectionResultType("OK", SUCCESS, true)
        val MISSING = AclInspectionResultType("MISSING", ERROR, false)
        val UNEXPECTED =  AclInspectionResultType("UNEXPECTED", ERROR, false)
        val NOT_PRESENT_AS_EXPECTED = AclInspectionResultType("NOT_PRESENT_AS_EXPECTED", IGNORE, true)
        val UNKNOWN = AclInspectionResultType("UNKNOWN", WARNING, false)
        val CLUSTER_DISABLED = AclInspectionResultType("CLUSTER_DISABLED", IGNORE, true)
        val CLUSTER_UNREACHABLE = AclInspectionResultType("CLUSTER_UNREACHABLE", ERROR, false)
        val SECURITY_DISABLED = AclInspectionResultType("SECURITY_DISABLED", WARNING, false)
        val UNAVAILABLE = AclInspectionResultType("UNAVAILABLE", IGNORE, false)
    }
}

data class AclStatus(
    val ok: Boolean,
    val statusCounts: List<NamedTypeQuantity<AclInspectionResultType, Int>>,
) {
    companion object {
        val EMPTY_OK = AclStatus(true, emptyList())

        fun from(statuses: List<AclRuleStatus>) = AclStatus(
            ok = statuses.map { it.statusType.valid }.fold(true, Boolean::and),
            statusCounts = statuses.groupingBy { it.statusType }
                .eachCountDescending()
                .map { NamedTypeQuantity(it.key, it.value) }
        )
    }

    infix fun merge(other: AclStatus) = AclStatus(
        ok = ok && other.ok,
        statusCounts = (statusCounts.asSequence() + other.statusCounts.asSequence())
            .groupBy({ it.type }, { it.quantity })
            .mapValues { (_, counts) -> counts.sum() }
            .sortedByValueDescending()
            .map { NamedTypeQuantity(it.key, it.value) }
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