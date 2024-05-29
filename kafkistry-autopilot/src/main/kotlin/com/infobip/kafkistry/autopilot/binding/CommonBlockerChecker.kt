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

    fun allNodesOnline(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        return checkingCache.cache("AllNodesOnline", clusterIdentifier) {
            allNodesOnlineImpl(clusterIdentifier)
        }
    }

    fun clusterHavingOverPromisedRetention(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        return checkingCache.cache("OverPromisedRetention", clusterIdentifier) {
            clusterHavingOverPromisedRetentionImpl(clusterIdentifier)
        }
    }

    private fun ok(): AutopilotActionBlocker? = null

    private fun allNodesOnlineImpl(clusterIdentifier: KafkaClusterIdentifier): AutopilotActionBlocker? {
        val clusterStatus = inspectApi.inspectClusterStatus(clusterIdentifier)
        val clusterInfo = clusterStatus.clusterInfo ?: return AutopilotActionBlocker(
            message = "Not having cluster info, state is %CLUSTER_STATE%",
            placeholders = mapOf("CLUSTER_STATE" to Placeholder("cluster.state", clusterStatus.clusterState)),
        )
        val offlineNodes = clusterInfo.nodeIds - clusterInfo.onlineNodeIds.toSet()
        if (offlineNodes.isNotEmpty()) {
            return AutopilotActionBlocker(
                message = "Cluster has offline brokers/nodes: %OFFLINE_NODE_IDS%",
                placeholders = mapOf("OFFLINE_NODE_IDS" to Placeholder("node.ids", offlineNodes)),
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