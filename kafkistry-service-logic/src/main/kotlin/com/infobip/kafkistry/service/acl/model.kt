package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.StatusLevel.*

data class AclInspectionResultType(
    override val name: String,
    override val level: StatusLevel,
    override val valid: Boolean,
    override val doc: String,
) : NamedType {
    companion object {
        val OK = AclInspectionResultType("OK", SUCCESS, true, NamedTypeDoc.OK)
        val MISSING = AclInspectionResultType("MISSING", ERROR, false, NamedTypeDoc.MISSING)
        val UNEXPECTED =  AclInspectionResultType("UNEXPECTED", ERROR, false, NamedTypeDoc.UNEXPECTED)
        val NOT_PRESENT_AS_EXPECTED = AclInspectionResultType("NOT_PRESENT_AS_EXPECTED", IGNORE, true, NamedTypeDoc.NOT_PRESENT_AS_EXPECTED)
        val UNKNOWN = AclInspectionResultType("UNKNOWN", WARNING, false, NamedTypeDoc.UNKNOWN)
        val CLUSTER_DISABLED = AclInspectionResultType("CLUSTER_DISABLED", IGNORE, true, NamedTypeDoc.CLUSTER_DISABLED)
        val CLUSTER_UNREACHABLE = AclInspectionResultType("CLUSTER_UNREACHABLE", ERROR, false, NamedTypeDoc.CLUSTER_UNREACHABLE)
        val SECURITY_DISABLED = AclInspectionResultType("SECURITY_DISABLED", WARNING, false, "Can't get ACLs fro kafka due to security/authorizer being disabled")
        val UNAVAILABLE = AclInspectionResultType("UNAVAILABLE", IGNORE, false,  NamedTypeDoc.UNAVAILABLE)
    }
}

data class AclStatus(
    override val ok: Boolean,
    override val statusCounts: List<NamedTypeQuantity<AclInspectionResultType, Int>>,
) : SubjectStatus<AclInspectionResultType>{
    companion object {
        val EMPTY_OK = AclStatus(true, emptyList())

        fun from(statuses: List<AclRuleStatus>) = SubjectStatus.from(statuses.map { it.statusType }) { ok, counts ->
            AclStatus(ok, counts)
        }
    }
}

fun Iterable<AclStatus>.aggregate(): AclStatus = aggregateStatusTypes(AclStatus.EMPTY_OK) { ok, counts ->
    AclStatus(ok, counts)
}

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