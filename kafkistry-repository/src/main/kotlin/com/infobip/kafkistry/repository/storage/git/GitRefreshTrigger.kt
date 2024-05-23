package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.RequestingKeyValueRepository
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.scheduling.annotation.Scheduled

class GitRefreshTrigger(
    private val git: GitRepository,
    private val repositories: List<RequestingKeyValueRepository<*, *>>,
    private val issuesRegistry: BackgroundJobIssuesRegistry,
) {

    private var lastCommitId: String? = currentGitCommit()
    private val backgroundJob = BackgroundJob.of(
        category = "git", description = "Git repository refresh/pull",
    )
    private val backgroundJobRepos = BackgroundJob.of(
        category = "repository-git", description = "Data repository cache backed by git refresh",
    )

    private fun currentGitCommit(): String? {
        return try {
            git.currentCommitId()
        } catch (ex: Exception) {
            null
        }
    }

    @Scheduled(fixedDelayString = "#{gitRepositoriesProperties.refreshIntervalMs()}")
    fun trigger() {
        val jobExecution = issuesRegistry.newExecution(backgroundJob)
        git.refreshRepository()
        val lastRefreshErrorMsg = git.lastRefreshErrorMsg()
        if (lastRefreshErrorMsg != null) {
            jobExecution.failed(lastRefreshErrorMsg)
        } else {
            jobExecution.succeeded()
        }
        issuesRegistry.doCapturingException(backgroundJobRepos) {
            val newCommitId = currentGitCommit()
            val needRefresh = newCommitId != lastCommitId || newCommitId == null
            if (needRefresh) {
                repositories.forEach { it.refresh() }
            }
            lastCommitId = newCommitId
        }
    }
}