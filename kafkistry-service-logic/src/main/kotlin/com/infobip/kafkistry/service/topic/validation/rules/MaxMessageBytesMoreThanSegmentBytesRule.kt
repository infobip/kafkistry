package com.infobip.kafkistry.service.topic.validation.rules

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.toBytes
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.topic.withClusterProperty
import org.springframework.stereotype.Component

@Component
class MaxMessageBytesMoreThanSegmentBytesRule : ValidationRule {
    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val config = topicDescriptionView.config
        val segmentBytes = try {
            config[TopicConfig.SEGMENT_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.SEGMENT_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.SEGMENT_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return valid()
        val maxMessageBytes = try {
            config[TopicConfig.MAX_MESSAGE_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.MAX_MESSAGE_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.MAX_MESSAGE_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return valid()
        if (maxMessageBytes <= segmentBytes) {
            return valid()
        }
        return violated(
            message = "${TopicConfig.MAX_MESSAGE_BYTES_CONFIG} %maxMessageBytes% should not be bigger than " +
                    "${TopicConfig.SEGMENT_BYTES_CONFIG} %segmentBytes% because biggest allowed message will be limited by max segment size",
            placeholders = mapOf(
                "segmentBytes" to Placeholder(TopicConfig.SEGMENT_BYTES_CONFIG, segmentBytes),
                "maxMessageBytes" to Placeholder(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, maxMessageBytes),
            ),
            severity = RuleViolation.Severity.WARNING,
        )
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val maxMessageBytes = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.MAX_MESSAGE_BYTES_CONFIG]?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            return topicDescription
        }
        val segmentBytes = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.SEGMENT_BYTES_CONFIG]?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            return topicDescription
        }
        val retentionBytes = try {
            topicDescription.configForCluster(clusterMetadata.ref)[TopicConfig.RETENTION_BYTES_CONFIG]?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            return topicDescription
        }
        val fixedSegmentBytes = segmentBytes.coerceAtLeast(maxMessageBytes)
        val fixedRetentionBytes = retentionBytes.coerceAtLeast(fixedSegmentBytes)
        return topicDescription
            .withClusterProperty(clusterMetadata.ref.identifier, TopicConfig.SEGMENT_BYTES_CONFIG, fixedSegmentBytes.toString())
            .withClusterProperty(clusterMetadata.ref.identifier, TopicConfig.RETENTION_BYTES_CONFIG, fixedRetentionBytes.toString())
    }
}
