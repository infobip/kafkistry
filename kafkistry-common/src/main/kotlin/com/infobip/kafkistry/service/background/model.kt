package com.infobip.kafkistry.service.background

import com.infobip.kafkistry.model.KafkaClusterIdentifier

data class BackgroundJobKey(
    val jobClass: String,
    val type: String,
    val jobName: String,
    val cluster: KafkaClusterIdentifier? = null,
)

data class BackgroundJobIssue(
    val key: BackgroundJobKey,
    val failureMessage: String,
    val timestamp: Long,
)

data class BackgroundJobIssuesGroup(
    val groupKey: String,
    val issues: List<BackgroundJobIssue>,
)

data class BackgroundJobStatus(
    val key: BackgroundJobKey,
    val timestamp: Long,
    val lastSuccess: Boolean,
    val lastFailureMessage: String?,
)