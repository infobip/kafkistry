package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.HistoryApi
import com.infobip.kafkistry.webapp.url.HistoryUrls.Companion.HISTORY
import com.infobip.kafkistry.webapp.url.HistoryUrls.Companion.HISTORY_ALL
import com.infobip.kafkistry.webapp.url.HistoryUrls.Companion.HISTORY_RECENT
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$HISTORY")
class HistoryController(
    val historyApi: HistoryApi
) {

    @GetMapping(HISTORY_ALL)
    fun showAllCommitsHistory(): ModelAndView {
        val allCommits = historyApi.allHistory()
        return ModelAndView(
            "history/all", mutableMapOf(
                "allCommits" to allCommits
            )
        )
    }

    @GetMapping(HISTORY_RECENT)
    fun showRecentCommitsHistory(
        @RequestParam(name = "count", required = false, defaultValue = "10") recentCount: Int
    ): ModelAndView {
        val allCommits = historyApi.recentHistory(recentCount)
        return ModelAndView(
            "history/recent", mutableMapOf(
                "allCommits" to allCommits,
                "recentCount" to recentCount,
            )
        )
    }


}