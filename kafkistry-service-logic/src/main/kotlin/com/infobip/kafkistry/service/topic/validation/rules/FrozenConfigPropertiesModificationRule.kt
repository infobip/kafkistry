package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.FreezeDirective
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import org.springframework.stereotype.Component

@Component
class FrozenConfigPropertiesModificationRule : ValidationRule {

    companion object {
        private const val PARTITION_COUNT = "partition-count"
        private const val REPLICATION_FACTOR = "replication-factor"
    }

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        val violatedModifications = with(topicDescriptionView) {
            val existingTopic = existingTopicInfo ?: return valid()
            originalDescription.freezeDirectives.flatMap { directive ->
                with(directive) {
                    listOfNotNull(
                        maybeForbiddenModification(
                            PARTITION_COUNT, old = existingTopic.properties.partitionCount, new = properties.partitionCount,
                        ),
                        maybeForbiddenModification(
                            REPLICATION_FACTOR, old = existingTopic.properties.replicationFactor, new = properties.replicationFactor,
                        )
                    ) + configProperties.mapNotNull {
                        maybeForbiddenModification(what = it, old = existingTopic.config[it]?.value, new = config[it])
                    }
                }
            }
        }
        if (violatedModifications.isEmpty()) {
            return valid()
        }
        return violated(
            message = "Attempting to modify frozen value: "
                + violatedModifications.indices.joinToString(separator = ", ") {
                "%CONFIG_KEY_$it% (%OLD_VALUE_$it% into %NEW_VALUE_$it%)"
            } + " Freeze reason: " + violatedModifications.map { it.freezeReason }.distinct(),
            placeholders = violatedModifications.flatMapIndexed { index, modification ->
                listOf(
                    "CONFIG_KEY_$index" to Placeholder("key.name", modification.what),
                    "OLD_VALUE_$index" to Placeholder(modification.what, modification.from ?: "[null]"),
                    "NEW_VALUE_$index" to Placeholder(modification.what, modification.into ?: "[null]"),
                )
            }.toMap()
        )
    }

    private fun FreezeDirective.maybeForbiddenModification(what: String, old: Any?, new: Any?): FrozenModification? {
        val enforced = what in configProperties
            || (what == PARTITION_COUNT && partitionCount)
            || (what == REPLICATION_FACTOR && replicationFactor)
        if (!enforced || old == new) {
            return null
        }
        return FrozenModification(reasonMessage, what, old, new)
    }

    data class FrozenModification(
        val freezeReason: String,
        val what: String,
        val from: Any?,
        val into: Any?,
    )

}
