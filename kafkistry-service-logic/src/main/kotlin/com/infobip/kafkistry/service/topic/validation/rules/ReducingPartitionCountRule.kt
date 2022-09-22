package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.ExistingTopicInfo
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.service.topic.withClusterProperties
import org.springframework.stereotype.Component

@Component
class ReducingPartitionCountRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        val actualPartitionCount = topicDescriptionView.existingTopicInfo?.properties?.partitionCount
            ?: return valid()
        val expectedPartitionCount = topicDescriptionView.properties.partitionCount
        if (actualPartitionCount <= expectedPartitionCount) {
            return valid()
        }
        return violated(
            message = "Won't be able to reduce partition count " +
                    "from %CURRENT_PARTITION_COUNT% to %EXPECTED_PARTITION_COUNT% " +
                    "without complete deletion of a topic",
            placeholders = mapOf(
                "CURRENT_PARTITION_COUNT" to Placeholder("partition.count", actualPartitionCount),
                "EXPECTED_PARTITION_COUNT" to Placeholder("partition.count", expectedPartitionCount),
            ),
            severity = RuleViolation.Severity.CRITICAL,
        )
    }

    override fun doFixConfig(
        topicDescription: TopicDescription, clusterMetadata: ClusterMetadata, existingTopicInfo: ExistingTopicInfo?,
    ): TopicDescription {
        val currentPartitionCount = existingTopicInfo?.properties?.partitionCount ?: return topicDescription
        val properties = topicDescription.propertiesForCluster(clusterMetadata.ref)
        return topicDescription.withClusterProperties(
            clusterMetadata.ref.identifier,
            properties.copy(partitionCount = maxOf(currentPartitionCount, properties.partitionCount))
        )
    }
}