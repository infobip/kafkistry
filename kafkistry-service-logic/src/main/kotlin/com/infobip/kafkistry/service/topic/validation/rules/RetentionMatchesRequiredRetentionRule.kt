package com.infobip.kafkistry.service.topic.validation.rules

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.configForCluster
import com.infobip.kafkistry.service.resources.INF_RETENTION
import com.infobip.kafkistry.service.withClusterProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.*

@Component
class RetentionMatchesRequiredRetentionRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val resourceRequirements = topicDescriptionView.originalDescription.resourceRequirements
            ?: return valid()

        val config = topicDescriptionView.config
        val retentionMs = try {
            config[TopicConfig.RETENTION_MS_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.RETENTION_MS_CONFIG} property is not numeric value, (${config[TopicConfig.RETENTION_MS_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return violated(
            message = "${TopicConfig.RETENTION_MS_CONFIG} is not explicitly configured",
            severity = RuleViolation.Severity.WARNING,
        )
        val expectedRetentionMs = resourceRequirements.retention(clusterMetadata.ref).toMillis()
        if (retentionMs != INF_RETENTION && expectedRetentionMs / 1000 != retentionMs / 1000) {
            return violated(
                    message = "Configured ${TopicConfig.RETENTION_MS_CONFIG} %retentionTime% is not matching " +
                            "required resources retention %expectedRetention%",
                    placeholders = mapOf(
                            "retentionTime" to Placeholder(TopicConfig.RETENTION_MS_CONFIG, retentionMs),
                            "expectedRetention" to Placeholder(TopicConfig.RETENTION_MS_CONFIG, expectedRetentionMs),
                    ),
                    severity = RuleViolation.Severity.WARNING,
            )
        }
        return valid()
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val resourceRequirements = topicDescription.resourceRequirements
        val expectedRetentionMs = resourceRequirements?.retention(clusterMetadata.ref)?.toMillis()
                ?: return topicDescription
        val retentionMs = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.RETENTION_MS_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            null
        } ?: return topicDescription.withClusterProperty(
            clusterMetadata.ref.identifier, "retention.ms", expectedRetentionMs.toString()
        )
        val newRetention = when {
            retentionMs % (1000L * 60 * 60 * 24) == 0L -> DataRetention(MILLISECONDS.toDays(retentionMs).toInt(), DAYS)
            retentionMs % (1000L * 60 * 60) == 0L -> DataRetention(MILLISECONDS.toHours(retentionMs).toInt(), HOURS)
            retentionMs % (1000L * 60) == 0L -> DataRetention(MILLISECONDS.toMinutes(retentionMs).toInt(), MINUTES)
            else -> DataRetention(MILLISECONDS.toSeconds(retentionMs).toInt(), SECONDS)
        }
        return topicDescription.copy(
                resourceRequirements = resourceRequirements.copy(
                        retentionOverrides = resourceRequirements.retentionOverrides + (clusterMetadata.ref.identifier to newRetention)
                )
        )
    }
}