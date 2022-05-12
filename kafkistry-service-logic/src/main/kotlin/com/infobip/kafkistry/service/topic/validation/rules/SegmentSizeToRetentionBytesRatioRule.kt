package com.infobip.kafkistry.service.topic.validation.rules

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.configForCluster
import com.infobip.kafkistry.service.resources.INF_RETENTION
import com.infobip.kafkistry.service.withClusterProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties("app.topic-validation.segment-size-rule")
class SegmentSizeToRetentionBytesRatioRuleProperties {
    var maxRatioToRetentionBytes = 0.5
    var minRatioToRetentionBytes = 0.02
}

@Component
class SegmentSizeToRetentionBytesRatioRule(
    private val properties: SegmentSizeToRetentionBytesRatioRuleProperties,
) : ValidationRule {

    init {
        with(properties) {
            require(maxRatioToRetentionBytes > minRatioToRetentionBytes) {
                "Max ratio $maxRatioToRetentionBytes must be > than min ratio $minRatioToRetentionBytes"
            }
        }
    }

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val config = topicDescriptionView.config
        val retentionBytes = try {
            config[TopicConfig.RETENTION_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.RETENTION_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.RETENTION_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return valid()
        if (retentionBytes == INF_RETENTION) {
            return valid()
        }
        val segmentBytes = try {
            config[TopicConfig.SEGMENT_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.SEGMENT_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.SEGMENT_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return valid()

        if (segmentBytes > Int.MAX_VALUE) {
            return violated(
                message = "${TopicConfig.SEGMENT_BYTES_CONFIG} property is bigger than MAX_INT (${config[TopicConfig.SEGMENT_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        }

        val ratio = segmentBytes.toDouble() / retentionBytes.toDouble()
        return with(properties) {
            when {
                ratio > maxRatioToRetentionBytes -> violated(
                    message = "Configured ${TopicConfig.SEGMENT_BYTES_CONFIG} %segmentBytes% is too big " +
                            "comparing to ${TopicConfig.RETENTION_BYTES_CONFIG} %retentionBytes%, having ratio %ratio% " +
                            "which is more than max configured ratio %maxRatio%. Topic's replicas might use more disk than expected.",
                    placeholders = mapOf(
                        "segmentBytes" to Placeholder(TopicConfig.SEGMENT_BYTES_CONFIG, segmentBytes),
                        "retentionBytes" to Placeholder(TopicConfig.RETENTION_BYTES_CONFIG, retentionBytes),
                        "ratio" to Placeholder("ratio", ratio),
                        "maxRatio" to Placeholder("maxRatio", maxRatioToRetentionBytes),
                    ),
                    severity = RuleViolation.Severity.WARNING,
                )
                ratio < minRatioToRetentionBytes && retentionBytes < Int.MAX_VALUE -> violated(
                    message = "Configured ${TopicConfig.SEGMENT_BYTES_CONFIG} %segmentBytes% is too small " +
                            "comparing to ${TopicConfig.RETENTION_BYTES_CONFIG} %retentionBytes%, having ratio %ratio% " +
                            "which is less than min configured ratio %minRatio%. Topic's replicas might have many small segments.",
                    placeholders = mapOf(
                        "segmentBytes" to Placeholder(TopicConfig.SEGMENT_BYTES_CONFIG, segmentBytes),
                        "retentionBytes" to Placeholder(TopicConfig.RETENTION_BYTES_CONFIG, retentionBytes),
                        "ratio" to Placeholder("ratio", ratio),
                        "minRatio" to Placeholder("minRatio", minRatioToRetentionBytes),
                    ),
                    severity = RuleViolation.Severity.WARNING,
                )
                else -> valid()
            }
        }
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val retentionBytes = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.RETENTION_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            null
        } ?: return topicDescription
        val segmentBytes = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.SEGMENT_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            null
        } ?: 0L
        val maxSegmentBytes = retentionBytes.times(properties.maxRatioToRetentionBytes).toLong()
        val minSegmentBytes = retentionBytes.times(properties.minRatioToRetentionBytes).toLong()
        val fixedSegmentBytes = segmentBytes.coerceIn(minSegmentBytes, maxSegmentBytes).coerceAtMost(Int.MAX_VALUE.toLong())
        return topicDescription.withClusterProperty(
            clusterMetadata.ref.identifier, TopicConfig.SEGMENT_BYTES_CONFIG, fixedSegmentBytes.toString()
        )
    }
}