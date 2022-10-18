package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.resources.BrokerDisk
import com.infobip.kafkistry.service.resources.ClusterResourcesAnalyzer
import com.infobip.kafkistry.service.resources.UsageLevel
import org.springframework.stereotype.Component

@Component
class DiskUsageIssuesChecker(
    private val clusterResourcesAnalyzer: ClusterResourcesAnalyzer,
) : ClusterIssueChecker {

    override fun checkIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue> {
        val clusterDiskUsage = clusterResourcesAnalyzer.clusterDiskUsage(clusterIdentifier)
        return buildList {
            val combinedUsageIssue = clusterDiskUsage.combined.analyzeUsedDisk()
                ?.toIssue(null, "Current")
            val brokersUsageIssues = clusterDiskUsage.brokerUsages.mapNotNull { (brokerId, usage) ->
                usage.analyzeUsedDisk()?.toIssue(brokerId, "Current")
            }
            if (combinedUsageIssue != null && brokersUsageIssues.all { combinedUsageIssue.isSevereOrMoreSevereThan(it) }) {
                add(combinedUsageIssue)
            } else {
                addAll(brokersUsageIssues)
            }
            val combinedPossibleUsageIssue = clusterDiskUsage.combined.analyzePossibleUsedDisk()
                ?.toIssue(null, "Retention promised")
            val brokersPossibleUsageIssues = clusterDiskUsage.brokerUsages.mapNotNull { (brokerId, usage) ->
                usage.analyzePossibleUsedDisk()?.toIssue(brokerId, "Retention promised")
            }
            if (combinedPossibleUsageIssue != null && brokersPossibleUsageIssues.all {
                    combinedPossibleUsageIssue.isSevereOrMoreSevereThan(it)
                }) {
                add(combinedPossibleUsageIssue)
            } else {
                addAll(brokersPossibleUsageIssues)
            }
            if (clusterDiskUsage.errors.isNotEmpty()) {
                ClusterInspectIssue(
                    name = "HAVING_ERRORS_ANALYZING_DISK_USAGE",
                    violation = RuleViolation(
                        ruleClassName = checkerClassName,
                        severity = RuleViolation.Severity.CRITICAL,
                        message = "Having ${clusterDiskUsage.errors.size} errors. " +
                                clusterDiskUsage.errors.joinToString("; "),
                    ),
                    doc = "Unable to analyze disk usage due to runtime error(s)",
                ).also { add(it) }
            }
        }
    }

    private fun ClusterInspectIssue?.isSevereOrMoreSevereThan(other: ClusterInspectIssue?): Boolean {
        if (this == null) return false
        if (other == null) return true
        return this.violation.severity.ordinal >= other.violation.severity.ordinal
    }

    private fun BrokerDisk.analyzeUsedDisk(): ProblematicDisk? {
        val usedBytes = usage.totalUsedBytes ?: return null
        val capacityBytes = usage.totalCapacityBytes ?: return null
        val usedPercent = portions.usedPercentOfCapacity ?: return null
        return when (portions.usageLevel) {
            UsageLevel.NONE, UsageLevel.LOW -> null
            UsageLevel.MEDIUM -> ProblematicDisk(
                issueName = "DISK_USAGE_SIGNIFICANT",
                usageBytes = usedBytes,
                capacityBytes = capacityBytes,
                portionOfCapacity = usedPercent,
                severity = RuleViolation.Severity.WARNING,
                doc = "Indicates that disk usage is reaching limits of total capacity available."
            )
            UsageLevel.HIGH -> ProblematicDisk(
                issueName = "DISK_USAGE_HIGH",
                usageBytes = usedBytes,
                capacityBytes = capacityBytes,
                portionOfCapacity = usedPercent,
                severity = RuleViolation.Severity.ERROR,
                doc = "Indicates that used disk is almost full."
            )
            UsageLevel.OVERFLOW -> ProblematicDisk(
                issueName = "DISK_USAGE_OVERFLOW",
                usageBytes = usedBytes,
                capacityBytes = capacityBytes,
                portionOfCapacity = usedPercent,
                severity = RuleViolation.Severity.CRITICAL,
                doc = "Indicates that disk usage is somehow reporting more being used than total available."
            )
        }
    }

    private fun BrokerDisk.analyzePossibleUsedDisk(): ProblematicDisk? {
        val capacityBytes = usage.totalCapacityBytes ?: return null
        val usedPercent = portions.possibleUsedPercentOfCapacity ?: return null
        if (portions.possibleUsageLevel != UsageLevel.OVERFLOW) {
            return null
        }
        return ProblematicDisk(
            issueName = "OVER_PROMISED_RETENTION",
            usageBytes = usage.boundedSizePossibleUsedBytes,
            capacityBytes = capacityBytes,
            portionOfCapacity = usedPercent,
            severity = RuleViolation.Severity.CRITICAL,
            doc = "Indicates that current topics have 'retention.bytes' that add up to more required disk than available",
        )
    }

    private data class ProblematicDisk(
        val issueName: String,
        val usageBytes: Long,
        val capacityBytes: Long,
        val portionOfCapacity: Double,
        val severity: RuleViolation.Severity,
        val doc: String,
    )

    private fun ProblematicDisk.toIssue(brokerId: BrokerId?, messagePrefix: String): ClusterInspectIssue {
        return ClusterInspectIssue(
            name = issueName,
            violation = RuleViolation(
                ruleClassName = checkerClassName,
                severity = severity,
                message = "$messagePrefix usage of %USED_BYTES% is at %PERCENTAGE% % of disk capacity %CAPACITY_BYTES% on " +
                        (brokerId?.let { "broker ID %BROKER%" } ?: "all brokers combined"),
                placeholders = mapOf(
                    "USED_BYTES" to Placeholder("used.bytes", usageBytes),
                    "CAPACITY_BYTES" to Placeholder("capacity.bytes", capacityBytes),
                    "PERCENTAGE" to Placeholder("used.percentage", portionOfCapacity),
                    "BROKER" to Placeholder("broker.id", brokerId ?: "all brokers"),
                ),
            ),
            doc = doc,
        )
    }
}