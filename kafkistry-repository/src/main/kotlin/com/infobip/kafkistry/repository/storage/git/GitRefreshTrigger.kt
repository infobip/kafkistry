package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.RequestingKeyValueRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.background.BackgroundJobKey
import org.springframework.scheduling.annotation.Scheduled

class GitRefreshTrigger(
    private val git: GitRepository,
    private val repositories: List<RequestingKeyValueRepository<*, *>>,
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry,
) {

    private var lastCommitId: String? = currentGitCommit()
    private val jobKey = BackgroundJobKey(javaClass.name,"git","Git repository refresh")

    private fun currentGitCommit(): String? {
        return try {
            git.currentCommitId()
        } catch (ex: Exception) {
            null
        }
    }

    @Scheduled(fixedDelayString = "#{gitRepositoriesProperties.refreshIntervalMs()}")
    fun trigger() {
        git.refreshRepository()
        val lastRefreshErrorMsg = git.lastRefreshErrorMsg()
        if (lastRefreshErrorMsg != null) {
            backgroundJobIssuesRegistry.reportIssue(jobKey, lastRefreshErrorMsg)
        } else {
            backgroundJobIssuesRegistry.clearIssue(jobKey)
        }
        val newCommitId = currentGitCommit()
        val needRefresh = newCommitId != lastCommitId || newCommitId == null
        lastCommitId = newCommitId
        if (needRefresh) {
            repositories.forEach { it.refresh() }
        }
    }
}