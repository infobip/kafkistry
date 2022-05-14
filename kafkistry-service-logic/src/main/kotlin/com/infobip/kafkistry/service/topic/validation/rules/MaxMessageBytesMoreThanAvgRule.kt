package com.infobip.kafkistry.service.topic.validation.rules

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.toBytes
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.withClusterProperty
import org.springframework.stereotype.Component

@Component
class MaxMessageBytesMoreThanAvgRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val resourceRequirements = topicDescriptionView.originalDescription.resourceRequirements
            ?: return valid()
        val config = topicDescriptionView.config
        val avgMsgSizeBytes = resourceRequirements.avgMessageSize.toBytes()
        val maxMessageBytes = try {
            config[TopicConfig.MAX_MESSAGE_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.MAX_MESSAGE_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.MAX_MESSAGE_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return valid()
        if (avgMsgSizeBytes < maxMessageBytes) {
            return valid()
        }
        return violated(
            message = "Average message size %avgMessageBytes% should not be bigger than " +
                    "${TopicConfig.MAX_MESSAGE_BYTES_CONFIG} %maxMessageBytes%",
            placeholders = mapOf(
                "avgMessageBytes" to Placeholder("resources.avg.message.bytes", avgMsgSizeBytes),
                "maxMessageBytes" to Placeholder(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, maxMessageBytes),
            ),
            severity = RuleViolation.Severity.WARNING,
        )
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val avgMsgBytes = topicDescription.resourceRequirements?.avgMessageSize?.toBytes()
            ?: return topicDescription
        return topicDescription.withClusterProperty(
            clusterMetadata.ref.identifier, TopicConfig.MAX_MESSAGE_BYTES_CONFIG, avgMsgBytes.times(1.1).toLong().toString()
        )
    }
}