package com.infobip.kafkistry.service.topic.validation

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.validation.rules.ClusterMetadata
import com.infobip.kafkistry.service.topic.validation.rules.ValidationRule
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.validation.rules.TopicDescriptionView
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.topic-validation")
class TopicValidationProperties {
    var disabledRules = mutableListOf<Class<in ValidationRule>>()
    var enabledRules = mutableListOf<Class<in ValidationRule>>()
}

@Component
class TopicConfigurationValidator(
    validationRules: List<ValidationRule>,
    properties: TopicValidationProperties,
) {

    private val rules = validationRules
        .filterEnabled(properties.enabledRules)
        .filterDisabled(properties.disabledRules)

    private fun List<ValidationRule>.filterEnabled(enabled: List<Class<in ValidationRule>>): List<ValidationRule> {
        if (enabled.isEmpty()) return this
        return filter { it.javaClass in enabled }
    }

    private fun List<ValidationRule>.filterDisabled(disabled: List<Class<in ValidationRule>>): List<ValidationRule> {
        if (disabled.isEmpty()) return this
        return filter { it.javaClass !in disabled }
    }

    fun checkRules(
        topicName: TopicName, presentOnCluster: Boolean,
        properties: TopicProperties, config: TopicConfigMap,
        clusterMetadata: ClusterMetadata,
        topicDescription: TopicDescription
    ): List<RuleViolation> {
        val topicDescriptionView = TopicDescriptionView(
            name = topicName,
            properties = properties,
            config = config,
            presentOnCluster = presentOnCluster,
            originalDescription = topicDescription,
        )
        return rules.mapNotNull { it.check(topicDescriptionView, clusterMetadata) }
    }

    fun fixConfigViolations(topicDescription: TopicDescription, clusters: List<ClusterMetadata>): TopicDescription {
        var resultTopicDescription = topicDescription
        for (clusterMetadata in clusters) {
            for (rule in rules) {
                resultTopicDescription = rule.fixConfig(resultTopicDescription, clusterMetadata)
            }
        }
        return resultTopicDescription
    }
}