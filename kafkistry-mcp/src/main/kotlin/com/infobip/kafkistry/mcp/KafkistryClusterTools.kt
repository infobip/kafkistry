package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.service.cluster.ClusterStatusService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.resources.ClusterResourcesAnalyzer
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistryClusterTools(
    private val clustersRegistryService: ClustersRegistryService,
    private val clusterStatusService: ClusterStatusService,
    private val clusterResourcesAnalyzer: ClusterResourcesAnalyzer,
) {

    @McpTool(
        name = "kafkistry_list_cluster_identifiers",
        description = """Returns the identifiers of all Kafka clusters registered in the Kafkistry registry.
Each identifier is a unique string name for a Kafka cluster managed by Kafkistry.
These identifiers are used as path/query parameters throughout all other API endpoints
that operate on a per-cluster basis. Use this endpoint to discover what clusters are managed
before querying cluster-specific data."""
    )
    open fun kafkistry_list_cluster_identifiers(): String {
        return try {
            val result = clustersRegistryService.listClusters().map { it.identifier }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_cluster_identifiers", ex)
        }
    }

    @McpTool(
        name = "kafkistry_list_cluster_tags",
        description = """Returns all registered clusters with their identifiers and associated tags.
Tags are string labels used by the topic presence system (TAGGED_CLUSTERS presence type) to determine
which topics should exist on which clusters. Use to understand cluster groupings and to map tag-based
topic presence rules to concrete clusters."""
    )
    open fun kafkistry_list_cluster_tags(): String {
        return try {
            val result = clustersRegistryService.listClusters().map { cluster ->
                mapOf(
                    "identifier" to cluster.identifier,
                    "tags" to cluster.tags
                )
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_cluster_tags", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_cluster_config",
        description = """Returns the configuration for a single Kafka cluster as stored in the registry.
Includes: identifier, connectionString (bootstrap servers), tags, sslEnabled, saslEnabled, profiles.
This is the source of truth for cluster connectivity and security settings as configured in the registry."""
    )
    open fun kafkistry_get_registry_cluster_config(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        return try {
            val cluster = clustersRegistryService.getCluster(clusterIdentifier)
            val result = mapOf(
                "identifier" to cluster.identifier,
                "connectionString" to cluster.connectionString,
                "tags" to cluster.tags,
                "sslEnabled" to cluster.sslEnabled,
                "saslEnabled" to cluster.saslEnabled,
                "profiles" to cluster.profiles
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_cluster_config", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_clusters_all_statuses",
        description = """Returns the current connectivity and scraping state for all registered clusters.
Per cluster: identifier, stateType (VISIBLE, UNREACHABLE, DISABLED), ok (boolean, true when VISIBLE),
lastRefreshTime (epoch millis). Use to quickly check which clusters are healthy and reachable before
querying cluster-specific inspection data."""
    )
    open fun kafkistry_inspect_clusters_all_statuses(): String {
        return try {
            val result = clusterStatusService.clustersState().map { clusterState ->
                mapOf(
                    "identifier" to clusterState.cluster.identifier,
                    "stateType" to clusterState.clusterState.name,
                    "ok" to (clusterState.clusterState.name == "VISIBLE"),
                    "lastRefreshTime" to clusterState.lastRefreshTime
                )
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_clusters_all_statuses", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_cluster_state",
        description = """Returns the live state and broker topology for a specific Kafka cluster.
Includes: identifier, stateType, clusterInfo (nodeIds, controllerId, version, securityEnabled),
lastRefreshTime. The clusterInfo field will be null if the cluster is not currently reachable.
Returns null if the cluster is not registered."""
    )
    open fun kafkistry_inspect_cluster_state(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        return try {
            val clusterState = clusterStatusService.clustersState()
                .firstOrNull { it.cluster.identifier == clusterIdentifier }
                ?: return toMcpJson(null)
            val result = mapOf(
                "identifier" to clusterIdentifier,
                "stateType" to clusterState.clusterState.name,
                "clusterInfo" to clusterState.clusterInfo?.let { info ->
                    mapOf(
                        "nodeIds" to info.nodeIds,
                        "controllerId" to info.controllerId,
                        "version" to info.clusterVersion?.toString(),
                        "securityEnabled" to info.securityEnabled
                    )
                },
                "lastRefreshTime" to clusterState.lastRefreshTime
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_cluster_state", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_cluster_stats",
        description = """Returns broker count statistics for a specific Kafka cluster.
Includes: identifier, brokerCount (total registered brokers), onlineBrokerCount (currently reachable).
A difference between brokerCount and onlineBrokerCount indicates one or more brokers are offline.
Returns null if the cluster is not registered or cluster info is unavailable."""
    )
    open fun kafkistry_inspect_cluster_stats(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        return try {
            val clusterState = clusterStatusService.clustersState()
                .firstOrNull { it.cluster.identifier == clusterIdentifier }
                ?: return toMcpJson(null)
            val clusterInfo = clusterState.clusterInfo ?: return toMcpJson(null)
            val result = mapOf(
                "identifier" to clusterIdentifier,
                "brokerCount" to clusterInfo.nodeIds.size,
                "onlineBrokerCount" to clusterInfo.onlineNodeIds.size
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_cluster_stats", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_cluster_disk_usage",
        description = """Returns aggregated disk usage statistics for all topics on a cluster (no per-topic breakdown).
Includes: identifier, combined (totalCapacityBytes, usedBytes, freeBytes, possibleUsedBytes),
worstCurrentUsageLevel (LOW/MEDIUM/HIGH/VERY_HIGH/CRITICAL), worstPossibleUsageLevel, errors.
Use kafkistry_inspect_cluster_topic_disk_usage to drill down into a specific topic's contribution."""
    )
    open fun kafkistry_inspect_cluster_disk_usage(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        return try {
            val diskUsage = clusterResourcesAnalyzer.clusterDiskUsage(clusterIdentifier)
            val result = mapOf(
                "identifier" to clusterIdentifier,
                "combined" to diskUsage.combined,
                "worstCurrentUsageLevel" to diskUsage.worstCurrentUsageLevel,
                "worstPossibleUsageLevel" to diskUsage.worstPossibleUsageLevel,
                "errors" to diskUsage.errors
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_cluster_disk_usage", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_cluster_topic_disk_usage",
        description = """Returns disk usage details for a specific topic on a specific cluster.
Includes: identifier, topicName, and diskUsage containing combined (total bytes on disk),
perBroker (per-broker breakdown), retentionBoundedSizeBytes (estimated max size given retention config),
configuredReplicationFactor. Returns null if the cluster is not registered."""
    )
    open fun kafkistry_inspect_cluster_topic_disk_usage(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        return try {
            val diskUsage = clusterResourcesAnalyzer.clusterDiskUsage(clusterIdentifier)
            val topicUsage = diskUsage.topicDiskUsages[topicName] ?: return toMcpJson(null)
            val result = mapOf(
                "identifier" to clusterIdentifier,
                "topicName" to topicName,
                "diskUsage" to topicUsage
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_cluster_topic_disk_usage", ex)
        }
    }
}
