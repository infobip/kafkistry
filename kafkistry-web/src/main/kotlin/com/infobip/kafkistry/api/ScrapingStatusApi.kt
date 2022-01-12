package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.scrapingstatus.ClusterScrapingStatus
import com.infobip.kafkistry.service.scrapingstatus.ScrapingStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/scraping-statuses")
class ScrapingStatusApi(
    private val scrapingStatusService: ScrapingStatusService
) {

    @GetMapping
    fun currentScrapingStatuses(): List<ClusterScrapingStatus> = scrapingStatusService.currentScrapingStatuses()
}