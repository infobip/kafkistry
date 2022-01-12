package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.model.TopicDescription
import org.springframework.stereotype.Component

@Component
class MultipleTagsAmbiguousTopicConfigRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val conflictingKeyTagValues = conflictingConfigs(
            topicDescriptionView.originalDescription, clusterMetadata
        )
        if (conflictingKeyTagValues.isEmpty()) {
            return valid()
        }
        return violated(
            message = "There ${if (conflictingKeyTagValues.size > 1) "are" else "is"} ambiguous TopicConfig resolution " +
                    "due to following competing config: " +
                    conflictingKeyTagValues.map { it }.joinToString(separator = "; ") { (key, tagValues) ->
                        " key=$key " + tagValues.keys
                            .joinToString(separator = ", ", prefix = "[", postfix = "]") { tag ->
                                "tag='$tag' => %${(key + "_" + tag).escapePH()}%"
                            }
                    },
            placeholders = conflictingKeyTagValues
                .flatMap { (key, tagValues) ->
                    tagValues.map { (tag, value) ->
                        (key + "_" + tag).escapePH() to Placeholder(key, value.toString())
                    }
                }
                .toMap()
        )
    }

    private val wordChar = Regex("\\w")

    private fun String.escapePH() = map { if (it.toString().matches(wordChar)) it else '_' }
        .joinToString(separator = "")

    private fun conflictingConfigs(
        topicDescription: TopicDescription, clusterMetadata: ClusterMetadata
    ): Map<String, Map<Tag, String?>> {
        val clusterOverrides = topicDescription.perClusterConfigOverrides[clusterMetadata.ref.identifier].orEmpty()
        return topicDescription.perTagConfigOverrides
            .filterKeys { it in clusterMetadata.ref.tags }
            .flatMap { (tag, config) ->
                config.map { (key, value) -> Triple(key, value, tag) }
            }
            .groupBy({ it.first }, { it.third to it.second })
            .filterKeys { it !in clusterOverrides.keys }    //cluster specific override wins, no ambiguity
            .mapValues { (_, tagValueList) -> tagValueList.toMap() }
            .filterValues { it.values.distinct().size > 1 }
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val conflictingKeyTagValues = conflictingConfigs(topicDescription, clusterMetadata)
        val chosenKeyValues = conflictingKeyTagValues
            .mapValues { (_, tagValues) -> tagValues.entries.first().value }
        val conflictingKeyTags = conflictingKeyTagValues
            .mapValues { (key, tagValues) ->
                tagValues.filterValues { it != chosenKeyValues[key] }.keys
            }
        return topicDescription.copy(
            perTagConfigOverrides = topicDescription.perTagConfigOverrides
                .mapValues { (tag, config) ->
                    config.filterKeys { tag !in conflictingKeyTags[it].orEmpty()  }
                }
                .filterValues { it.isNotEmpty() }
        )
    }

}
