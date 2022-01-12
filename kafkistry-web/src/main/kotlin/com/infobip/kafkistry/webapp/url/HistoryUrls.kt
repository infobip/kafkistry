package com.infobip.kafkistry.webapp.url

class HistoryUrls(base: String) : BaseUrls() {

    companion object {
        const val HISTORY = "/history"
        const val HISTORY_ALL = "/all"
        const val HISTORY_RECENT = "/recent"
    }

    private val showAll = Url("$base$HISTORY_ALL")
    private val showRecent = Url("$base$HISTORY_RECENT")
    private val showRecentCount = Url("$base$HISTORY_RECENT", listOf("count"))

    fun showAll() = showAll.render()
    fun showRecent() = showRecent.render()
    fun showRecentCount(count: Int) = showRecentCount.render("count" to count.toString())
}