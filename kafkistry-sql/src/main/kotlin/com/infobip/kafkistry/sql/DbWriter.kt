package com.infobip.kafkistry.sql

import com.infobip.kafkistry.service.background.BackgroundJob
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

    private val writeJob = BackgroundJob.of(
        category = "sql", phase = "db-write", description = "Refresh SQL data - DB write of collected data",
    )

    @Scheduled(fixedDelay = 10_000)
    fun writeAll() {
        //get the data
        val generatedData = sqlDataSources.orElse(emptyList()).mapNotNull {
            val simpleName = it.javaClass.simpleName
                .removeSuffix("DataSource")
                .removeSuffix("DataSource")
            val sourceJob = BackgroundJob.of(
                jobClass = it.javaClass.name, category = "sql", phase = simpleName,
                description = "Collect SQL data for $simpleName",
            )
            issuesRegistry.computeCapturingException(sourceJob) {
                it.supplyEntities()
            }
        }
        //write data to DB
        issuesRegistry.doCapturingException(writeJob) {
            repository.updateAllLists(generatedData)
        }
    }
}