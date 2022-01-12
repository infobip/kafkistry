package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.RequestingKeyValueRepository
import org.springframework.scheduling.annotation.Scheduled

class GitRefreshTrigger(
    private val git: GitRepository,
    private val repositories: List<RequestingKeyValueRepository<*, *>>,
) {

    private var lastCommitId: String? = currentGitCommit()

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
        val newCommitId = currentGitCommit()
        val needRefresh = newCommitId != lastCommitId || newCommitId == null
        lastCommitId = newCommitId
        if (needRefresh) {
            repositories.forEach { it.refresh() }
        }
    }
}