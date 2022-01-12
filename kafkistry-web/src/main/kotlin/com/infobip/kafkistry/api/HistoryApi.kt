package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.Change
import com.infobip.kafkistry.service.ChangeCommit
import com.infobip.kafkistry.service.HistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/history")
class HistoryApi(
    private val historyService: HistoryService
) {

    @GetMapping("/all")
    fun allHistory(): List<ChangeCommit<out Change>> = historyService.getFullHistory()

    @GetMapping("/recent")
    fun recentHistory(
        @RequestParam("count") recentCount: Int
    ): List<ChangeCommit<out Change>> = historyService.getRecentHistory(recentCount)

}