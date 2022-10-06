package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service

@Service
class AclsInspectionService(
    private val aclsRegistry: AclsRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
    private val aclsIssuesInspector: AclsIssuesInspector,
    private val aclsConflictResolver: AclsConflictResolver,
) {

    fun inspectAllPrincipals(): List<PrincipalAclsInspection> {
        return aclsRegistry.listAllPrincipalsAcls().map { principalAcls ->
            inspectPrincipalAcls(principalAcls.principal, principalAcls)
        }
    }

    fun inspectUnknownPrincipals(): List<PrincipalAclsInspection> {
        val knownPrincipals = aclsRegistry.listAllPrincipalsAcls().map { it.principal }.toSet()
        val unknownPrincipals = clustersRegistry.listClustersIdentifiers()
                .map { kafkaClustersStateProvider.getLatestClusterState(it) }
                .mapNotNull { it.valueOrNull()?.acls }
                .flatMap { aclRule -> aclRule.map { it.principal } }
                .filter { it !in knownPrincipals }
                .distinct()
                .sorted()
        return unknownPrincipals.map { principal ->
            inspectPrincipalAcls(principal, null)
        }
    }

    fun inspectAllClusters(): List<ClusterAclsInspection> {
        val allPrincipalsAcls = aclsRegistry.listAllPrincipalsAcls()
        return clustersRegistry.listClustersRefs().map { clusterRef ->
            val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterRef.identifier)
            inspectClusterAcls(clusterRef, latestClusterState, allPrincipalsAcls)
        }
    }

    fun inspectPrincipalAclsOnCluster(principal: PrincipalId, clusterIdentifier: KafkaClusterIdentifier): PrincipalAclsClusterInspection {
        val principalAcls = aclsRegistry.findPrincipalAcls(principal)
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return inspectPrincipalAclsOnCluster(principal, principalAcls, clusterRef)
    }

    fun inspectPrincipalAclsPerRule(principal: PrincipalId): PrincipalAclsClustersPerRuleInspection {
        return inspectPrincipalAcls(principal).transpose()
    }

    fun inspectPrincipalAcls(principal: PrincipalId): PrincipalAclsInspection {
        val principalAcls = aclsRegistry.findPrincipalAcls(principal)
        return inspectPrincipalAcls(principal, principalAcls)
    }

    fun inspectPrincipalAcls(principalAcls: PrincipalAclRules): PrincipalAclsInspection {
        val conflictChecker = aclsConflictResolver.checker(
            listOf(principalAcls)
        )
        return inspectPrincipalAcls(principalAcls.principal, principalAcls, conflictChecker)
    }

    fun inspectRuleOnCluster(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): AclRuleStatus {
        val principalAcls = aclsRegistry.findPrincipalAcls(aclRule.principal)
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterIdentifier)
        val conflictResolver = aclsConflictResolver.checker()
        return aclsIssuesInspector.inspectSingleRule(aclRule, principalAcls, clusterRef, latestClusterState, conflictResolver)
    }

    private fun inspectPrincipalAcls(
        principal: PrincipalId,
        principalAcls: PrincipalAclRules?,
        conflictChecker: AclsConflictResolver.ConflictChecker = aclsConflictResolver.checker(),
    ): PrincipalAclsInspection {
        val clustersRefs = clustersRegistry.listClustersRefs()
        val clusterInspections = clustersRefs.map { clusterRef ->
            inspectPrincipalAclsOnCluster(principal, principalAcls, clusterRef, conflictChecker)
        }
        val affectingQuotaEntities = clusterInspections
            .flatMap { it.affectingQuotaEntities.map { entity -> entity to it.clusterIdentifier } }
            .groupBy ({ it.first }, {it.second })
            .mapValues { clustersRefs.computePresence(it.value) }
        return PrincipalAclsInspection(
                principal = principal,
                principalAcls = principalAcls,
                clusterInspections = clusterInspections,
                status = clusterInspections.map { it.status }.aggregate(),
                availableOperations = clusterInspections.mergeAvailableOps { it.availableOperations },
                affectingQuotaEntities = affectingQuotaEntities,
        )
    }

    fun inspectClusterAcls(clusterIdentifier: KafkaClusterIdentifier): ClusterAclsInspection {
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return inspectClusterAcls(clusterRef)
    }

    fun inspectClusterAcls(clusterRef: ClusterRef): ClusterAclsInspection {
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterRef.identifier)
        return inspectClusterAcls(clusterRef, latestClusterState)
    }

    private fun inspectClusterAcls(
        clusterRef: ClusterRef,
        latestClusterState: StateData<KafkaClusterState>
    ): ClusterAclsInspection {
        val allPrincipalsAcls = aclsRegistry.listAllPrincipalsAcls()
        return inspectClusterAcls(clusterRef, latestClusterState, allPrincipalsAcls)
    }

    private fun inspectClusterAcls(
        clusterRef: ClusterRef,
        latestClusterState: StateData<KafkaClusterState>,
        allPrincipalAcls: List<PrincipalAclRules>,
    ): ClusterAclsInspection {
        val knownPrincipals = allPrincipalAcls.map { it.principal }.toSet()
        val unknownPrincipals = (latestClusterState.valueOrNull()?.acls ?: emptyList()).asSequence()
                .map { it.principal }
                .filter { it !in knownPrincipals }
                .distinct()
                .toList()
        val principalAclsInspections = allPrincipalAcls.asSequence()
                .map { it.principal to it as PrincipalAclRules? }
                .plus(unknownPrincipals.asSequence().map { it to null })
                .sortedBy { (principal, _) -> principal }
                .map { (principal, principalAcls) ->
                    aclsIssuesInspector.inspectPrincipalAcls(
                        principal = principal,
                        clusterRef = clusterRef,
                        principalAcls = principalAcls,
                        clusterState = latestClusterState,
                        conflictChecker = aclsConflictResolver.checker(),
                    )
                }
                .toList()
        return ClusterAclsInspection(
            clusterIdentifier = clusterRef.identifier,
            principalAclsInspections = principalAclsInspections,
            status = principalAclsInspections.map { it.status }.aggregate(),
        )
    }

    private fun inspectPrincipalAclsOnCluster(
        principal: PrincipalId,
        principalAcls: PrincipalAclRules?,
        clusterRef: ClusterRef,
        conflictChecker: AclsConflictResolver.ConflictChecker = aclsConflictResolver.checker(),
    ): PrincipalAclsClusterInspection {
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterRef.identifier)
        return aclsIssuesInspector.inspectPrincipalAcls(
            principal = principal,
            clusterRef = clusterRef,
            principalAcls = principalAcls,
            clusterState = latestClusterState,
            conflictChecker = conflictChecker,
        )
    }

}