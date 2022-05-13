package com.infobip.kafkistry.service.topic.validation.rules

import org.apache.kafka.common.config.TopicConfig
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.resources.INF_RETENTION
import com.infobip.kafkistry.service.resources.RequiredResourcesInspector
import com.infobip.kafkistry.service.resources.TopicResourceRequiredUsages
import com.infobip.kafkistry.service.topic.withClusterProperty
import org.springframework.stereotype.Component

@Component
class SizeRetentionEnoughToCoverRequiredRule(
        val resourcesInspector: RequiredResourcesInspector
) : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val resourceRequirements = topicDescriptionView.originalDescription.resourceRequirements
            ?: return valid()
        if (clusterMetadata.info == null) return valid()

        val usages: TopicResourceRequiredUsages = try {
            resourcesInspector.inspectTopicResources(
                    topicDescriptionView.properties, resourceRequirements,
                    clusterMetadata.ref, clusterMetadata.info
            )
        } catch (ex: Exception) {
            return violated(
                message = "Failed to inspect resource usages, cause: $ex",
                severity = RuleViolation.Severity.ERROR,
            )
        }
        val config = topicDescriptionView.config
        val currentRetentionBytes = try {
            config[TopicConfig.RETENTION_BYTES_CONFIG]?.toLong()
        } catch (e: NumberFormatException) {
            return violated(
                message = "${TopicConfig.RETENTION_BYTES_CONFIG} property is not numeric value, (${config[TopicConfig.RETENTION_BYTES_CONFIG]})",
                severity = RuleViolation.Severity.ERROR,
            )
        } ?: return violated("${TopicConfig.RETENTION_BYTES_CONFIG} is not explicitly configured")

        if (currentRetentionBytes != INF_RETENTION && usages.diskUsagePerPartitionReplica > currentRetentionBytes) {
            return violated(
                    message = "Configured retention.bytes %currentRetentionSize% is not enough " +
                            "for required estimated data size per partition replica %neededRetentionSize%",
                    placeholders = mapOf(
                            "currentRetentionSize" to Placeholder(TopicConfig.RETENTION_BYTES_CONFIG, currentRetentionBytes),
                            "neededRetentionSize" to Placeholder(TopicConfig.RETENTION_BYTES_CONFIG, usages.diskUsagePerPartitionReplica)
                    ),
                    severity = RuleViolation.Severity.WARNING,
            )
        }
        return valid()
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val resourceRequirements = topicDescription.resourceRequirements ?: return topicDescription
        val usages: TopicResourceRequiredUsages = try {
            resourcesInspector.inspectTopicResources(
                    topicDescription.properties, resourceRequirements,
                    clusterMetadata.ref, clusterMetadata.info
            )
        } catch (ex: Exception) {
            return topicDescription
        }
        val neededRetentionBytes = usages.diskUsagePerPartitionReplica
        return topicDescription.withClusterProperty(
            clusterMetadata.ref.identifier, TopicConfig.RETENTION_BYTES_CONFIG, neededRetentionBytes.toString()
        )
    }
}