package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.SqlSearchApi
import com.infobip.kafkistry.webapp.url.SqlUrls.Companion.SQL
import com.infobip.kafkistry.webapp.url.SqlUrls.Companion.SQL_QUERY_RESULT
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.util.*

@Controller
@RequestMapping("\${app.http.root-path}$SQL")
class SqlController(
    private val sqlSearchApiOpt: Optional<SqlSearchApi>,
) : BaseController() {

    private fun sqlSearchApi(): SqlSearchApi? = sqlSearchApiOpt.orElse(null)

    private fun disabled() = ModelAndView("featureDisabled", mapOf(
        "featureName" to "SQL",
        "propertyToEnable" to listOf("SQL_ENABLED", "app.sql.enabled")
    ))

    @GetMapping
    fun showSqlPage(
            @RequestParam(name = "query", required = false) query: String?
    ): ModelAndView {
        val sqlSearchApi = sqlSearchApi() ?: return disabled()
        val tableColumns = sqlSearchApi.tableColumns()
        val queryExamples = sqlSearchApi.queryExamples()
        return ModelAndView("sql/sql", mapOf(
                "tables" to tableColumns,
                "queryExamples" to queryExamples,
                "query" to query
        ))
    }

    @PostMapping(SQL_QUERY_RESULT)
    fun showSqlQueryResult(
            @RequestParam("query") query: String
    ): ModelAndView {
        val sqlSearchApi = sqlSearchApi() ?: return disabled()
        val queryResult = sqlSearchApi.executeSql(query)
        return ModelAndView("sql/queryResult", mapOf(
                "result" to queryResult
        ))
    }

}