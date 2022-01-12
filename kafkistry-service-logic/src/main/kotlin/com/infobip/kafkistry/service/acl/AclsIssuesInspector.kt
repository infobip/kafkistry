package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.kafkastate.StateType.DISABLED
import com.infobip.kafkistry.kafkastate.StateType.VISIBLE
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.AclInspectionResultType.*
import com.infobip.kafkistry.model.*
import org.springframework.stereotype.Component

@Component
class AclsIssuesInspector(
        private val aclLinkResolver: AclLinkResolver
) {

    fun inspectPrincipalAcls(
        clusterRef: ClusterRef,
        principal: PrincipalId,
        principalAcls: PrincipalAclRules?,
        clusterState: StateData<KafkaClusterState>
    ): PrincipalAclsClusterInspection {
        val clusterData = clusterState.valueOrNull()
        if (principalAcls == null) {
            return if (clusterData?.acls == null) {
                emptyInspection(principal, clusterRef.identifier, clusterState.stateType)
            } else {
                val statuses = clusterData.acls
                        .filter { it.principal == principal }
                        .asUnknownRules(clusterRef.identifier, false)
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
            clusterState.stateType == DISABLED -> principalAcls.toInspectionResult(clusterRef.identifier) { CLUSTER_DISABLED }
            clusterState.stateType != VISIBLE || clusterData?.acls == null -> {
                principalAcls.toInspectionResult(clusterRef.identifier) { CLUSTER_UNREACHABLE }
            }
            clusterData.clusterInfo.securityEnabled.not() -> {
                principalAcls.toInspectionResult(clusterRef.identifier) {
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
                    existingPrincipalAcls = clusterData.acls.filter { it.principal == principal }
            )
        }
    }

    fun inspectSingleRule(
        aclRule: KafkaAclRule,
        principalAcls: PrincipalAclRules?,
        clusterRef: ClusterRef,
        clusterState: StateData<KafkaClusterState>
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
        return AclRuleStatus(
                statusType = statusType,
                rule = aclRule,
                affectedTopics = aclLinkResolver.findAffectedTopics(aclRule, clusterRef.identifier),
                affectedConsumerGroups = aclLinkResolver.findAffectedConsumerGroups(aclRule, clusterRef.identifier),
                availableOperations = statusType.availableOperations(principalAcls != null)
        )
    }

    private fun PrincipalAclRules.toInspectionResult(
        clusterIdentifier: KafkaClusterIdentifier,
        statusResolver: (AclRule) -> AclInspectionResultType
    ): PrincipalAclsClusterInspection {
        val statuses = rules.map {
            val aclRule = it.toKafkaAclRule(principal)
            val statusType = statusResolver(it)
            AclRuleStatus(
                    statusType = statusType,
                    rule = aclRule,
                    affectedTopics = aclLinkResolver.findAffectedTopics(aclRule, clusterIdentifier),
                    affectedConsumerGroups = aclLinkResolver.findAffectedConsumerGroups(aclRule, clusterIdentifier),
                    availableOperations = statusType.availableOperations(true)
            )
        }
        return PrincipalAclsClusterInspection(
                clusterIdentifier = clusterIdentifier,
                principal = principal,
                statuses = statuses,
                status = AclStatus.from(statuses),
                availableOperations = statuses.mergeAvailableOps { it.availableOperations },
                affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principal, clusterIdentifier),
        )
    }

    private fun inspectAcls(
        clusterRef: ClusterRef,
        principalAcls: PrincipalAclRules,
        existingPrincipalAcls: List<KafkaAclRule>
    ): PrincipalAclsClusterInspection {
        val statuses = principalAcls.rules.map {
            val kafkaAclRule = it.toKafkaAclRule(principalAcls.principal)
            val statusType = statusWhen(
                    exists = kafkaAclRule in existingPrincipalAcls,
                    shouldExist = it.presence.needToBeOnCluster(clusterRef)
            )
            AclRuleStatus(
                    statusType = statusType,
                    rule = kafkaAclRule,
                    affectedTopics = aclLinkResolver.findAffectedTopics(kafkaAclRule, clusterRef.identifier),
                    affectedConsumerGroups = aclLinkResolver.findAffectedConsumerGroups(kafkaAclRule, clusterRef.identifier),
                    availableOperations = statusType.availableOperations(true)
            )
        }
        val wantedAclRules = principalAcls.rules.map { it.toKafkaAclRule(principalAcls.principal) }
        val unknownStatuses = existingPrincipalAcls
                .filter { it !in wantedAclRules }
                .asUnknownRules(clusterRef.identifier, true)
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
                    statusCounts = emptyMap()
            ),
            availableOperations = emptyList(),
            affectingQuotaEntities = aclLinkResolver.findPrincipalAffectingQuotas(principal, clusterIdentifier),
    )

    private fun Iterable<KafkaAclRule>.asUnknownRules(
        clusterIdentifier: KafkaClusterIdentifier,
        principalExists: Boolean
    ): List<AclRuleStatus> = map {
        AclRuleStatus(
                statusType = UNKNOWN,
                rule = it,
                affectedTopics = aclLinkResolver.findAffectedTopics(it, clusterIdentifier),
                affectedConsumerGroups = aclLinkResolver.findAffectedConsumerGroups(it, clusterIdentifier),
                availableOperations = UNKNOWN.availableOperations(principalExists)
        )
    }

}

