package com.infobip.kafkistry.autopilot.binding

import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.cluster.inspect.DiskUsageIssuesChecker
import org.springframework.stereotype.Component

@Component
class CommonBlockerChecker(
    private val inspectApi: InspectApi,
    private val diskIssuesChecker: DiskUsageIssuesChecker,
    private val checkingCache: CheckingCache,
) {

    fun allBrokersOnline(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        return checkingCache.cache("AllBrokersOnline", clusterIdentifier) {
            allBrokersOnlineImpl(clusterIdentifier)
        }
    }

    fun clusterHavingOverPromisedRetention(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        return checkingCache.cache("OverPromisedRetention", clusterIdentifier) {
            clusterHavingOverPromisedRetentionImpl(clusterIdentifier)
        }
    }

    private fun ok(): AutopilotActionBlocker? = null

    private fun allBrokersOnlineImpl(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        val clusterStatus = inspectApi.inspectClusterStatus(clusterIdentifier)
        val clusterInfo = clusterStatus.clusterInfo ?: return AutopilotActionBlocker(
            message = "Not having cluster info, state is %CLUSTER_STATE%",
            placeholders = mapOf("CLUSTER_STATE" to Placeholder("cluster.state", clusterStatus.clusterState)),
        )
        val offlineBrokers = clusterInfo.nodeIds - clusterInfo.onlineNodeIds.toSet()
        if (offlineBrokers.isNotEmpty()) {
            return AutopilotActionBlocker(
                message = "Cluster has offline brokers/nodes: %OFFLINE_BROKER_IDS%",
                placeholders = mapOf("OFFLINE_BROKER_IDS" to Placeholder("broker.ids", offlineBrokers)),
            )
        }
        return ok()
    }

    private fun clusterHavingOverPromisedRetentionImpl(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        val overPromisedRetention = diskIssuesChecker.checkIssues(clusterIdentifier)
            .find { it.name == DiskUsageIssuesChecker.OVER_PROMISED_RETENTION }
            ?: return ok()
        return AutopilotActionBlocker(
            message = overPromisedRetention.violation.message,
            placeholders = overPromisedRetention.violation.placeholders,
        )
    }

}