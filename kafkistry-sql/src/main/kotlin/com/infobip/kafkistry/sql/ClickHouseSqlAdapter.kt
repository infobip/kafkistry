package com.infobip.kafkistry.sql

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.sql.enabled", matchIfMissing = true)
class ClickHouseSqlAdapter(
        private val sqlRepository: SQLRepository
) {

    fun query(query: String): ClickHouseResponse {
        val sql = query.translateQuery()
        val result = execute(sql)
        return result.translateToClickHouseResponse()
    }

    private fun String.translateQuery(): String {
        //sqlite does not understand FORMAT JSON
        val sql = removeSuffix("FORMAT JSON")

        //translate table 'kr.SomeTable' to 'SomeTable', no concept of database namespaces in SQLite
        return sql.replace(" $DATABASE.", " ")
    }

    private fun execute(sql: String): QueryResult {
        return if (sql.isSchemaQuery()) {
            val rows = sqlRepository.tableColumns.flatMap { table ->
                table.columns.map { column ->
                    QueryResultRow(
                        values = listOf(DATABASE, table.name, column.name),
                        linkedResource = null,
                    )
                }
            }
            QueryResult(
                count = rows.size,
                totalCount = rows.size,
                columns = listOf(
                    ColumnMeta("database", "varchar"),
                    ColumnMeta("table", "varchar"),
                    ColumnMeta("name", "varchar"),
                ),
                columnLinkedType = emptyMap(),
                linkedCompoundTypes = emptyList(),
                rows = rows,
            )
        } else {
            sqlRepository.query(sql)
        }
    }

    private fun String.isSchemaQuery(): Boolean {
        return trim().equals(SCHEMA_QUERY, ignoreCase = true)
    }

    private fun QueryResult.translateToClickHouseResponse(): ClickHouseResponse {
        val data = rows.map { row ->
            columns.zip(row.values).associate { (column, value) -> column.name to value }
        }
        return ClickHouseResponse(
            meta = columns, data = data, rows = count
        )
    }

    companion object {
        const val DATABASE = "kr"
        const val SCHEMA_QUERY = "SELECT database, table, name FROM system.columns WHERE database NOT IN ('system')"
    }
}

data class ClickHouseResponse(
        val meta: List<ColumnMeta>,
        val data: List<Map<String, Any?>>,
        val rows: Int
)