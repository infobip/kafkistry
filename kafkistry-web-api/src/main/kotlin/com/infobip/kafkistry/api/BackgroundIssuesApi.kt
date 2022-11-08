package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.background.BackgroundJobIssue
import com.infobip.kafkistry.service.background.BackgroundJobIssuesGroup
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.background.BackgroundJobStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/background-issues")
class BackgroundIssuesApi(
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry
) {

    @GetMapping
    fun currentIssues(): List<BackgroundJobIssue> = backgroundJobIssuesRegistry.currentIssues()

    @GetMapping("/grouped")
    fun currentGroupedIssues(): List<BackgroundJobIssuesGroup> = backgroundJobIssuesRegistry.currentGroupedIssues()

    @GetMapping("/statuses")
    fun currentStatuses(): List<BackgroundJobStatus> = backgroundJobIssuesRegistry.currentStatuses()
}