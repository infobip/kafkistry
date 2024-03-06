package com.infobip.kafkistry.service.cluster.inspect

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
        if (clusterInfo.nodeIds.size == 1) {
            return emptyList()  //no point in checking rack if having only one node
        }
        if (clusterInfo.onlineNodeIds.toSet() != clusterInfo.nodeIds.toSet()) {
            return emptyList()  //some nodes are down, no further analysis
        }
        val nodesRackNullable = clusterInfo.perBrokerConfig.mapValues { (_, configs) ->
            configs[KafkaConfig.RackProp()]?.value?.takeIf { it != properties.undefinedRackValue }
        }
        val allUndefined = nodesRackNullable.values.all { it == null }
        if (allUndefined) {
            return if (properties.tolerateUndefined) {
                emptyList() //no rack info, no analysis
            } else {
                listOf(
                    ClusterInspectIssue(
                        name = RACK_ABSENT_ISSUE,
                        doc = "Indicates that nodes in cluster have no rack defined",
                        violation = RuleViolation(
                            ruleClassName = checkerClassName,
                            severity = RuleViolation.Severity.WARNING,
                            message = "All %NUM_BROKERS% brokers do not have %RACK_PROPERTY% defined",
                            placeholders = mapOf(
                                "NUM_BROKERS" to Placeholder("num.brokers", clusterInfo.nodeIds.size),
                                "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                            )
                        )
                    )
                )
            }
        }
        val undefinedRackBrokerIds = nodesRackNullable
            .filterValues { it == null }
            .keys.sorted()
        if (undefinedRackBrokerIds.isNotEmpty()) {
            return listOf(
                ClusterInspectIssue(
                    name = RACK_ABSENT_ISSUE,
                    doc = "Indicates that some nodes in cluster have no rack defined, while some do",
                    violation = RuleViolation(
                        ruleClassName = checkerClassName,
                        severity = RuleViolation.Severity.WARNING,
                        message = "%NUM_BROKERS% brokers %BROKER_IDS% do not have %RACK_PROPERTY% defined",
                        placeholders = mapOf(
                            "NUM_BROKERS" to Placeholder("num.brokers", clusterInfo.nodeIds.size),
                            "BROKER_IDS" to Placeholder("broker.ids", undefinedRackBrokerIds),
                            "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
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
                        doc = "Indicates that all nodes in cluster have same rack, meaning less redundancy",
                        violation = RuleViolation(
                            ruleClassName = checkerClassName,
                            severity = RuleViolation.Severity.WARNING,
                            message = "All %NUM_BROKERS% brokers have same %RACK_PROPERTY%=%RACK_ID",
                            placeholders = mapOf(
                                "NUM_BROKERS" to Placeholder("num.brokers", clusterInfo.nodeIds.size),
                                "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                                "RACK_ID" to Placeholder(KafkaConfig.RackProp(), nodesRack.values.first()),
                            )
                        )
                    )
                )
            }
        }
        val rackNodes = nodesRack.entries.groupBy ({ it.value }, { it.key })
        val minRackBrokerIds = rackNodes.minBy { it.value.size }
        val maxRackBrokerIds = rackNodes.maxBy { it.value.size }
        val exactBalance = minRackBrokerIds.value.size == maxRackBrokerIds.value.size
        val semiBalance = minRackBrokerIds.value.size + 1 == maxRackBrokerIds.value.size
        if (exactBalance || (semiBalance && !properties.strictBalance)) {
            return emptyList()
        } else {
            return listOf(
                ClusterInspectIssue(
                    name = RACK_DISBALANCE_ISSUE,
                    doc = "Indicates that different amount of nodes in cluster have same rack, meaning disbalance",
                    violation = RuleViolation(
                        ruleClassName = checkerClassName,
                        severity = RuleViolation.Severity.WARNING,
                        message = "Different number of brokers per rack, " +
                            "%NUM_BROKERS_MIN% brokers have %RACK_PROPERTY%=%RACK_ID_MIN (ids=%BROKER_IDS_MIN%), " +
                            "%NUM_BROKERS_MAX% brokers have %RACK_PROPERTY%=%RACK_ID_MAX (ids=%BROKER_IDS_MAX%)",
                        placeholders = mapOf(
                            "NUM_BROKERS_MIN" to Placeholder("num.brokers", minRackBrokerIds.value.size),
                            "NUM_BROKERS_MAX" to Placeholder("num.brokers", maxRackBrokerIds.value.size),
                            "BROKER_IDS_MIN" to Placeholder("broker.ids", minRackBrokerIds.value.sorted()),
                            "BROKER_IDS_MAX" to Placeholder("broker.ids", maxRackBrokerIds.value.sorted()),
                            "RACK_ID_MIN" to Placeholder(KafkaConfig.RackProp(), minRackBrokerIds.key),
                            "RACK_ID_MAX" to Placeholder(KafkaConfig.RackProp(), maxRackBrokerIds.key),
                            "RACK_PROPERTY" to Placeholder("property.name", KafkaConfig.RackProp()),
                        )
                    )
                )
            )
        }
    }
}