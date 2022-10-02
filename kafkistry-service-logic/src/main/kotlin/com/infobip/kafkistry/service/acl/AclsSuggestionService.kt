package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.SECURITY_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service

@Service
class AclsSuggestionService(
    private val aclsInspector: AclsInspectionService,
    private val aclsRegistry: AclsRegistryService,
    private val clustersRegistry: ClustersRegistryService
) {

    fun suggestPrincipalAclsImport(
            principal: PrincipalId
    ): PrincipalAclRules {
        val principalAclsInspection = aclsInspector.inspectUnknownPrincipals()
                .find { it.principal == principal }
                ?: throw KafkistryIllegalStateException(
                        "Can't import acls of principal '$principal', it's not listed as unexpected on any cluster"
                )
        return principalAclsInspection.toPrincipalAcls(null)
    }

    fun suggestPrincipalAclsUpdate(
            principal: PrincipalId
    ): PrincipalAclRules {
        val currentPrincipalAcls = aclsRegistry.findPrincipalAcls(principal)
                ?: throw KafkistryIllegalStateException(
                        "Can't suggest update of acls of principal '$principal', it does not exist in registry"
                )
        val principalAclsInspection = aclsInspector.inspectPrincipalAcls(principal)
        if (principalAclsInspection.status.ok) {
            throw KafkistryIllegalStateException(
                    "Can't suggest update of acls of principal '$principal', it's already ok"
            )
        }
        val principalAclRules = principalAclsInspection.toPrincipalAcls(currentPrincipalAcls)
        return principalAclRules.withRulesOrderedAsIn(currentPrincipalAcls)     //to minimize change diff
    }

    private fun PrincipalAclsInspection.toPrincipalAcls(
            currentPrincipalAcls: PrincipalAclRules?
    ): PrincipalAclRules {
        fun PrincipalAclRules.hasRuleOnCluster(
                rule: KafkaAclRule, clusterRef: ClusterRef
        ): Boolean = rules.any { it.presence.needToBeOnCluster(clusterRef) && it.toKafkaAclRule(principal) == rule }

        val allClusterRefs = clustersRegistry.listClustersRefs()
        val clusterRefs = allClusterRefs.associateBy { it.identifier }
        val disabledClusters = mutableListOf<KafkaClusterIdentifier>()
        val rules = clusterInspections
                .flatMap { clusterInspection ->
                    val clusterIdentifier = clusterInspection.clusterIdentifier
                    val clusterRef = ClusterRef(clusterIdentifier, clusterRefs[clusterIdentifier]?.tags.orEmpty())
                    clusterInspection.statuses
                            .filter {
                                //should include this rule in result
                                it.statusTypes.all { type ->
                                    when (type) {
                                        OK, UNEXPECTED, UNKNOWN -> true
                                        MISSING, NOT_PRESENT_AS_EXPECTED, SECURITY_DISABLED, UNAVAILABLE -> false
                                        CLUSTER_DISABLED -> {
                                            disabledClusters.add(clusterIdentifier)
                                            currentPrincipalAcls?.hasRuleOnCluster(it.rule, clusterRef) ?: false
                                        }
                                        CLUSTER_UNREACHABLE -> throw KafkistryIllegalStateException(
                                            "Can't suggest acls of principal '$principal', cluster is unreachable: '$clusterIdentifier'"
                                        )
                                        else -> true
                                    }
                                }
                            }
                            .map { it.rule to clusterInspection.clusterIdentifier }
                }
                .groupBy({ it.first }, { it.second })
                .map { (rule, clusters) ->
                    rule.toAclRule(allClusterRefs.computePresence(clusters, disabledClusters))
                }
        return PrincipalAclRules(
                principal = principal,
                description = currentPrincipalAcls?.description ?: "",
                owner = currentPrincipalAcls?.owner ?: "",
                rules = rules
        )
    }

    private fun PrincipalAclRules.withRulesOrderedAsIn(other: PrincipalAclRules): PrincipalAclRules {
        val otherRulesIndexes = other.rules
                .mapIndexed { index, rule -> rule.toKafkaAclRule(other.principal) to index }
                .associate { it }
        val (existing, missing) = rules.partition { it.toKafkaAclRule(principal) in otherRulesIndexes }
        return copy(
                rules = existing.sortedBy { otherRulesIndexes[it.toKafkaAclRule(principal)] } + missing
        )
    }
}