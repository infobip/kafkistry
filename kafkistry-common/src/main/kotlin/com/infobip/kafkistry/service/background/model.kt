package com.infobip.kafkistry.service.background

data class BackgroundJobIssue(
        val jobName: String,
        val failureMessage: String,
        val timestamp: Long,
)