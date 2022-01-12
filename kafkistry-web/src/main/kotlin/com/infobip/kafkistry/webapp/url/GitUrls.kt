package com.infobip.kafkistry.webapp.url

class GitUrls(base: String) : BaseUrls() {

    companion object {
        const val GIT = "/git"
        const val GIT_COMMIT = "/commits/{commitId}"
        const val GIT_BRANCH = "/branches/inspect"
    }

    private val showCommit = Url("$base$GIT_COMMIT")
    private val showBranch = Url("$base$GIT_BRANCH", listOf("branch"))

    fun showCommit(commitId: String) = showCommit.render("commitId" to commitId)

    fun showBranch(branch: String) = showBranch.render("branch" to branch)

}