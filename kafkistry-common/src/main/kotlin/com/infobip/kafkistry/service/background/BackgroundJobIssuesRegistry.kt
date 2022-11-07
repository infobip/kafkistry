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

    fun doCapturingException(job: BackgroundJob, clearAfter: Long = 0, execution: () -> Unit) {
        execCapturingException(job, clearAfter, execution)
    }

    fun <T> execCapturingException(job: BackgroundJob, clearAfter: Long = 0, execution: () -> T): Result<T> {
        val jobExecution = newExecution(job)
        return try {
            val result = execution()
            val prevIssue = issues[job.key]
            if (prevIssue != null && System.currentTimeMillis() >= prevIssue.timestamp + clearAfter) {
                jobExecution.succeeded()
            } else {
                jobExecution.succeededSoftly()
            }
            Result.success(result)
        } catch (ex: Throwable) {
            jobExecution.failed(ex.deepToString())
            log.error("{} failed with exception", job, ex)
            Result.failure(ex)
        }
    }

    fun newExecution(job: BackgroundJob) = JobExecution(job, System.currentTimeMillis())

    inner class JobExecution(
        private val job: BackgroundJob,
        private val startTime: Long,
    ) {

        private fun durationMs() = System.currentTimeMillis() - startTime

        fun succeededSoftly() {
            lastSuccesses[job.key] = job.toSuccessStatus(startTime, durationMs())
        }

        fun succeeded() {
            issues.remove(job.key)
            lastSuccesses[job.key] = job.toSuccessStatus(startTime, durationMs())
        }

        fun failed(message: String) {
            issues[job.key] = BackgroundJobIssue(job, message, startTime, durationMs())
            lastSuccesses.remove(job.key)
        }

    }

    fun clearJob(job: BackgroundJob) {
        issues.remove(job.key)
        lastSuccesses.remove(job.key)
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
        job = job, timestamp = timestamp, lastSuccess = false, lastFailureMessage = failureMessage, lastDurationMs = durationMs,
    )

    private fun BackgroundJob.toSuccessStatus(timestamp: Long, durationMs: Long) = BackgroundJobStatus(
        job = this, timestamp = timestamp, lastSuccess = true, lastFailureMessage = null, lastDurationMs = durationMs,
    )
}