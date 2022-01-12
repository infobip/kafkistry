package com.infobip.kafkistry.webapp.url

class AboutUrls(base: String) : BaseUrls() {

    companion object {
        const val ABOUT = "/about"
        const val BUILD_INFO = "/build-info"
        const val USERS_SESSIONS = "/users-sessions"
        const val SCRAPING_STATUSES = "/scraping-statuses"
    }

    private val showAboutPage = Url(base)
    private val showBuildInfo = Url("$base$BUILD_INFO")
    private val showUsersSessions = Url("$base$USERS_SESSIONS")
    private val showScrapingStatuses = Url("$base$SCRAPING_STATUSES")

    fun showAboutPage() = showAboutPage.render()
    fun showBuildInfo() = showBuildInfo.render()
    fun showUsersSessions() = showUsersSessions.render()
    fun showScrapingStatuses() = showScrapingStatuses.render()

}