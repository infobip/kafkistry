package com.infobip.kafkistry.sql

interface SQLRepository : AutoCloseable {

    val tableColumns: List<TableInfo>

    val queryExamples: List<QueryExample>

    val tableStats: List<TableStats>

    fun updateAllLists(objectLists: List<List<Any>>)

    fun query(sql: String): QueryResult

    override fun close()
}

