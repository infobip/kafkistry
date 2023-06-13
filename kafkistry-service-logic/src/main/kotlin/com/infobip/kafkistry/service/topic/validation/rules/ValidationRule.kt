package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.TOPIC_CONFIG_PROPERTIES
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.renderMessage
import com.infobip.kafkistry.service.topic.*

interface ValidationRule {

    fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation?

    fun fixConfig(
        topicDescription: TopicDescription, clusterMetadata: ClusterMetadata, existingTopicInfo: ExistingTopicInfo?,
    ): TopicDescription {
        if (!isViolated(topicDescription, clusterMetadata, existingTopicInfo)) {
            return topicDescription
        }
        return doFixConfig(topicDescription, clusterMetadata, existingTopicInfo)
    }

    fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription = topicDescription
    fun doFixConfig(
        topicDescription: TopicDescription, clusterMetadata: ClusterMetadata, existingTopicInfo: ExistingTopicInfo?
    ): TopicDescription = doFixConfig(topicDescription, clusterMetadata)

    fun topicDescriptionViewOf(
        topicDescription: TopicDescription,
        clusterMetadata: ClusterMetadata,
        existingTopicInfo: ExistingTopicInfo?,
    ): TopicDescriptionView {
        val clusterDefaults = clusterMetadata.info?.config?.let { clusterConfig ->
            TOPIC_CONFIG_PROPERTIES.associateWith {
                ConfigValueInspector().clusterDefaultValue(clusterConfig, it)?.value
            }
        }.orEmpty()
        return TopicDescriptionView(
            name = topicDescription.name,
            properties = topicDescription.propertiesForCluster(clusterMetadata.ref),
            config = clusterDefaults + topicDescription.configForCluster(clusterMetadata.ref),
            presentOnCluster = topicDescription.presence.needToBeOnCluster(clusterMetadata.ref),
            originalDescription = topicDescription,
            existingTopicInfo = existingTopicInfo,
        )
    }

    fun isViolated(
        topicDescription: TopicDescription,
        clusterMetadata: ClusterMetadata,
        existingTopicInfo: ExistingTopicInfo?,
    ): Boolean {
        val topicDescriptionView = topicDescriptionViewOf(topicDescription, clusterMetadata, existingTopicInfo)
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
    val existingTopicInfo: ExistingTopicInfo?,
)

fun RuleViolationIssue.renderMessage(): String = violation.renderMessage()
