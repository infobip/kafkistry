package com.infobip.kafkistry.api

import com.infobip.kafkistry.sql.*
import com.infobip.kafkistry.service.KafkistrySQLException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/sql")
@ConditionalOnProperty("app.sql.enabled", matchIfMissing = true)
class SqlSearchApi(
    private val sqlRepository: SQLRepository,
    private val sqlClickHouse: ClickHouseSqlAdapter,
) {

    @RequestMapping("/query")
    fun executeSql(
        @RequestParam("sql") sql: String
    ): QueryResult = sqlRepository.query(sql)

    @RequestMapping("/table-columns")
    fun tableColumns(): List<TableInfo> = sqlRepository.tableColumns

    @RequestMapping("/query-examples")
    fun queryExamples(): List<QueryExample> = sqlRepository.queryExamples

    @RequestMapping("/click-house")
    fun queryClickHouse(
        @RequestParam(name = "query", required = false) query: String?,
        @RequestBody(required = false) body: String?,
    ): ClickHouseResponse = sqlClickHouse.query(
        query = query?.takeIf { it.trim().isNotEmpty() }
            ?: body?.takeIf { it.trim().isNotEmpty() }
            ?: throw KafkistrySQLException("Request needs to either have 'query' parameter or query as HTTP POST body")
    )

}