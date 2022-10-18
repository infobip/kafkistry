package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.NamedType
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.RuleViolation.Severity
import com.infobip.kafkistry.service.StatusLevel

interface ClusterIssueChecker {

    val checkerClassName: String get() = javaClass.name

    fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue>
}

data class ClusterInspectIssue(
    override val name: String,
    val violation: RuleViolation,
    override val doc: String,
): NamedType {
    override val level: StatusLevel = violation.severity.toStatusLevel()
    override val valid: Boolean = violation.severity == Severity.NONE
}

fun Severity.toStatusLevel(): StatusLevel = when (this) {
    Severity.NONE -> StatusLevel.IGNORE
    Severity.MINOR -> StatusLevel.IMPORTANT
    Severity.WARNING -> StatusLevel.WARNING
    Severity.ERROR -> StatusLevel.ERROR
    Severity.CRITICAL -> StatusLevel.CRITICAL
}

