package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.TOPIC_CONFIG_PROPERTIES
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.ConfigValueInspector
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.renderMessage
import com.infobip.kafkistry.service.topic.RuleViolationIssue

interface ValidationRule {

    fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation?

    fun fixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        if (!isViolated(topicDescription, clusterMetadata)) {
            return topicDescription
        }
        return doFixConfig(topicDescription, clusterMetadata)
    }

    fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription

    fun isViolated(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): Boolean {
        val clusterDefaults = clusterMetadata.info?.config?.let { clusterConfig ->
            TOPIC_CONFIG_PROPERTIES.associateWith {
                ConfigValueInspector().clusterDefaultValue(clusterConfig, it)?.value
            }
        }.orEmpty()
        val topicDescriptionView = TopicDescriptionView(
                name = topicDescription.name,
                properties = topicDescription.propertiesForCluster(clusterMetadata.ref),
                config = clusterDefaults + topicDescription.configForCluster(clusterMetadata.ref),
                presentOnCluster = topicDescription.presence.needToBeOnCluster(clusterMetadata.ref),
                originalDescription = topicDescription
        )
        val violation = check(topicDescriptionView, clusterMetadata)
        return violation != valid()
    }

    fun violated(
        message: String,
        placeholders: Map<String, Placeholder> = emptyMap(),
        severity: RuleViolation.Severity = RuleViolation.Severity.WARNING
    ) = RuleViolation(
        severity = severity,
        ruleClassName = javaClass.name,
        message = message,
        placeholders = placeholders,
    )

    fun valid(): RuleViolation? = null
}

data class ClusterMetadata(
    val ref: ClusterRef,
    val info: ClusterInfo? = null,
)

data class TopicDescriptionView(
    val name: TopicName,
    val properties: TopicProperties,
    val config: TopicConfigMap,
    val presentOnCluster: Boolean,
    val originalDescription: TopicDescription,
)

fun RuleViolationIssue.renderMessage(): String = violation.renderMessage()