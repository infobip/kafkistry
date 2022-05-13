package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.events.AclCreatedEvent
import com.infobip.kafkistry.events.AclDeletedEvent
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclInspectionResultType.*
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.KafkistryIntegrityException
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service

@Service
class AclsManagementService(
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaClientProvider: KafkaClientProvider,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val aclsInspection: AclsInspectionService,
    private val eventPublisher: EventPublisher
) {

    fun deleteUnknownOrUnexpectedPrincipalAcls(
        principal: PrincipalId, clusterIdentifier: KafkaClusterIdentifier
    ) {
        val clusterInspection = aclsInspection.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
        val rulesToDelete = clusterInspection.statuses.asSequence()
                .filter { it.statusType == UNKNOWN || it.statusType == UNEXPECTED }
                .map { it.rule }
                .toList()
        if (rulesToDelete.isEmpty()) {
            throw KafkistryIllegalStateException(
                    "No UNKNOWN or UNEXPECTED acl rules of principal '$principal' on cluster '$clusterIdentifier' to create, " +
                            "principal's rules statuses are " + clusterInspection.status
            )
        }
        doDeleteAclRules(principal, rulesToDelete, clusterIdentifier)
    }

    fun deletePrincipalAclOnCluster(
        principal: PrincipalId, rule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ) {
        val clusterInspection = aclsInspection.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
        val ruleStatus = clusterInspection.statuses.asSequence()
                .find { it.rule == rule }
                ?: throw KafkistryIllegalStateException("Rule to delete is not found, rule: $rule")
        when (ruleStatus.statusType) {
            UNKNOWN, UNEXPECTED -> Unit
            else -> throw KafkistryIllegalStateException("Rule to delete is not for deletion, status is ${ruleStatus.statusType}")
        }
        doDeleteAclRules(rule.principal, listOf(rule), clusterIdentifier)
    }

    fun forceDeletePrincipalAclOnCluster(
        principal: PrincipalId, rule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ) = doDeleteAclRules(rule.principal, listOf(rule), clusterIdentifier)

    fun createPrincipalMissingAcls(
        principal: PrincipalId, clusterIdentifier: KafkaClusterIdentifier
    ) {
        val clusterInspection = aclsInspection.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
        val rulesToCreate = clusterInspection.statuses.asSequence()
                .filter { it.statusType == MISSING }
                .map { it.rule }
                .toList()
        if (rulesToCreate.isEmpty()) {
            throw KafkistryIllegalStateException(
                    "No MISSING acl rules of principal '$principal' on cluster '$clusterIdentifier' to create, " +
                            "principal's rules statuses are " + clusterInspection.status
            )
        }
        doCreateAclRules(principal, rulesToCreate, clusterIdentifier)
    }

    fun createPrincipalMissingAcl(principal: PrincipalId, rule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier) {
        val clusterInspection = aclsInspection.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
        val ruleStatus = clusterInspection.statuses.asSequence()
                .find { it.rule == rule }
                ?: throw KafkistryIllegalStateException("Rule to create is not found, rule: $rule")
        when (ruleStatus.statusType) {
            MISSING -> Unit
            else -> throw KafkistryIllegalStateException("Rule to create is not missing, status is ${ruleStatus.statusType}")
        }
        doCreateAclRules(rule.principal, listOf(rule), clusterIdentifier)
    }

    fun forceCreatePrincipalAcl(
        principal: PrincipalId, rule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ) = doCreateAclRules(principal, listOf(rule), clusterIdentifier)

    private fun doDeleteAclRules(
        principal: PrincipalId, rules: List<KafkaAclRule>, clusterIdentifier: KafkaClusterIdentifier
    ) {
        rules.checkEachPrincipal(principal)
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        kafkaClientProvider.doWithClient(kafkaCluster) {
            it.deleteAcls(rules).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(AclDeletedEvent(clusterIdentifier, principal))
    }

    private fun doCreateAclRules(
        principal: PrincipalId, rules: List<KafkaAclRule>, clusterIdentifier: KafkaClusterIdentifier
    ) {
        rules.checkEachPrincipal(principal)
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        kafkaClientProvider.doWithClient(kafkaCluster) {
            it.createAcls(rules).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(AclCreatedEvent(clusterIdentifier, principal))
    }

    private fun Iterable<KafkaAclRule>.checkEachPrincipal(principal: PrincipalId) = forEach { it.checkPrincipal(principal) }

    private fun KafkaAclRule.checkPrincipal(principal: PrincipalId) {
        if (this.principal != principal) {
            throw KafkistryIntegrityException(
                    "Principal in rule definition '${this.principal}' is not the same as supplied principal '$principal'"
            )
        }
    }

}