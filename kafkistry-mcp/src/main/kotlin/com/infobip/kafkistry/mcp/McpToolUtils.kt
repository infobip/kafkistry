package com.infobip.kafkistry.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.infobip.kafkistry.utils.deepToString

internal val MCP_OM: ObjectMapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

internal fun toMcpJson(value: Any?): String = MCP_OM.writeValueAsString(value)

internal fun mcpErrorJson(toolName: String, ex: Exception): String = toMcpJson(
    mapOf("error" to "$toolName failed unexpectedly", "exception" to ex.deepToString())
)
