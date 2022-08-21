package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.AclRule
import com.infobip.kafkistry.model.Presence
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclInspectionResultType.*
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.SECURITY_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNKNOWN

fun AclRule.toKafkaAclRule(principal: PrincipalId) = KafkaAclRule(
    principal = principal,
    host = host,
    resource = resource,
    operation = operation
)

fun KafkaAclRule.toAclRule(presence: Presence) = AclRule(
    presence = presence,
    host = host,
    resource = resource,
    operation = operation
)

fun PrincipalAclsInspection.transpose(): PrincipalAclsClustersPerRuleInspection {
    val ruleStatuses = clusterInspections
        .flatMap { clusterStatuses -> clusterStatuses.statuses.map { clusterStatuses.clusterIdentifier to it } }
        .groupBy { (_, ruleStatus) -> ruleStatus.rule }
        .map { (rule, statuses) ->
            AclRuleClustersInspection(
                aclRule = rule,
                clusterStatuses = statuses.associate { it },
                status = AclStatus.from(statuses.map { (_, ruleStatus) -> ruleStatus }),
                availableOperations = statuses.mergeAvailableOps { it.second.availableOperations }
            )
        }
    val clusterAffectingQuotaEntities = clusterInspections.associate {
        it.clusterIdentifier to it.affectingQuotaEntities
    }
    return PrincipalAclsClustersPerRuleInspection(
        principal = principal,
        principalAcls = principalAcls,
        statuses = ruleStatuses,
        status = ruleStatuses.map { it.status }.aggregate(),
        availableOperations = availableOperations,
        clusterAffectingQuotaEntities = clusterAffectingQuotaEntities,
        affectingQuotaEntities = affectingQuotaEntities,
    )
}

fun <T> Iterable<T>.mergeAvailableOps(extractor: (T) -> List<AvailableAclOperation>): List<AvailableAclOperation> =
    flatMap(extractor).distinct().sorted()

fun AclInspectionResultType.availableOperations(
    principalExists: Boolean
): List<AvailableAclOperation> = when (this) {
    OK, NOT_PRESENT_AS_EXPECTED, CLUSTER_UNREACHABLE, SECURITY_DISABLED, CLUSTER_DISABLED, UNAVAILABLE -> emptyList()
    MISSING -> listOf(
        AvailableAclOperation.CREATE_MISSING_ACLS,
        AvailableAclOperation.EDIT_PRINCIPAL_ACLS
    )
    UNEXPECTED -> listOf(
        AvailableAclOperation.DELETE_UNWANTED_ACLS,
        AvailableAclOperation.EDIT_PRINCIPAL_ACLS
    )
    UNKNOWN -> listOf(
        AvailableAclOperation.DELETE_UNWANTED_ACLS,
        if (principalExists) {
            AvailableAclOperation.EDIT_PRINCIPAL_ACLS
        } else {
            AvailableAclOperation.IMPORT_PRINCIPAL
        }
    )
    else -> emptyList()
}