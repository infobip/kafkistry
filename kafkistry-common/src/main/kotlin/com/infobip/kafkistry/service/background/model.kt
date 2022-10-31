package com.infobip.kafkistry.service.background

import com.infobip.kafkistry.model.KafkaClusterIdentifier

data class BackgroundJobKey(
    val jobClass: String,
    val category: String,
    val phase: String,
    val cluster: KafkaClusterIdentifier? = null,
)

data class BackgroundJob(
    val key: BackgroundJobKey,
    val description: String,
) {
    companion object {

        fun of(
            description: String,
            category: String,
            phase: String = "refresh",
            cluster: KafkaClusterIdentifier? = null,
            jobClass: String = Exception().stackTrace[1].className,
        ) = BackgroundJob(
            key = BackgroundJobKey(jobClass, category, phase, cluster),
            description = description,
        )
    }
}

data class BackgroundJobIssue(
    val job: BackgroundJob,
    val failureMessage: String,
    val timestamp: Long,
)

data class BackgroundJobIssuesGroup(
    val groupKey: String,
    val issues: List<BackgroundJobIssue>,
)

data class BackgroundJobStatus(
    val job: BackgroundJob,
    val timestamp: Long,
    val lastSuccess: Boolean,
    val lastFailureMessage: String?,
)
