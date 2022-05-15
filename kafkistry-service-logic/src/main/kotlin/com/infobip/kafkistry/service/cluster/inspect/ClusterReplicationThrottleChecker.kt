package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.ThrottleRate
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import org.springframework.stereotype.Component

@Component
class ClusterReplicationThrottleChecker(
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
) : ClusterIssueChecker {

    override fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue> {
        val clusterInfo = kafkaClustersStateProvider.getLatestClusterState(clusterIdentifier)
            .valueOrNull()
            ?.clusterInfo
            ?: return emptyList()
        return with(clusterInfo) {
            listOfNotNull(
                analyzeThrottle("HAS_LEADER_REPLICATION_THROTTLE", "leader throttle rate") { it.leaderRate },
                analyzeThrottle("HAS_FOLLOWER_REPLICATION_THROTTLE", "follower throttle rate") { it.followerRate },
                analyzeThrottle("HAS_ALTER_DIR_REPLICATION_THROTTLE", "alter dir IO rate") { it.alterDirIoRate },
            )
        }
    }

    private fun ClusterInfo.analyzeThrottle(
        issueName: String,
        name: String,
        throttleBy: (ThrottleRate) -> Long?,
    ): ClusterInspectIssue? {
        val brokerThrottles = perBrokerThrottle
            .mapValues { throttleBy(it.value) }
            .entries
            .mapNotNull { (brokerId, throttle) -> throttle?.let { brokerId to it } }
            .toMap()
        val minThrottle = brokerThrottles.values.minOrNull() ?: return null
        val maxThrottle = brokerThrottles.values.maxOrNull() ?: return null
        val allBrokersAffected = brokerThrottles.keys == onlineNodeIds.toSet()
        val allHaveSameRate = minThrottle == maxThrottle
        return ClusterInspectIssue(
            name = issueName,
            violation = RuleViolation(
                ruleClassName = checkerClassName,
                severity = RuleViolation.Severity.MINOR,
                message = buildString {
                    if (allBrokersAffected) {
                        append("All brokers")
                    } else if (brokerThrottles.size == 1) {
                        append("Broker ID ").append(brokerThrottles.keys.first())
                    } else {
                        append("Some brokers")
                    }
                    append(" have ").append(name).append(" of ")
                    if (allHaveSameRate) {
                        append("%THROTTLE_RATE%")
                    } else {
                        append("%MIN_THROTTLE_RATE% up to %MAX_THROTTLE_RATE%")
                    }
                },
                placeholders = if (allHaveSameRate) {
                    mapOf("THROTTLE_RATE" to Placeholder(THROTTLE_RATE, minThrottle))
                } else {
                    mapOf(
                        "MIN_THROTTLE_RATE" to Placeholder(THROTTLE_RATE, minThrottle),
                        "MAX_THROTTLE_RATE" to Placeholder(THROTTLE_RATE, maxThrottle),
                    )
                },
            )
        )
    }

    companion object {
        private const val THROTTLE_RATE = "throttle.rate"
    }
}