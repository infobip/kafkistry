package com.infobip.kafkistry.api

import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.RepositoryRefreshEvent
import com.infobip.kafkistry.repository.storage.Branch
import com.infobip.kafkistry.repository.storage.CommitFileChanges
import com.infobip.kafkistry.repository.storage.git.GitRefreshTrigger
import com.infobip.kafkistry.repository.storage.git.GitRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/git")
@ConditionalOnProperty("app.repository.git.enabled")
class GitApi(
    private val gitRepository: GitRepository,
    private val gitRefreshTrigger: GitRefreshTrigger,
    private val eventPublisher: EventPublisher,
) {

    @PostMapping("/re-clone-git")
    fun reCloneGit(): Unit = gitRepository.reCloneRepository()

    @GetMapping("/last-refresh-error")
    fun lastRefreshError(): String? = gitRepository.lastRefreshErrorMsg()

    @PostMapping("/refresh-now")
    fun refreshNow() {
        eventPublisher.publish(RepositoryRefreshEvent())
        gitRefreshTrigger.trigger()
    }

    @GetMapping("/branches")
    fun existingBranches(): List<String> = gitRepository.listBranches()

    @GetMapping("/branches/inspect")
    fun branchChanges(
        @RequestParam("branch") branch: Branch
    ): GitRepository.BranchChanges = gitRepository.listBranchChanges(branch)

    @GetMapping("/commits/{commitId}")
    fun commitChanges(
        @PathVariable("commitId") commitId: String
    ): CommitFileChanges = gitRepository.commitChanges(commitId)

}