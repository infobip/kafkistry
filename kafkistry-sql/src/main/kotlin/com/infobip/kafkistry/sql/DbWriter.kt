package com.infobip.kafkistry.sql

import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.background.BackgroundJobKey
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

    private val writeJobKey = BackgroundJobKey(javaClass.name, "sql-write", "Refresh SQL data - DB write")

    @Scheduled(fixedDelay = 10_000)
    fun writeAll() {
        //get the data
        val generatedData = sqlDataSources.orElse(emptyList()).mapNotNull {
            val issueKey = BackgroundJobKey(javaClass.name,"sql-" + it.javaClass.simpleName, "Collect SQL data for ${it.javaClass.name}")
            issuesRegistry.computeCapturingException(issueKey) {
                it.supplyEntities()
            }
        }
        //write data to DB
        issuesRegistry.doCapturingException(writeJobKey) {
            repository.updateAllLists(generatedData)
        }
    }
}