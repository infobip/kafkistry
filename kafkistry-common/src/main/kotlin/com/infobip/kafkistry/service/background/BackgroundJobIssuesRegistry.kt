package com.infobip.kafkistry.service.background

import com.infobip.kafkistry.utils.deepToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class BackgroundJobIssuesRegistry {

    private val log = LoggerFactory.getLogger(BackgroundJobIssuesRegistry::class.java)

    private val issues = ConcurrentHashMap<String, BackgroundJobIssue>()
    private val suppliers = ConcurrentHashMap<String, IssueSupplier>()

    private data class IssueSupplier(
            val jobName: String,
            val failureCauseSupplier: () -> String?
    )

    fun doCapturingException(key: String, jobName: String, clearAfter: Long = 0, job: () -> Unit): Boolean {
        return computeCapturingException(key, jobName, clearAfter, job) != null
    }

    fun <T> computeCapturingException(key: String, jobName: String, clearAfter: Long = 0, job: () -> T): T? {
        return try {
            val result = job()
            val prevIssue = issues[key]
            if (prevIssue != null && System.currentTimeMillis() >= prevIssue.timestamp + clearAfter) {
                clearIssue(key)
            }
            result
        } catch (ex: Exception) {
            reportIssue(key, jobName, ex.deepToString())
            log.error("Background job [{}] '{}' failed with exception", key, jobName, ex)
            null
        }
    }

    fun reportIssue(key: String, jobName: String, failMessage: String) {
        issues[key] = BackgroundJobIssue(jobName, failMessage, System.currentTimeMillis())
    }

    fun clearIssue(key: String) {
        issues.remove(key)
    }

    fun registerSupplier(key: String, jobName: String, failureCauseSupplier: () -> String?) {
        suppliers[key] = IssueSupplier(jobName, failureCauseSupplier)
    }

    fun unRegisterSupplier(key: String) {
        suppliers.remove(key)
    }

    fun currentIssues(): List<BackgroundJobIssue> {
        val reportedIssues = issues.values
        val suppliedIssues = suppliers.mapNotNull { (_, issueSupplier) ->
            issueSupplier.failureCauseSupplier()?.let {
                BackgroundJobIssue(issueSupplier.jobName, it, System.currentTimeMillis())
            }
        }
        return (reportedIssues + suppliedIssues).sortedBy { it.jobName }
    }
}