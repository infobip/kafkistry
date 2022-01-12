package com.infobip.kafkistry.sql

class RollingSQLRepository(
    private val primary: SQLRepository,
    private val secondary: SQLRepository,
) : SQLRepository {

    private enum class ActiveRepository {
        PRIMARY, SECONDARY
    }

    @Volatile
    private var active: ActiveRepository = ActiveRepository.PRIMARY

    private fun current(): SQLRepository = when (active) {
        ActiveRepository.PRIMARY -> primary
        ActiveRepository.SECONDARY -> secondary
    }

    private fun next(): SQLRepository = when (active) {
        ActiveRepository.PRIMARY -> secondary
        ActiveRepository.SECONDARY -> primary
    }

    private fun swap(){
        active = when (active) {
            ActiveRepository.PRIMARY -> ActiveRepository.SECONDARY
            ActiveRepository.SECONDARY -> ActiveRepository.PRIMARY
        }
    }

    override val tableColumns: List<TableInfo> = primary.tableColumns
    override val queryExamples: List<QueryExample> = primary.queryExamples

    override fun updateAllLists(objectLists: List<List<Any>>) {
        next().updateAllLists(objectLists)
        swap()
    }

    override fun query(sql: String): QueryResult = current().query(sql)

    override fun close() {
        primary.close()
        secondary.close()
    }
}