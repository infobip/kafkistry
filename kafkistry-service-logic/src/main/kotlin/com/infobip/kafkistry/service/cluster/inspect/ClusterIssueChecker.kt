package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.RuleViolation

interface ClusterIssueChecker {

    val checkerClassName: String get() = javaClass.name

    fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue>
}

data class ClusterInspectIssue(
    val name: String,
    val violation: RuleViolation,
)

