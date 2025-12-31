package com.infobip.kafkistry.webapp.url

class AboutUrls(base: String) : BaseUrls() {

    companion object {
        const val ABOUT = "/about"
        const val BUILD_INFO = "/build-info"
        const val USERS_SESSIONS = "/users-sessions"
        const val SCRAPING_STATUSES = "/scraping-statuses"
        const val BACKGROUND_JOBS = "/background-jobs"
        const val ENVIRONMENT = "/environment"
    }

    private val showAboutPage = Url(base)
    private val showBuildInfo = Url("$base$BUILD_INFO")
    private val showUsersSessions = Url("$base$USERS_SESSIONS")
    private val showScrapingStatuses = Url("$base$SCRAPING_STATUSES")
    private val showBackgroundJobs = Url("$base$BACKGROUND_JOBS")
    private val showEnvironment = Url("$base$ENVIRONMENT")

    fun showAboutPage() = showAboutPage.render()
    fun showBuildInfo() = showBuildInfo.render()
    fun showUsersSessions() = showUsersSessions.render()
    fun showScrapingStatuses() = showScrapingStatuses.render()
    fun showBackgroundJobs() = showBackgroundJobs.render()
    fun showEnvironment() = showEnvironment.render()

}