package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicProperties
import org.springframework.stereotype.Component

@Component
class MultipleTagsAmbiguousTopicPropertiesRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        if (clusterMetadata.ref.identifier in topicDescriptionView.originalDescription.perClusterProperties.keys) {
            return valid()
        }
        val propertiesCandidates = propertiesCandidates(clusterMetadata, topicDescriptionView.originalDescription)
        return if (propertiesCandidates.values.distinct().size > 1) {
            violated(
                "There is ambiguous TopicProperties resolution due to following competing properties: $propertiesCandidates"
            )
        } else {
            valid()
        }
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val propertiesCandidates = propertiesCandidates(clusterMetadata, topicDescription)
        val chosenProperties = propertiesCandidates.entries.first().value
        val conflictingTags = propertiesCandidates.filterValues { it != chosenProperties }.keys
        return topicDescription.copy(
            perTagProperties = topicDescription.perTagProperties.filterKeys { it !in conflictingTags }
        )
    }

    private fun propertiesCandidates(
        clusterMetadata: ClusterMetadata, topicDescription: TopicDescription
    ): Map<Tag, TopicProperties> {
        val clusterTags = clusterMetadata.ref.tags
        return topicDescription.perTagProperties.filterKeys { it in clusterTags }
    }
}
