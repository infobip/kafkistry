package com.infobip.kafkistry.autopilot.binding.acls

import com.infobip.kafkistry.api.AclsManagementApi
import com.infobip.kafkistry.api.ClustersApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.acl.AvailableAclOperation.CREATE_MISSING_ACLS
import com.infobip.kafkistry.service.acl.AvailableAclOperation.DELETE_UNWANTED_ACLS
import org.springframework.stereotype.Component

@Component
class AclsAutopilotBinding(
    private val inspectApi: InspectApi,
    private val clustersApi: ClustersApi,
    private val managementApi: AclsManagementApi,
    private val commonBlockerChecker: CommonBlockerChecker,
): AutopilotBinding<AclAutopilotAction> {

    override fun listCapabilities(): List<ActionDescription> {
        return listOf(
            CreateMissingAclAction.DESCRIPTION,
            DeleteUnwantedAclAction.DESCRIPTION,
        )
    }

    override fun actionsToProcess(): List<AclAutopilotAction> {
        val refCache = mutableMapOf<KafkaClusterIdentifier, ClusterRef>()
        fun KafkaClusterIdentifier.ref(): ClusterRef = refCache.computeIfAbsent(this) {
            clustersApi.getCluster(it).ref()
        }
        return inspectApi.inspectAllClustersAcls().asSequence()
            .flatMap { it.principalAclsInspections }
            .flatMap { it.statuses.map { status -> it.clusterIdentifier to status } }
            .mapNotNull { (cluster, aclStatus) ->
                val actions = aclStatus.availableOperations
                when {
                    CREATE_MISSING_ACLS in actions -> CreateMissingAclAction(cluster.ref(), aclStatus)
                    DELETE_UNWANTED_ACLS in actions -> DeleteUnwantedAclAction(cluster.ref(), aclStatus)
                    else -> null
                }
            }
            .toList()
    }

    override fun checkBlockers(action: AclAutopilotAction): List<AutopilotActionBlocker> {
        return when (action) {
            is CreateMissingAclAction -> listOfNotNull(
                commonBlockerChecker.allNodesOnline(action.clusterRef.identifier),
                action.aclStatus.conflictingAcls.takeIf { it.isNotEmpty() }?.let { rules ->
                    AutopilotActionBlocker(
                        message = "ACL rule is in conflict with other %RULE_COUNT% rules: %RULES_LIST%",
                        placeholders = mapOf(
                            "RULE_COUNT" to Placeholder("acls.count", rules.size),
                            "RULES_LIST" to Placeholder("acls.list", rules.map { it.asString() }),
                        )
                    )
                }
            )
            is DeleteUnwantedAclAction -> listOfNotNull(
                commonBlockerChecker.allNodesOnline(action.clusterRef.identifier),
            )
        }
    }

    override fun processAction(action: AclAutopilotAction) {
        when (action) {
            is CreateMissingAclAction -> managementApi.createPrincipalAclOnCluster(
                clusterIdentifier = action.clusterRef.identifier,
                principal = action.aclStatus.rule.principal,
                rule = action.aclStatus.rule.asString(),
            )
            is DeleteUnwantedAclAction -> managementApi.deletePrincipalAclRuleOnCluster(
                clusterIdentifier = action.clusterRef.identifier,
                principal = action.aclStatus.rule.principal,
                rule = action.aclStatus.rule.asString(),
            )
        }
    }

}