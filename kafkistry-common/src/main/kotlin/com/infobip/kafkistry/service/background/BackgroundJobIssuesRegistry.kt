package com.infobip.kafkistry.service.background

import com.infobip.kafkistry.utils.deepToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class BackgroundJobIssuesRegistry {

    private val log = LoggerFactory.getLogger(BackgroundJobIssuesRegistry::class.java)

    private val issues = ConcurrentHashMap<BackgroundJobKey, BackgroundJobIssue>()
    private val lastSuccesses = ConcurrentHashMap<BackgroundJobKey, BackgroundJobStatus>()

    fun doCapturingException(job: BackgroundJob, clearAfter: Long = 0, execution: () -> Unit): Boolean {
        return computeCapturingException(job, clearAfter, execution) != null
    }

    fun <T> computeCapturingException(job: BackgroundJob, clearAfter: Long = 0, execution: () -> T): T? {
        return try {
            val result = execution()
            val prevIssue = issues[job.key]
            if (prevIssue != null && System.currentTimeMillis() >= prevIssue.timestamp + clearAfter) {
                clearIssue(job)
            } else {
                reportSuccess(job)
            }
            result
        } catch (ex: Exception) {
            reportIssue(job, ex.deepToString())
            log.error("{} failed with exception", job, ex)
            null
        }
    }

    fun reportIssue(job: BackgroundJob, failMessage: String) {
        issues[job.key] = BackgroundJobIssue(job, failMessage, System.currentTimeMillis())
    }

    fun clearIssue(job: BackgroundJob) {
        issues.remove(job.key)
        reportSuccess(job)
    }

    private fun reportSuccess(job: BackgroundJob) {
        lastSuccesses[job.key] = job.toSuccessStatus(System.currentTimeMillis())
    }

    fun currentIssues(): List<BackgroundJobIssue> {
        return issues.values.sortedBy { it.job.key.jobClass }
    }

    fun currentGroupedIssues(): List<BackgroundJobIssuesGroup> {
        return currentIssues()
            .groupBy { with(it.job.key) { cluster ?: category } }
            .map { (group, issues) ->
                BackgroundJobIssuesGroup(group, issues)
            }
    }

    fun currentStatuses(): List<BackgroundJobStatus> {
        return sequence {
            currentIssues().forEach {
                yield(it.toFailureStatus())
            }
            yieldAll(lastSuccesses.values)
        }.sortedBy { it.timestamp }.toList()
    }

    private fun BackgroundJobIssue.toFailureStatus() = BackgroundJobStatus(
        job = job, timestamp = timestamp, lastSuccess = false, lastFailureMessage = failureMessage,
    )

    private fun BackgroundJob.toSuccessStatus(timestamp: Long) = BackgroundJobStatus(
        job = this, timestamp = timestamp, lastSuccess = true, lastFailureMessage = null,
    )
}