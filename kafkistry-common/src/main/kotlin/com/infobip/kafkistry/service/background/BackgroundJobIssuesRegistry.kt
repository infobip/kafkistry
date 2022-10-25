package com.infobip.kafkistry.service.background

import com.infobip.kafkistry.utils.deepToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class BackgroundJobIssuesRegistry {

    private val log = LoggerFactory.getLogger(BackgroundJobIssuesRegistry::class.java)

    private val issues = ConcurrentHashMap<BackgroundJobKey, BackgroundJobIssue>()
    private val lastSuccesses = ConcurrentHashMap<BackgroundJobKey, Long>()

    fun doCapturingException(key: String, jobName: String, clearAfter: Long = 0, job: () -> Unit): Boolean {
        return doCapturingException(BackgroundJobKey(key, jobName), clearAfter, job)
    }

    fun doCapturingException(key: BackgroundJobKey, clearAfter: Long = 0, job: () -> Unit): Boolean {
        return computeCapturingException(key, clearAfter, job) != null
    }

    fun <T> computeCapturingException(key: BackgroundJobKey, clearAfter: Long = 0, job: () -> T): T? {
        return try {
            val result = job()
            val prevIssue = issues[key]
            if (prevIssue != null && System.currentTimeMillis() >= prevIssue.timestamp + clearAfter) {
                clearIssue(key)
            } else {
                reportSuccess(key)
            }
            result
        } catch (ex: Exception) {
            reportIssue(key, ex.deepToString())
            log.error("{} failed with exception", key, ex)
            null
        }
    }

    fun reportIssue(key: BackgroundJobKey, failMessage: String) {
        issues[key] = BackgroundJobIssue(key, failMessage, System.currentTimeMillis())
    }

    fun clearIssue(key: BackgroundJobKey) {
        issues.remove(key)
        reportSuccess(key)
    }

    private fun reportSuccess(key: BackgroundJobKey) {
        lastSuccesses[key] = System.currentTimeMillis()
    }

    fun currentIssues(): List<BackgroundJobIssue> {
        return issues.values.sortedBy { it.key.jobName }
    }

    fun currentGroupedIssues(): List<BackgroundJobIssuesGroup> {
        return currentIssues()
            .groupBy { it.key.cluster ?: it.key.type }
            .map { (group, issues) ->
                BackgroundJobIssuesGroup(group, issues)
            }
    }
}