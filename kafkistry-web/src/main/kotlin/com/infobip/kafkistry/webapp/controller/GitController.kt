package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.GitApi
import com.infobip.kafkistry.webapp.url.GitUrls.Companion.GIT
import com.infobip.kafkistry.webapp.url.GitUrls.Companion.GIT_BRANCH
import com.infobip.kafkistry.webapp.url.GitUrls.Companion.GIT_COMMIT
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$GIT")
@ConditionalOnProperty("app.repository.git.enabled")
class GitController(
        private val gitApi: GitApi
) : BaseController() {

    @GetMapping(GIT_COMMIT)
    fun showCommit(
            @PathVariable("commitId") commitId: String
    ): ModelAndView {
        val commitChanges = gitApi.commitChanges(commitId)
        return ModelAndView("git/commit", mapOf(
                "commitChanges" to commitChanges
        ))
    }

    @GetMapping(GIT_BRANCH)
    fun showBranch(
            @RequestParam("branch") branch: String
    ): ModelAndView {
        val branchChanges = gitApi.branchChanges(branch)
        return ModelAndView("git/branch", mapOf(
                "branchChanges" to branchChanges
        ))
    }
}