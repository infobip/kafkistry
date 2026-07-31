package com.infobip.kafkistry.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.infobip.kafkistry.utils.deepToString
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

internal val MCP_OM: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
    .build()

internal fun toMcpJson(value: Any?): String = MCP_OM.writeValueAsString(value)

internal fun mcpErrorJson(toolName: String, ex: Exception): String = toMcpJson(
    mapOf("error" to "$toolName failed unexpectedly", "exception" to ex.deepToString())
)
