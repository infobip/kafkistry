package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.sql.SQLRepository
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistryAnalysisTools(
    private val sqlRepository: SQLRepository,
) {

    @McpTool(
        name = "kafkistry_execute_sql_query",
        description = """Executes a SQL query against Kafkistry's in-memory SQLite metadata database.
This database contains tables for topics, partitions, consumer groups, ACLs, quotas,
cluster nodes, and related metadata. SQL SELECT queries can be used to perform
cross-cluster aggregations, filtering, and analysis.

IMPORTANT — before writing a query, always call:
  kafkistry_get_sql_table_columns   — full schema (table names, column names, types, keys)
  kafkistry_get_sql_query_examples  — example queries showing correct join patterns
  kafkistry_get_sql_table_stats     — row counts to avoid full-scan joins on large tables

Table names are capitalized (e.g., "Topics", not "topics"). Join tables use compound FK columns.
Only SELECT queries are supported; write operations are not permitted."""
    )
    open fun kafkistry_execute_sql_query(
        @McpToolParam(required = true, description = "SQL SELECT query string") sql: String,
    ): String {
        return try {
            val result = sqlRepository.query(sql)
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_execute_sql_query", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_sql_table_names",
        description = """Returns just the table names in the Kafkistry SQLite metadata database.
Lightweight first step to discover which tables exist before requesting full column details.
Much cheaper than kafkistry_get_sql_table_columns and useful when you only need to verify a table name.

Typical workflow:
  1. kafkistry_get_sql_table_names — discover available tables (cheap)
  2. kafkistry_get_sql_table_columns with specific tables — load columns only for relevant tables
  3. kafkistry_get_sql_query_examples — find a working example query
  4. kafkistry_execute_sql_query — run the adapted query"""
    )
    open fun kafkistry_get_sql_table_names(): String {
        return try {
            val result = sqlRepository.tableNames
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_sql_table_names", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_sql_table_columns",
        description = """Returns the full schema of the Kafkistry SQLite metadata database — all tables, columns, types, and key relationships.
ALWAYS call this before writing any SQL query. It eliminates schema-discovery queries and prevents failures.

To reduce response size, pass a comma-separated list of table names via the tables parameter
to retrieve columns only for the tables you need.

Each TableInfo entry contains: name (exact table name), joinTable (true for many-to-many join tables),
and columns: name (case-sensitive), type, primaryKey, joinKey (use in JOIN conditions), referenceKey.

Foreign-key naming convention: join tables reference their parent via columns named
"{ParentTable}_{columnName}" (e.g., Topics_ActualConfigs references Topics via "Topic_cluster" + "Topic_topic")."""
    )
    open fun kafkistry_get_sql_table_columns(
        @McpToolParam(required = false, description = "Optional comma-separated list of table names to filter the response. When omitted, all tables are returned.") tables: String?,
    ): String {
        return try {
            val all = sqlRepository.tableColumns
            val result = if (tables.isNullOrBlank()) {
                all
            } else {
                val requested = tables.split(",").map { it.trim() }.toSet()
                all.filter { it.name in requested }
            }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_sql_table_columns", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_sql_query_examples",
        description = """Returns a curated set of example SQL queries for the Kafkistry SQLite metadata database.
These examples demonstrate correct table names, column names, join patterns, and filter conditions
for the most common analytical use cases. Use them as templates rather than writing queries from scratch.

Each QueryExample contains: title (short description) and sql (ready-to-run SELECT statement).
Examples show correct compound FK join patterns, grouping by use case (disk usage, compression,
consumer lag, ACL analysis, etc.), and use indexed columns in WHERE/JOIN conditions."""
    )
    open fun kafkistry_get_sql_query_examples(): String {
        return try {
            val result = sqlRepository.queryExamples
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_sql_query_examples", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_sql_table_stats",
        description = """Returns the current row counts for all tables in the Kafkistry SQLite metadata database.
Each TableStats entry contains: name (table name), count (current number of rows).
Use to: decide whether to paginate (large tables >10,000 rows should use LIMIT/OFFSET),
detect unpopulated tables (count=0 on ConsumerGroups means no consumer groups scraped yet),
prioritize queries, and confirm data freshness. If counts look stale, check kafkistry_get_scraping_status."""
    )
    open fun kafkistry_get_sql_table_stats(): String {
        return try {
            val result = sqlRepository.tableStats
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_sql_table_stats", ex)
        }
    }
}
