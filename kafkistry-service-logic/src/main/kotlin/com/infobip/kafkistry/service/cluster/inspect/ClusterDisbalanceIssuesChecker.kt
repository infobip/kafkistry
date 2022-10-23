package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.generator.balance.BrokerByLoad
import com.infobip.kafkistry.service.generator.balance.BrokerLoad
import com.infobip.kafkistry.service.generator.balance.ClusterBalanceStatus
import com.infobip.kafkistry.service.generator.balance.GlobalBalancerService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.clusters-inspect.disbalance")
class ClusterDisbalanceIssuesCheckerProperties {
    var acceptableDiskUsageDisbalance = 20.0
    var acceptableReplicaCountDisbalance = 10.0
    var acceptableLeadersCountDisbalance = 10.0
}

class DisbalanceProperties {
    var maxAcceptablePercent: Double = 10.0
    var minThreshold: Double? = null
}

@Component
class ClusterDisbalanceIssuesChecker(
    private val properties: ClusterDisbalanceIssuesCheckerProperties,
    private val globalBalancerService: GlobalBalancerService,
) : ClusterIssueChecker {

    override fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue> {
        val balanceStatus = globalBalancerService.getCurrentBalanceStatus(clusterIdentifier)
        return buildList {
            with(balanceStatus) {
                if (loadDiffPortion.size > properties.acceptableDiskUsageDisbalance) {
                    disbalanceIssue(
                        name = "DISK_USAGE_DISBALANCE",
                        resourceName = "Disk usage",
                        valueName = "usage.bytes",
                        acceptableDisbalance = properties.acceptableDiskUsageDisbalance,
                        resourceValue = { it.size },
                        resourceValueBrokers = { it.size },
                    ).also { add(it) }
                }
                if (loadDiffPortion.replicas > properties.acceptableReplicaCountDisbalance) {
                    disbalanceIssue(
                        name = "REPLICAS_COUNT_DISBALANCE",
                        resourceName = "Replica count",
                        valueName = "replica.count",
                        acceptableDisbalance = properties.acceptableReplicaCountDisbalance,
                        resourceValue = { it.replicas },
                        resourceValueBrokers = { it.replicas },
                    ).also { add(it) }
                }
                if (loadDiffPortion.leaders > properties.acceptableLeadersCountDisbalance) {
                    disbalanceIssue(
                        name = "LEADERS_COUNT_DISBALANCE",
                        resourceName = "Leaders count",
                        valueName = "leaders.count",
                        acceptableDisbalance = properties.acceptableLeadersCountDisbalance,
                        resourceValue = { it.leaders },
                        resourceValueBrokers = { it.leaders },
                    ).also { add(it) }
                }
            }
        }
    }

    private fun ClusterBalanceStatus.disbalanceIssue(
        name: String, resourceName: String, valueName: String,
        acceptableDisbalance: Double,
        resourceValue: (BrokerLoad) -> Double,
        resourceValueBrokers: (BrokerByLoad) -> List<BrokerId>,
    ): ClusterInspectIssue {
        val disbalance = resourceValue(loadDiffPortion)
        val maxBrokerId = resourceValueBrokers(maxLoadBrokers).first()
        val minBrokerId = resourceValueBrokers(minLoadBrokers).first()
        val minBrokerValue = brokerLoads[minBrokerId]?.let(resourceValue) ?: 0.0
        val maxBrokerValue = brokerLoads[maxBrokerId]?.let(resourceValue) ?: 0.0
        return ClusterInspectIssue(
            name = name,
            violation = RuleViolation(
                ruleClassName = checkerClassName,
                severity = RuleViolation.Severity.WARNING,
                message = "$resourceName disbalance portion is %DISBALANCE% which is more than acceptable disbalance of %ACCEPTABLE_DISBALANCE%. " +
                        "Minimal load is on broker with ID %MIN_BROKER_ID% having $resourceName of %MIN_BROKER_VALUE%. " +
                        "Maximum load is on broker with ID %MAX_BROKER_ID% having $resourceName of %MAX_BROKER_VALUE%. ",
                placeholders = mapOf(
                    "DISBALANCE" to Placeholder("disbalance.percent", disbalance),
                    "ACCEPTABLE_DISBALANCE" to Placeholder("disbalance.percent", acceptableDisbalance),
                    "MIN_BROKER_ID" to Placeholder("broker.id", minBrokerId),
                    "MAX_BROKER_ID" to Placeholder("broker.id", maxBrokerId),
                    "MIN_BROKER_VALUE" to Placeholder(valueName, minBrokerValue),
                    "MAX_BROKER_VALUE" to Placeholder(valueName, maxBrokerValue),
                ),
            ),
            doc = "Indicates that here is significant disproportion of ${resourceName.lowercase()} across brokers in a cluster."
        )
    }

}