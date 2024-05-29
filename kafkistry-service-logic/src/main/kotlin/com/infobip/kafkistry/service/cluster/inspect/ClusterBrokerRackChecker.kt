package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import kafka.server.KafkaConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.clusters-inspect.rack")
class ClusterBrokerRackCheckerProperties {

    var undefinedRackValue = ""
    var tolerateUndefined = true
    var tolerateAllEqual = false
    var strictBalance = false
}

@Component
class ClusterBrokerRackChecker(
    private val properties: ClusterBrokerRackCheckerProperties,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
) : ClusterIssueChecker {

    companion object {
        const val RACK_ABSENT_ISSUE = "CLUSTER_RACK_UNDEFINED"
        const val EQUAL_RACK_ISSUE = "CLUSTER_RACK_ALL_EQUAL"
        const val RACK_DISBALANCE_ISSUE = "CLUSTER_RACKS_DISBALANCE"
    }

    override fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue> {
        val clusterInfo = kafkaClustersStateProvider.getLatestClusterState(clusterIdentifier)
            .valueOrNull()
            ?.clusterInfo
            ?: return emptyList()
        if (clusterInfo.onlineNodeIds.toSet() != clusterInfo.nodeIds.toSet()) {
            return emptyList()  //some nodes are down, no further analysis
        }
        val brokerIssues = checkNodesIssues(clusterInfo.brokerIds, ClusterNodeRole.BROKER, clusterInfo.perBrokerConfig)
        val controllerIssues = checkNodesIssues(clusterInfo.controllerIds, ClusterNodeRole.CONTROLLER, clusterInfo.perBrokerConfig)
        return brokerIssues + controllerIssues
    }

    private fun checkNodesIssues(
        nodeIds: List<NodeId>, processRole: ClusterNodeRole, perNodeConfig: Map<NodeId, ExistingConfig>,
    ): List<ClusterInspectIssue> {
        if (nodeIds.size == 1) {
            return emptyList()  //no point in checking rack if having only one node
        }
        val nodesRackNullable = nodeIds.associateWith { perNodeConfig[it] }.mapValues { (_, configs) ->
            configs?.get(KafkaConfig.RackProp())?.value?.takeIf { it != properties.undefinedRackValue }
        }
        val allUndefined = nodesRackNullable.values.all { it == null }
        if (allUndefined) {
            return if (properties.tolerateUndefined) {
                emptyList() //no rack info, no analysis
            } else {
                listOf(
                    ClusterInspectIssue(
                        name = RACK_ABSENT_ISSUE,
                        doc = "Indicates that $processRole nodes in cluster have no rack defined",
                        violation = RuleViolation(
                            ruleClassName = checkerClassName,
                            severity = RuleViolation.Severity.WARNING,
                            message = "All %NUM_NODES% %NODE_ROLE%(s) do not have %RACK_PROPERTY% defined",
                            placeholders = mapOf(
                                "NUM_NODES" to Placeholder("num.nodes", nodeIds.size),
                                "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                                "NODE_ROLE" to Placeholder("process.roles", processRole.name),
                            )
                        )
                    )
                )
            }
        }
        val undefinedRackNodeIds = nodesRackNullable
            .filterValues { it == null }
            .keys.sorted()
        if (undefinedRackNodeIds.isNotEmpty()) {
            return listOf(
                ClusterInspectIssue(
                    name = RACK_ABSENT_ISSUE,
                    doc = "Indicates that some $processRole nodes in cluster have no rack defined, while some do",
                    violation = RuleViolation(
                        ruleClassName = checkerClassName,
                        severity = RuleViolation.Severity.WARNING,
                        message = "%NUM_NODES% %NODE_ROLE%(s) %BROKER_IDS% do not have %RACK_PROPERTY% defined",
                        placeholders = mapOf(
                            "NUM_NODES" to Placeholder("num.nodes", nodeIds.size),
                            "NODE_IDS" to Placeholder("node.ids", undefinedRackNodeIds),
                            "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                            "NODE_ROLE" to Placeholder("process.roles", processRole.name),
                        )
                    )
                )
            )
        }

        val nodesRack = nodesRackNullable
            .mapNotNull { (brokerId, rack) -> rack?.let { brokerId to it } }
            .toMap()
        val allEqual = nodesRack.values.toSet().size == 1
        if (allEqual) {
            return if (properties.tolerateAllEqual) {
                emptyList()
            } else {
                listOf(
                    ClusterInspectIssue(
                        name = EQUAL_RACK_ISSUE,
                        doc = "Indicates that all $processRole nodes in cluster have same rack, meaning less redundancy",
                        violation = RuleViolation(
                            ruleClassName = checkerClassName,
                            severity = RuleViolation.Severity.WARNING,
                            message = "All %NUM_NODES% %NODE_ROLE%(s) have same %RACK_PROPERTY%=%RACK_ID%",
                            placeholders = mapOf(
                                "NUM_NODES" to Placeholder("num.nodes", nodeIds.size),
                                "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                                "RACK_ID" to Placeholder(KafkaConfig.RackProp(), nodesRack.values.first()),
                                "NODE_ROLE" to Placeholder("process.roles", processRole.name),
                            )
                        )
                    )
                )
            }
        }
        val rackNodes = nodesRack.entries.groupBy ({ it.value }, { it.key })
        val minRackNodeIds = rackNodes.minBy { it.value.size }
        val maxRackNodeIds = rackNodes.maxBy { it.value.size }
        val exactBalance = minRackNodeIds.value.size == maxRackNodeIds.value.size
        val semiBalance = minRackNodeIds.value.size + 1 == maxRackNodeIds.value.size
        if (exactBalance || (semiBalance && !properties.strictBalance)) {
            return emptyList()
        } else {
            return listOf(
                ClusterInspectIssue(
                    name = RACK_DISBALANCE_ISSUE,
                    doc = "Indicates that different amount of $processRole nodes in cluster have same rack, meaning disbalance",
                    violation = RuleViolation(
                        ruleClassName = checkerClassName,
                        severity = RuleViolation.Severity.WARNING,
                        message = "Different number of %NODE_ROLE%(s) per rack, " +
                            "%NUM_NODES_MIN% %NODE_ROLE%(s) have %RACK_PROPERTY%=%RACK_ID_MIN% (ids=%NODE_IDS_MIN%), " +
                            "%NUM_NODES_MAX% %NODE_ROLE%(s) have %RACK_PROPERTY%=%RACK_ID_MAX% (ids=%NODE_IDS_MAX%)",
                        placeholders = mapOf(
                            "NUM_NODES_MIN" to Placeholder("num.nodes", minRackNodeIds.value.size),
                            "NUM_NODES_MAX" to Placeholder("num.nodes", maxRackNodeIds.value.size),
                            "NODE_IDS_MIN" to Placeholder("nodes.ids", minRackNodeIds.value.sorted()),
                            "NODE_IDS_MAX" to Placeholder("nodes.ids", maxRackNodeIds.value.sorted()),
                            "RACK_ID_MIN" to Placeholder(KafkaConfig.RackProp(), minRackNodeIds.key),
                            "RACK_ID_MAX" to Placeholder(KafkaConfig.RackProp(), maxRackNodeIds.key),
                            "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                            "NODE_ROLE" to Placeholder("process.roles", processRole.name),
                        )
                    )
                )
            )
        }
    }
}