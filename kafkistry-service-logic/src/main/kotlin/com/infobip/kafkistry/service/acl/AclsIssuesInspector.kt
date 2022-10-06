package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.kafkastate.StateType.DISABLED
import com.infobip.kafkistry.kafkastate.StateType.VISIBLE
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CONFLICT
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.SECURITY_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNKNOWN
import org.springframework.stereotype.Component

@Component
class AclsIssuesInspector(
    private val aclLinkResolver: AclLinkResolver,
) {

    fun inspectPrincipalAcls(
        clusterRef: ClusterRef,
        principal: PrincipalId,
        principalAcls: PrincipalAclRules?,
        clusterState: StateData<KafkaClusterState>,
        conflictChecker: AclsConflictResolver.ConflictChecker,
    ): PrincipalAclsClusterInspection {
        val clusterData = clusterState.valueOrNull()
        if (principalAcls == null) {
            return if (clusterData?.acls == null) {
                emptyInspection(principal, clusterRef.identifier, clusterState.stateType)
            } else {
                val statuses = clusterData.acls
                        .filter { it.principal == principal }
                        .asUnknownRules(clusterRef, false, conflictChecker)
                PrincipalAclsClusterInspection(
                        principal = principal,
                        clusterIdentifier = clusterRef.identifier,
                        statuses = statuses,
                        status = AclStatus.from(statuses),
                        availableOperations = statuses.mergeAvailableOps { it.availableOperations },
                        affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principal, clusterRef.identifier),
                )
            }
        }
        return when {
            clusterState.stateType == DISABLED -> {
                principalAcls.toInspectionResult(clusterRef, conflictChecker) { CLUSTER_DISABLED }
            }
            clusterState.stateType != VISIBLE || clusterData?.acls == null -> {
                principalAcls.toInspectionResult(clusterRef, conflictChecker) { CLUSTER_UNREACHABLE }
            }
            clusterData.clusterInfo.securityEnabled.not() -> {
                principalAcls.toInspectionResult(clusterRef, conflictChecker) {
                    if (it.presence.needToBeOnCluster(clusterRef)) {
                        SECURITY_DISABLED
                    } else {
                        NOT_PRESENT_AS_EXPECTED
                    }
                }
            }
            else -> inspectAcls(
                clusterRef = clusterRef,
                principalAcls = principalAcls,
                existingPrincipalAcls = clusterData.acls.filter { it.principal == principal },
                conflictChecker = conflictChecker,
            )
        }
    }

    fun inspectSingleRule(
        aclRule: KafkaAclRule,
        principalAcls: PrincipalAclRules?,
        clusterRef: ClusterRef,
        clusterState: StateData<KafkaClusterState>,
        conflictChecker: AclsConflictResolver.ConflictChecker,
    ): AclRuleStatus {
        val clusterData = clusterState.valueOrNull()
        val statusType = when {
            clusterState.stateType == DISABLED -> CLUSTER_DISABLED
            clusterState.stateType != VISIBLE || clusterData?.acls == null -> CLUSTER_UNREACHABLE
            clusterData.clusterInfo.securityEnabled.not() -> SECURITY_DISABLED
            else -> {
                val exists = aclRule in clusterData.acls
                val registryAclRule = principalAcls?.rules?.find { it.toKafkaAclRule(aclRule.principal) == aclRule }
                if (registryAclRule == null) {
                    if (exists) UNKNOWN
                    else UNAVAILABLE
                } else {
                    statusWhen(
                            exists = exists,
                            shouldExist = registryAclRule.presence.needToBeOnCluster(clusterRef)
                    )
                }
            }
        }
        return ruleStatus(statusType, aclRule, clusterRef, principalAcls != null, conflictChecker)
    }

    private fun PrincipalAclRules.toInspectionResult(
        clusterRef: ClusterRef,
        conflictChecker: AclsConflictResolver.ConflictChecker,
        statusResolver: (AclRule) -> AclInspectionResultType
    ): PrincipalAclsClusterInspection {
        val statuses = rules.map {
            val aclRule = it.toKafkaAclRule(principal)
            val statusType = statusResolver(it)
            ruleStatus(statusType, aclRule, clusterRef, true, conflictChecker)
        }
        return PrincipalAclsClusterInspection(
                clusterIdentifier = clusterRef.identifier,
                principal = principal,
                statuses = statuses,
                status = AclStatus.from(statuses),
                availableOperations = statuses.mergeAvailableOps { it.availableOperations },
                affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principal, clusterRef.identifier),
        )
    }

    private fun inspectAcls(
        clusterRef: ClusterRef,
        principalAcls: PrincipalAclRules,
        existingPrincipalAcls: List<KafkaAclRule>,
        conflictChecker: AclsConflictResolver.ConflictChecker,
    ): PrincipalAclsClusterInspection {
        val statuses = principalAcls.rules.map {
            val kafkaAclRule = it.toKafkaAclRule(principalAcls.principal)
            val statusType = statusWhen(
                    exists = kafkaAclRule in existingPrincipalAcls,
                    shouldExist = it.presence.needToBeOnCluster(clusterRef)
            )
            ruleStatus(statusType, kafkaAclRule, clusterRef, true, conflictChecker)
        }
        val wantedAclRules = principalAcls.rules.map { it.toKafkaAclRule(principalAcls.principal) }
        val unknownStatuses = existingPrincipalAcls
                .filter { it !in wantedAclRules }
                .asUnknownRules(clusterRef, true, conflictChecker)
        val allStatuses = statuses + unknownStatuses
        return PrincipalAclsClusterInspection(
                clusterIdentifier = clusterRef.identifier,
                principal = principalAcls.principal,
                statuses = allStatuses,
                status = AclStatus.from(allStatuses),
                availableOperations = allStatuses.mergeAvailableOps { it.availableOperations },
                affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principalAcls.principal, clusterRef.identifier),
        )
    }

    private fun statusWhen(exists: Boolean, shouldExist: Boolean) =
        if (exists) {
            if (shouldExist) OK
            else UNEXPECTED
        } else {
            if (shouldExist) MISSING
            else NOT_PRESENT_AS_EXPECTED
        }

    private fun emptyInspection(
        principal: PrincipalId,
        clusterIdentifier: KafkaClusterIdentifier,
        clusterStateType: StateType
    ) = PrincipalAclsClusterInspection(
        principal = principal,
        clusterIdentifier = clusterIdentifier,
        statuses = emptyList(),
        status = AclStatus(
            ok = clusterStateType == VISIBLE,
            statusCounts = emptyList(),
        ),
        availableOperations = emptyList(),
        affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principal, clusterIdentifier),
    )

    private fun Iterable<KafkaAclRule>.asUnknownRules(
        clusterRef: ClusterRef,
        principalExists: Boolean,
        conflictChecker: AclsConflictResolver.ConflictChecker,
    ): List<AclRuleStatus> = map {
        ruleStatus(UNKNOWN, it, clusterRef, principalExists, conflictChecker)
    }

    private fun ruleStatus(
        statusType: AclInspectionResultType,
        rule: KafkaAclRule,
        clusterRef: ClusterRef,
        principalExists: Boolean,
        conflictChecker: AclsConflictResolver.ConflictChecker,
    ): AclRuleStatus {
        val conflictingAcls = when (rule.resource.type) {
            AclResource.Type.GROUP -> conflictChecker.consumerGroupConflicts(rule, clusterRef)
            AclResource.Type.TRANSACTIONAL_ID -> conflictChecker.transactionalIdConflicts(rule, clusterRef)
            else -> emptyList()
        }
        val statusTypes = if (conflictingAcls.isEmpty()) {
            listOf(statusType)
        } else {
            if (statusType == OK) {
                listOf(CONFLICT)
            } else {
                listOf(statusType, CONFLICT)
            }
        }
        return AclRuleStatus(
            statusTypes = statusTypes,
            rule = rule,
            affectedTopics = aclLinkResolver.findAffectedTopics(rule, clusterRef.identifier),
            affectedConsumerGroups = aclLinkResolver.findAffectedConsumerGroups(rule, clusterRef.identifier),
            availableOperations = statusType.availableOperations(principalExists),
            conflictingAcls = conflictingAcls,
        )
    }

}

