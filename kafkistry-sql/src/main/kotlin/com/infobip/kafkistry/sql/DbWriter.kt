package com.infobip.kafkistry.sql

import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConditionalOnProperty("app.sql.enabled", matchIfMissing = true)
class DbWriter(
    private val issuesRegistry: BackgroundJobIssuesRegistry,
    private val repository: SQLRepository,
    private val sqlDataSources: Optional<List<SqlDataSource<*>>>,
) {

    @Scheduled(fixedDelay = 10_000)
    fun writeAll() {
        //get the data
        val generatedData = sqlDataSources.orElse(emptyList()).mapNotNull {
            val issueKey = "sql-" + it.javaClass.name
            val jobName = "Collect SQL data for ${it.javaClass.simpleName}"
            issuesRegistry.computeCapturingException(issueKey, jobName) {
                it.supplyEntities()
            }
        }
        //write data to DB
        issuesRegistry.doCapturingException("sql", "Refresh SQL data") {
            repository.updateAllLists(generatedData)
        }
    }
}