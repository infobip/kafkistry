package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consumers.ConsumersService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistryConsumerTools(
    private val consumersService: ConsumersService,
    private val clustersRegistryService: ClustersRegistryService,
) {

    @McpTool(
        name = "kafkistry_inspect_consumer_group_names",
        description = """Returns the consumer group IDs observed on each cluster.
Response is a map from cluster identifier to a list of consumer group IDs active on that cluster.
Represents live observed state from the most recent cluster state scrape (not from registry).
Optional clusterIdentifier parameter restricts the result to a single cluster.
Use to discover what consumer groups exist before fetching detailed information."""
    )
    open fun kafkistry_inspect_consumer_group_names(
        @McpToolParam(required = false, description = "Optional cluster identifier to filter results to a single cluster") clusterIdentifier: String?,
    ): String {
        return try {
            val consumersData = consumersService.allConsumersData()
            val filtered = if (clusterIdentifier != null) {
                consumersData.clustersGroups.filter { it.clusterIdentifier == clusterIdentifier }
            } else {
                consumersData.clustersGroups
            }
            val result = filtered
                .groupBy { it.clusterIdentifier }
                .mapValues { (_, groups) -> groups.map { it.consumerGroup.groupId } }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumer_group_names", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumer_group_summary",
        description = """Returns a high-level summary of a consumer group on a specific cluster.
Includes: groupId, clusterIdentifier, status (Stable, Empty, PreparingRebalance, CompletingRebalance, Dead),
lagAmount (total unconsumed messages, may be null), lagStatus (OK, WARNING, CRITICAL),
partitionAssignor (range, roundrobin, sticky, cooperative-sticky), topics (list of topic names consumed).
Returns null if the group is not found on this cluster."""
    )
    open fun kafkistry_inspect_consumer_group_summary(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
        @McpToolParam(required = true, description = "Consumer group ID") consumerGroupId: String,
    ): String {
        return try {
            val group = consumersService.consumerGroup(clusterIdentifier, consumerGroupId)
                ?: return toMcpJson(null)
            val result = mapOf(
                "groupId" to group.groupId,
                "clusterIdentifier" to clusterIdentifier,
                "status" to group.status.name,
                "lagAmount" to group.lag.amount,
                "lagStatus" to group.lag.status.name,
                "partitionAssignor" to group.partitionAssignor,
                "topics" to group.topicMembers.map { it.topicName }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumer_group_summary", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumer_group_lag",
        description = """Returns consumer group lag information broken down by topic, without partition-level detail.
Includes: groupId, clusterIdentifier, totalLag (sum across all partitions and topics, may be null),
lagStatus (severity classification), topicLags (map from topic name to total lag for that topic, values may be null).
Use to identify which specific topics within a consumer group are accumulating backlog.
Returns null if the group is not found on this cluster."""
    )
    open fun kafkistry_inspect_consumer_group_lag(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
        @McpToolParam(required = true, description = "Consumer group ID") consumerGroupId: String,
    ): String {
        return try {
            val group = consumersService.consumerGroup(clusterIdentifier, consumerGroupId)
                ?: return toMcpJson(null)
            val result = mapOf(
                "groupId" to group.groupId,
                "clusterIdentifier" to clusterIdentifier,
                "totalLag" to group.lag.amount,
                "lagStatus" to group.lag.status.name,
                "topicLags" to group.topicMembers.associate { it.topicName to it.lag.amount }
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumer_group_lag", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumer_group_topic_members",
        description = """Returns the full partition-level membership details for a consumer group on a cluster.
Contains a list of TopicMembers (one per topic consumed). Each entry includes: topicName, lag (aggregate),
partitionMembers (per-partition: partition index, memberId, clientId, host, offset, endOffset, lag).
Most detailed consumer group endpoint - provides full visibility into per-partition assignment and offset commits.
Can return a large response for groups consuming many topics. Returns null if the group is not found."""
    )
    open fun kafkistry_inspect_consumer_group_topic_members(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
        @McpToolParam(required = true, description = "Consumer group ID") consumerGroupId: String,
    ): String {
        return try {
            val group = consumersService.consumerGroup(clusterIdentifier, consumerGroupId)
                ?: return toMcpJson(null)
            val result = mapOf(
                "groupId" to group.groupId,
                "clusterIdentifier" to clusterIdentifier,
                "topicMembers" to group.topicMembers
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumer_group_topic_members", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumer_group_acls",
        description = """Returns the ACL rules that affect a specific consumer group on a cluster.
Includes ACL rules with resource type GROUP and a name/pattern matching this consumer group's ID.
Each rule includes: principal, resource (type GROUP with group name or pattern), operation
(READ, DESCRIBE, DELETE), permission (ALLOW or DENY), host.
Derived from live cluster inspection state. Returns null if the group is not found."""
    )
    open fun kafkistry_inspect_consumer_group_acls(
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
        @McpToolParam(required = true, description = "Consumer group ID") consumerGroupId: String,
    ): String {
        return try {
            val group = consumersService.consumerGroup(clusterIdentifier, consumerGroupId)
                ?: return toMcpJson(null)
            val result = mapOf(
                "groupId" to group.groupId,
                "clusterIdentifier" to clusterIdentifier,
                "affectingAclRules" to group.affectingAclRules
            )
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumer_group_acls", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumers_by_topic",
        description = """Returns summaries of all consumer groups consuming a specific topic.
Each entry represents one consumer group on one cluster: groupId, clusterIdentifier, status,
lagAmount, lagStatus, partitionAssignor, topics (all topics the group consumes, not just the filtered one).
Optional clusterIdentifier restricts to a single cluster. When omitted, all registered clusters are queried.
Use kafkistry_inspect_consumers_by_topic_lag for a lag-focused view."""
    )
    open fun kafkistry_inspect_consumers_by_topic(
        @McpToolParam(required = true, description = "Topic name to filter by") topicName: String,
        @McpToolParam(required = false, description = "Optional cluster identifier to restrict results to a single cluster") clusterIdentifier: String?,
    ): String {
        return try {
            val clusterIds = if (clusterIdentifier != null) {
                listOf(clusterIdentifier)
            } else {
                clustersRegistryService.listClusters().map { it.identifier }
            }
            val result = clusterIds.flatMap { clusterId ->
                consumersService.clusterTopicConsumers(clusterId, topicName).map { group ->
                    mapOf(
                        "groupId" to group.groupId,
                        "clusterIdentifier" to clusterId,
                        "status" to group.status.name,
                        "lagAmount" to group.lag.amount,
                        "lagStatus" to group.lag.status.name,
                        "partitionAssignor" to group.partitionAssignor,
                        "topics" to group.topicMembers.map { it.topicName }
                    )
                }
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumers_by_topic", ex)
        }
    }

    @McpTool(
        name = "kafkistry_inspect_consumers_by_topic_lag",
        description = """Returns lag information for all consumer groups consuming a specific topic, across clusters.
Optimized for finding which consumer group has the biggest lag on a given topic.
Each entry: groupId, clusterIdentifier, totalLag (across ALL topics this group consumes, may be null),
lagStatus, topicLag (lag for the requested topic only, may be null).
Optional clusterIdentifier restricts to a single cluster.
To find the consumer with biggest lag on a topic, sort results by topicLag descending."""
    )
    open fun kafkistry_inspect_consumers_by_topic_lag(
        @McpToolParam(required = true, description = "Topic name to filter by") topicName: String,
        @McpToolParam(required = false, description = "Optional cluster identifier to restrict results to a single cluster") clusterIdentifier: String?,
    ): String {
        return try {
            val clusterIds = if (clusterIdentifier != null) {
                listOf(clusterIdentifier)
            } else {
                clustersRegistryService.listClusters().map { it.identifier }
            }
            val result = clusterIds.flatMap { clusterId ->
                consumersService.clusterTopicConsumers(clusterId, topicName).map { group ->
                    val topicMember = group.topicMembers.find { it.topicName == topicName }
                    mapOf(
                        "groupId" to group.groupId,
                        "clusterIdentifier" to clusterId,
                        "totalLag" to group.lag.amount,
                        "lagStatus" to group.lag.status.name,
                        "topicLag" to topicMember?.lag?.amount
                    )
                }
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_inspect_consumers_by_topic_lag", ex)
        }
    }
}
