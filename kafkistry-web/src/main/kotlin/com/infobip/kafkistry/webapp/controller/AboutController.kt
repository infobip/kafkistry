package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.BackgroundIssuesApi
import com.infobip.kafkistry.api.BuildInfoApi
import com.infobip.kafkistry.api.ScrapingStatusApi
import com.infobip.kafkistry.api.WebSessionsApi
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.ABOUT
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.BACKGROUND_JOBS
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.BUILD_INFO
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.SCRAPING_STATUSES
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.USERS_SESSIONS
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$ABOUT")
class AboutController(
    private val buildInfoApi: BuildInfoApi,
    private val webSessionsApi: WebSessionsApi,
    private val scrapingStatusApi: ScrapingStatusApi,
    private val backgroundIssuesApi: BackgroundIssuesApi,
) : BaseController() {

    @GetMapping
    fun showAboutPage(): ModelAndView {
        return ModelAndView("about/index")
    }

    @GetMapping(BUILD_INFO)
    fun showBuildInfo(): ModelAndView {
        val modules = buildInfoApi.modulesBuildInfos()
        return ModelAndView(
            "about/buildInfo", mapOf(
                "modules" to modules,
            )
        )
    }

    @GetMapping(USERS_SESSIONS)
    fun showUserSessions(): ModelAndView {
        val usersSessions = webSessionsApi.allUsersSessions()
        return ModelAndView(
            "about/usersSessions", mapOf(
                "usersSessions" to usersSessions,
            )
        )
    }

    @GetMapping(SCRAPING_STATUSES)
    fun showScrapingStatuses(): ModelAndView {
        val scrapingStatuses = scrapingStatusApi.currentScrapingStatuses()
        return ModelAndView(
            "about/scrapingStatuses", mapOf(
                "scrapingStatuses" to scrapingStatuses,
            )
        )
    }

    @GetMapping(BACKGROUND_JOBS)
    fun showBackgroundJobs(): ModelAndView {
        val backgroundJobStatuses = backgroundIssuesApi.currentStatuses()
        return ModelAndView(
            "about/backgroundJobs", mapOf(
                "backgroundJobStatuses" to backgroundJobStatuses,
            )
        )
    }

}