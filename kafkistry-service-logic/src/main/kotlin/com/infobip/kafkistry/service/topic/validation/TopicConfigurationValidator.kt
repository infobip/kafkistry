package com.infobip.kafkistry.service.topic.validation

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.validation.rules.ClusterMetadata
import com.infobip.kafkistry.service.topic.validation.rules.ValidationRule
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.filterDisabled
import com.infobip.kafkistry.service.filterEnabled
import com.infobip.kafkistry.service.topic.ExistingTopicInfo
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

    fun checkRules(
        topicName: TopicName, presentOnCluster: Boolean,
        properties: TopicProperties, config: TopicConfigMap,
        clusterMetadata: ClusterMetadata,
        topicDescription: TopicDescription,
        existingTopicInfo: ExistingTopicInfo?,
    ): List<RuleViolation> {
        val topicDescriptionView = TopicDescriptionView(
            name = topicName,
            properties = properties,
            config = config,
            presentOnCluster = presentOnCluster,
            originalDescription = topicDescription,
            existingTopicInfo = existingTopicInfo,
        )
        return rules.mapNotNull { it.check(topicDescriptionView, clusterMetadata) }
    }

    fun fixConfigViolations(topicDescription: TopicDescription, clusters: List<Pair<ClusterMetadata, ExistingTopicInfo?>>): TopicDescription {
        var resultTopicDescription = topicDescription
        for ((clusterMetadata, existingTopicInfo) in clusters) {
            for (rule in rules) {
                resultTopicDescription = rule.fixConfig(resultTopicDescription, clusterMetadata, existingTopicInfo)
            }
        }
        return resultTopicDescription
    }
}