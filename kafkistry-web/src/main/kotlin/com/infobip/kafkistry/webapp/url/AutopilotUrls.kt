package com.infobip.kafkistry.webapp.url

class AutopilotUrls(base: String) : BaseUrls() {

    companion object {
        const val AUTOPILOT = "/autopilot"

    }

    private val showAutopilotPage = Url(base)

    fun showAutopilotPage() = showAutopilotPage.render()

}