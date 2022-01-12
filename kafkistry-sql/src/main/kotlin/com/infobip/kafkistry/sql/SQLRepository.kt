package com.infobip.kafkistry.sql

interface SQLRepository : AutoCloseable {

    val tableColumns: List<TableInfo>

    val queryExamples: List<QueryExample>

    fun updateAllLists(objectLists: List<List<Any>>)

    fun query(sql: String): QueryResult

    override fun close()
}

