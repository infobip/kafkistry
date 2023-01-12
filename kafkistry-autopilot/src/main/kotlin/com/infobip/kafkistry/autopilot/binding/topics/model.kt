package com.infobip.kafkistry.autopilot.binding.topics

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicConfigMap
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.TopicOnClusterInspectionResult

const val TOPIC_TARGET_TYPE: ActionTargetType = "TOPIC"
const val TOPIC_NAME_ATTR: String = "topicName"

sealed interface TopicAutopilotAction : AutopilotAction {
    val topicName: TopicName
    val clusterRef: ClusterRef
    val inspectionResult: TopicOnClusterInspectionResult
    val description: ActionDescription

    override val metadata: ActionMetadata
        get() = ActionMetadata(
            actionIdentifier = javaClass.name + ": " + topicName +  "@ " + clusterRef.identifier,
            description, clusterRef, mapOf(
                CLUSTER_IDENTIFIER_ATTR to clusterRef.identifier,
                TOPIC_NAME_ATTR to topicName,
            )
        )
}

data class CreateMissingTopicAction(
    override val topicName: TopicName,
    override val clusterRef: ClusterRef,
    override val inspectionResult: TopicOnClusterInspectionResult,
) : TopicAutopilotAction {
    override val description: ActionDescription get() = DESCRIPTION

    companion object {
        val DESCRIPTION = ActionDescription.of(
            clazz = CreateMissingTopicAction::class,
            targetType = TOPIC_TARGET_TYPE,
            doc = "Action which creates missing topic on cluster that is expected to exist but it does not.",
        )
    }
}

data class DeleteUnwantedTopicAction(
    override val topicName: TopicName,
    override val clusterRef: ClusterRef,
    override val inspectionResult: TopicOnClusterInspectionResult,
) : TopicAutopilotAction {
    override val description: ActionDescription get() = DESCRIPTION

    companion object {
        val DESCRIPTION = ActionDescription.of(
            clazz = DeleteUnwantedTopicAction::class,
            targetType = TOPIC_TARGET_TYPE,
            doc = "Action which deletes unwanted topic from cluster. " +
                    "Such topic either doesn't exist in Kafkistry's repository or it's configured not to exist on this kafka cluster.",
        )
    }
}

data class AlterTopicConfigurationAction(
    override val topicName: TopicName,
    override val clusterRef: ClusterRef,
    val expectedTopicConfig: TopicConfigMap,
    override val inspectionResult: TopicOnClusterInspectionResult,
) : TopicAutopilotAction {
    override val description: ActionDescription get() = DESCRIPTION

    companion object {
        val DESCRIPTION = ActionDescription.of(
            clazz = AlterTopicConfigurationAction::class,
            targetType = TOPIC_TARGET_TYPE,
            doc = "Action which alters topic's configuration properties on cluster. " +
                    "End goal is to achieve that all topic's properties have values expected by definition in Kafkistry's repository or kafka server's defaults.",
        )
    }
}
