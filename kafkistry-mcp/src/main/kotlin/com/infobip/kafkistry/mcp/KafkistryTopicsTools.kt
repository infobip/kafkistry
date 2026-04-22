package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.service.resources.TopicResourcesAnalyzer
import com.infobip.kafkistry.service.topic.OperationSuggestionService
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistryTopicsTools(
    private val topicsRegistryService: TopicsRegistryService,
    private val topicsInspectionService: TopicsInspectionService,
    private val suggestionService: OperationSuggestionService,
    private val topicResourcesAnalyzer: TopicResourcesAnalyzer,
) {

    @McpTool(
        name = "kafkistry_list_registry_topic_names",
        description = """Lists all topic names registered in the Kafkistry registry (git-backed source of truth).
Returns only bare topic names without any associated metadata, configuration, or cluster state.
Useful as a starting point to enumerate topics before fetching detailed information.
The registry represents the desired/expected state. Supports pagination via limit and offset parameters."""
    )
    open fun kafkistry_list_registry_topic_names(
        @McpToolParam(required = false, description = "Maximum number of names to return") limit: Int?,
        @McpToolParam(required = false, description = "Number of names to skip for pagination (default 0)") offset: Int?,
    ): String {
        return try {
            val allTopics = topicsRegistryService.listTopics()
            val off = offset ?: 0
            val result = applyPagination(allTopics, limit, off).map { it.name }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_registry_topic_names", ex)
        }
    }

    @McpTool(
        name = "kafkistry_list_registry_topics_summary",
        description = """Lists all topics from the registry with ownership and presence metadata.
Each entry includes: topic name, owner (team/service responsible), and presence type
(ALL_CLUSTERS, INCLUDED_CLUSTERS, EXCLUDED_CLUSTERS, or TAGGED_CLUSTERS).
Preferred starting point when browsing topics by ownership or cluster targeting strategy.
Supports pagination via limit and offset parameters."""
    )
    open fun kafkistry_list_registry_topics_summary(
        @McpToolParam(required = false, description = "Maximum number of topics to return") limit: Int?,
        @McpToolParam(required = false, description = "Number of topics to skip for pagination (default 0)") offset: Int?,
    ): String {
        return try {
            val allTopics = topicsRegistryService.listTopics()
            val off = offset ?: 0
            val result = applyPagination(allTopics, limit, off).map { topic ->
                mapOf(
                    "name" to topic.name,
                    "owner" to topic.owner,
                    "presenceType" to topic.presence.type.name
                )
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_registry_topics_summary", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_topic_info",
        description = """Returns basic metadata for a single topic from the registry, excluding configuration details.
Includes: name, owner, description, labels, producer, presence (cluster-targeting policy),
and resourceRequirements. Use when you need business context without loading configuration or cluster state."""
    )
    open fun kafkistry_get_registry_topic_info(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        return try {
            val desc = topicsRegistryService.getTopic(topicName)
            toMcpJson(desc)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_topic_info", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_topic_config",
        description = """Returns the full desired configuration for a topic as stored in the registry.
Includes: properties (partition count, replication factor), config (base Kafka config key-value map),
perClusterProperties, perClusterConfigOverrides, perTagProperties, perTagConfigOverrides.
This is the authoritative desired configuration."""
    )
    open fun kafkistry_get_registry_topic_config(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        return try {
            val desc = topicsRegistryService.getTopic(topicName)
            val result = mapOf(
                "name" to desc.name,
                "properties" to desc.properties,
                "config" to desc.config,
                "perClusterProperties" to desc.perClusterProperties,
                "perClusterConfigOverrides" to desc.perClusterConfigOverrides,
                "perTagProperties" to desc.perTagProperties,
                "perTagConfigOverrides" to desc.perTagConfigOverrides
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_topic_config", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_topic_presence",
        description = """Returns the presence (cluster-targeting) configuration for a topic from the registry.
Presence types: ALL_CLUSTERS, INCLUDED_CLUSTERS, EXCLUDED_CLUSTERS, TAGGED_CLUSTERS.
Useful for determining the intended cluster scope of a topic without loading full config or live state."""
    )
    open fun kafkistry_get_registry_topic_presence(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        return try {
            val desc = topicsRegistryService.getTopic(topicName)
            val result = mapOf(
                "name" to desc.name,
                "presenceType" to desc.presence.type.name,
                "kafkaClusterIdentifiers" to desc.presence.kafkaClusterIdentifiers,
                "tag" to desc.presence.tag
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_topic_presence", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic",
        description = """Returns the live inspection result for a single topic, combining registry expectations
with actual observed state on each cluster. Response includes: topicName, topicDescription,
aggStatusFlags (allOk, hasWarnings, hasErrors), and statusPerClusters with status type codes
(OK, MISSING, UNEXPECTED, WRONG_CONFIG, WRONG_PARTITION_COUNT, CONFIG_RULE_VIOLATION, etc.).
Optional clustersOnly parameter: comma-separated cluster identifiers to filter results."""
    )
    open fun kafkistry_inspect_topic(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = false, description = "Comma-separated cluster identifiers to filter by") clustersOnly: String?,
    ): String {
        return try {
            val topic = topicsInspectionService.inspectAllTopics()
                .firstOrNull { it.topicName == topicName }
                ?: return toMcpJson(null)

            val clusterIds = clustersOnly?.split(",")?.toSet()
            val clusters = if (clusterIds != null) {
                topic.statusPerClusters.filter { it.clusterIdentifier in clusterIds }
            } else {
                topic.statusPerClusters
            }

            val result = mapOf(
                "topicName" to topic.topicName,
                "topicDescription" to topic.topicDescription,
                "aggStatusFlags" to topic.aggStatusFlags,
                "statusPerClusters" to clusters.map { cluster ->
                    mapOf(
                        "clusterIdentifier" to cluster.clusterIdentifier,
                        "status" to mapOf("types" to cluster.status.types)
                    )
                }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_status",
        description = """Returns detailed inspection status flags and issue type codes for a topic across all clusters.
Response includes: aggStatusFlags, availableActions (CREATE, DELETE, ALTER_PARTITION_COUNT, ALTER_CONFIG),
and per-cluster: flags, types (status codes), exists, lastRefreshTime.
Best source for understanding what is wrong with a topic and what actions are available for remediation.
Optional clustersOnly: comma-separated cluster identifiers."""
    )
    open fun kafkistry_inspect_topic_status(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = false, description = "Comma-separated cluster identifiers to restrict results to") clustersOnly: String?,
    ): String {
        return try {
            val topicStatus = topicsInspectionService.inspectTopic(topicName)
            val clusterIds = clustersOnly?.split(",")?.toSet()
            val clusters = if (clusterIds != null) topicStatus.statusPerClusters.filter { it.clusterIdentifier in clusterIds } else topicStatus.statusPerClusters
            val result = mapOf(
                "topicName" to topicStatus.topicName,
                "aggStatusFlags" to topicStatus.aggStatusFlags,
                "availableActions" to topicStatus.availableActions,
                "clusterStatuses" to clusters.map {
                    mapOf(
                        "clusterIdentifier" to it.clusterIdentifier,
                        "flags" to it.status.flags,
                        "types" to it.status.types.map { type -> type.name },
                        "exists" to it.status.exists,
                        "lastRefreshTime" to it.lastRefreshTime
                    )
                }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_status", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_config",
        description = """Returns the configuration diff between desired (registry) and actual (cluster) state for a topic.
Per cluster: wrongValues (actual differs from desired), updateValues (scheduled to change),
ruleViolations (violations of Kafkistry validation rules), currentConfigRuleViolations.
Primary source for diagnosing WRONG_CONFIG status issues - shows exact config keys and values that are out of alignment.
Optional clustersOnly: comma-separated cluster identifiers."""
    )
    open fun kafkistry_inspect_topic_config(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = false, description = "Comma-separated cluster identifiers to restrict results to") clustersOnly: String?,
    ): String {
        return try {
            val topicStatus = topicsInspectionService.inspectTopic(topicName)
            val clusterIds = clustersOnly?.split(",")?.toSet()
            val clusters = if (clusterIds != null) topicStatus.statusPerClusters.filter { it.clusterIdentifier in clusterIds } else topicStatus.statusPerClusters
            val result = mapOf(
                "topicName" to topicStatus.topicName,
                "clusterConfigIssues" to clusters.map {
                    mapOf(
                        "clusterIdentifier" to it.clusterIdentifier,
                        "wrongValues" to (it.status.wrongValues ?: emptyList<Any>()),
                        "updateValues" to (it.status.updateValues ?: emptyList<Any>()),
                        "ruleViolations" to (it.status.ruleViolations ?: emptyList<Any>()),
                        "currentConfigRuleViolations" to (it.status.currentConfigRuleViolations ?: emptyList<Any>())
                    )
                }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_config", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_acls",
        description = """Returns the deduplicated set of ACL rules that affect this topic across all clusters.
Each rule includes: principal, resource (type and name/pattern), operation (READ, WRITE, DESCRIBE, etc.),
permission (ALLOW or DENY), and host. Rules are aggregated across all clusters and deduplicated.
This is from live cluster inspection state, not the registry definition."""
    )
    open fun kafkistry_inspect_topic_acls(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        return try {
            val topicStatus = topicsInspectionService.inspectTopic(topicName)
            val allAclRules = topicStatus.statusPerClusters
                .flatMap { it.status.affectingAclRules }
                .distinctBy { it.principal to it.resource to it.operation }
            val result = mapOf(
                "topicName" to topicStatus.topicName,
                "affectingAclRules" to allAclRules
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_acls", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_assignments",
        description = """Returns live partition assignment details and rebalancing state for a topic across all clusters.
Per cluster: existingTopicInfo (partition count, replication factor, per-partition assignment map, leader, ISR, offline replicas,
current Kafka config), assignmentsDisbalance (partition/leader distribution analysis), currentReAssignments (in-progress reassignments).
Useful for diagnosing partition distribution problems and monitoring ongoing reassignment operations.
Optional clustersOnly: comma-separated cluster identifiers."""
    )
    open fun kafkistry_inspect_topic_assignments(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = false, description = "Comma-separated cluster identifiers to restrict results to") clustersOnly: String?,
    ): String {
        return try {
            val topicStatus = topicsInspectionService.inspectTopic(topicName)
            val clusterIds = clustersOnly?.split(",")?.toSet()
            val result = mapOf(
                "topicName" to topicStatus.topicName,
                "clusterAssignments" to topicStatus.statusPerClusters
                    .filter { it.existingTopicInfo != null }
                    .filter { clusterIds == null || it.clusterIdentifier in clusterIds }
                    .associate { cluster ->
                        cluster.clusterIdentifier to mapOf(
                            "existingTopicInfo" to cluster.existingTopicInfo!!,
                            "assignmentsDisbalance" to cluster.status.assignmentsDisbalance,
                            "currentReAssignments" to cluster.currentReAssignments
                        )
                    }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_assignments", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_resources",
        description = """Returns resource requirements and actual measured resource usage for a topic across clusters.
Includes resourceRequirements (declared requirements from registry: throughput, message size, retention, replication)
and per-cluster actual usage: requiredRetentionBytes, requiredProduceBytesPerSec, requiredDiskBytesPerSec,
usageLevel (LOW, MEDIUM, HIGH, VERY_HIGH). Optional clustersOnly: comma-separated cluster identifiers."""
    )
    open fun kafkistry_inspect_topic_resources(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = false, description = "Comma-separated cluster identifiers to restrict results to") clustersOnly: String?,
    ): String {
        return try {
            val topicStatus = topicsInspectionService.inspectTopic(topicName)
            val clusterIds = clustersOnly?.split(",")?.toSet()
            val result = mapOf(
                "topicName" to topicStatus.topicName,
                "resourceRequirements" to topicStatus.topicDescription?.resourceRequirements,
                "clusterResources" to topicStatus.statusPerClusters
                    .filter { it.resourceRequiredUsages.value != null }
                    .filter { clusterIds == null || it.clusterIdentifier in clusterIds }
                    .associate { it.clusterIdentifier to it.resourceRequiredUsages.value!! }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_resources", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_topic_disk_usage",
        description = """Returns detailed disk usage analysis for a specific topic on a specific cluster.
Includes actual bytes on disk per broker, total bytes across all brokers (accounting for replication),
retention-bounded size estimate (projected maximum storage footprint), comparison with declared
resource requirements, and usage level classification. Most detailed source for understanding
a single topic's storage footprint on a given cluster."""
    )
    open fun kafkistry_inspect_topic_disk_usage(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        return try {
            val result = topicResourcesAnalyzer.topicOnClusterDiskUsage(topicName, clusterIdentifier)
            val filteredClusterDiskUsage = result.clusterDiskUsage.copy(
                topicDiskUsages = result.clusterDiskUsage.topicDiskUsages.filterKeys { it == topicName }
            )
            toMcpJson(result.copy(clusterDiskUsage = filteredClusterDiskUsage))
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_topic_disk_usage", ex)
        }
    }

    @McpTool(
        name = "kafkistry_suggest_topic_import",
        description = """Generates a suggested registry configuration for importing a topic that exists on a cluster
but is not yet registered in the Kafkistry registry (status type: UNEXPECTED).
The suggestion includes inferred properties, configuration, presence, and placeholder values
for owner/description. Returns a starting point that should be reviewed before being submitted
to create the official registry entry. Does not automatically register the topic."""
    )
    open fun kafkistry_suggest_topic_import(
        @McpToolParam(required = true, description = "Topic name to generate an import suggestion for") topicName: String,
    ): String {
        return try {
            val result = suggestionService.suggestTopicImport(topicName)
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_suggest_topic_import", ex)
        }
    }

    private fun <T> applyPagination(items: List<T>, limit: Int?, offset: Int): List<T> {
        val end = if (limit != null) offset + limit else items.size
        return items.drop(offset).take(end - offset)
    }
}
