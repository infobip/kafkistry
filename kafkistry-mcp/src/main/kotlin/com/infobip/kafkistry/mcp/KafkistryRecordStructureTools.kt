package com.infobip.kafkistry.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.infobip.kafkistry.recordstructure.RecordStructureAnalyzer
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
open class KafkistryRecordStructureTools(
    private val recordStructureAnalyzer: RecordStructureAnalyzer,
) {

    @McpTool(
        name = "kafkistry_get_topic_payload_type",
        description = """Returns the inferred payload type (JSON, NULL, UNKNOWN) for a topic on each cluster where samples exist.
Useful as a lightweight triage step: check whether a topic carries JSON payloads before fetching the full
record structure. Returns a map of clusterIdentifier to PayloadType, or "null" if no samples exist for the topic."""
    )
    open fun kafkistry_get_topic_payload_type(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        val result = recordStructureAnalyzer.allTopicsTypes()[topicName] ?: return "null"
        return OM.writeValueAsString(result)
    }

    @McpTool(
        name = "kafkistry_get_topic_record_structure",
        description = """Returns the full inferred record structure for a topic, merged across all clusters.
Includes: payloadType, size statistics (per time window), headerFields, jsonFields (hierarchical field tree
with types, nullability, and sample values). Returns "null" if no samples have been collected for the topic."""
    )
    open fun kafkistry_get_topic_record_structure(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
    ): String {
        val result = recordStructureAnalyzer.getStructure(topicName) ?: return "null"
        return OM.writeValueAsString(result)
    }

    @McpTool(
        name = "kafkistry_get_topic_cluster_record_structure",
        description = """Returns the inferred record structure for a topic on a specific cluster.
Includes: payloadType, size statistics (per time window), headerFields, jsonFields (hierarchical field tree
with types, nullability, and sample values). Useful for comparing schemas between environments.
Returns "null" if no samples have been collected for the topic on that cluster."""
    )
    open fun kafkistry_get_topic_cluster_record_structure(
        @McpToolParam(required = true, description = "Topic name") topicName: String,
        @McpToolParam(required = true, description = "Cluster identifier") clusterIdentifier: String,
    ): String {
        val result = recordStructureAnalyzer.getStructure(clusterIdentifier, topicName) ?: return "null"
        return OM.writeValueAsString(result)
    }

    companion object {
        private val OM = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }
}
