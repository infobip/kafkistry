package com.infobip.kafkistry.webapp.url

class SqlUrls(base: String) : BaseUrls() {

    companion object {
        const val SQL = "/sql"
        const val SQL_QUERY_RESULT = "/query-result"
    }

    private val showSqlPage = Url(base, listOf("query"))
    private val showSqlQueryResult = Url("$base$SQL_QUERY_RESULT", listOf("query"))

    @JvmOverloads
    fun showSqlPage(query: String? = null) = showSqlPage.render("query" to query)

    fun showSqlQueryResult(query: String) = showSqlQueryResult.render("query" to query)
}