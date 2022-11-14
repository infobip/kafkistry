package com.infobip.kafkistry.autopilot.binding.topics

import com.infobip.kafkistry.api.ConsumersApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.api.ManagementApi
import com.infobip.kafkistry.autopilot.binding.ActionDescription
import com.infobip.kafkistry.autopilot.binding.AutopilotActionBlocker
import com.infobip.kafkistry.autopilot.binding.AutopilotBinding
import com.infobip.kafkistry.autopilot.binding.CommonBlockerChecker
import com.infobip.kafkistry.kafka.ConsumerGroupStatus
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.topic.AvailableAction.*
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_REPLICATION_THROTTLING
import com.infobip.kafkistry.service.topic.clusterRef
import com.infobip.kafkistry.service.topic.inspectors.EMPTY
import org.springframework.stereotype.Component

@Component
class TopicsAutopilotBinding(
    private val inspectApi: InspectApi,
    private val consumersApi: ConsumersApi,
    private val managementApi: ManagementApi,
    private val commonBlockerChecker: CommonBlockerChecker,
) : AutopilotBinding<TopicAutopilotAction> {

    override fun listCapabilities(): List<ActionDescription> = listOf(
        CreateMissingTopicAction.DESCRIPTION,
        DeleteUnwantedTopicAction.DESCRIPTION,
        AlterTopicConfigurationAction.DESCRIPTION,
    )

    override fun actionsToProcess(): List<TopicAutopilotAction> {
        return (inspectApi.inspectTopics() + inspectApi.inspectUnknownTopics()).asSequence()
            .flatMap { it.statusPerClusters.map { clusterStatus -> it.topicName to clusterStatus } }
            .mapNotNull { (topicName, clusterStatus) ->
                val inspectionResult = clusterStatus.status
                val actions = inspectionResult.availableActions
                val clusterRef = clusterStatus.clusterRef()
                when {
                    CREATE_TOPIC in actions -> CreateMissingTopicAction(topicName, clusterRef, inspectionResult)
                    DELETE_TOPIC_ON_KAFKA in actions -> DeleteUnwantedTopicAction(topicName, clusterRef, inspectionResult)
                    ALTER_TOPIC_CONFIG in actions -> {
                        val expectedTopicConfig = inspectApi
                            .inspectTopicNeededConfigChangesOnCluster(topicName, clusterRef.identifier)
                            .associate { it.key to it.newValue }
                        AlterTopicConfigurationAction(topicName, clusterRef, expectedTopicConfig, inspectionResult)
                    }
                    else -> null
                }
            }
            .toList()
    }

    override fun checkBlockers(action: TopicAutopilotAction): List<AutopilotActionBlocker> {
        return when (action) {
            is CreateMissingTopicAction -> listOfNotNull(
                commonBlockerChecker.allBrokersOnline(action.clusterRef.identifier),
                commonBlockerChecker.clusterHavingOverPromisedRetention(action.clusterRef.identifier),
            )
            is DeleteUnwantedTopicAction -> listOfNotNull(
                commonBlockerChecker.allBrokersOnline(action.clusterRef.identifier),
                action.inspectionResult.types.takeUnless { EMPTY in it }?.let {
                    AutopilotActionBlocker(message = "Topic is not empty")
                },
                consumersApi.clusterTopicConsumers(action.clusterRef.identifier, action.topicName)
                    .filter { it.status !in setOf(ConsumerGroupStatus.EMPTY, ConsumerGroupStatus.DEAD) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { groups ->
                        AutopilotActionBlocker(
                            message = "There are %NUM_GROUPS% consuming from topic, groups: %GROUP_IDS%",
                            placeholders = mapOf(
                                "NUM_GROUPS" to Placeholder("groups.count", groups.size),
                                "GROUP_IDS" to Placeholder("group.ids", groups.map { it.groupId }),
                            )
                        )
                    },
            )
            is AlterTopicConfigurationAction -> listOfNotNull(
                commonBlockerChecker.allBrokersOnline(action.clusterRef.identifier),
                action.inspectionResult.types.takeIf { HAS_REPLICATION_THROTTLING in it }?.let {
                    AutopilotActionBlocker(message = "Topic is being throttled")
                },
                action.inspectionResult.types.takeIf { CONFIG_RULE_VIOLATIONS in it }?.let {
                    AutopilotActionBlocker(message = "Config has rule violation(s)")
                },
            )
        }
    }

    override fun processAction(action: TopicAutopilotAction) {
        when (action) {
            is CreateMissingTopicAction -> managementApi.createMissingTopicOnCluster(
                topicName = action.topicName,
                clusterIdentifier = action.clusterRef.identifier,
            )
            is DeleteUnwantedTopicAction -> managementApi.deleteTopicOnCluster(
                topicName = action.topicName,
                clusterIdentifier = action.clusterRef.identifier,
            )
            is AlterTopicConfigurationAction -> managementApi.updateTopicOnClusterToExpectedConfig(
                topicName = action.topicName,
                clusterIdentifier = action.clusterRef.identifier,
                expectedTopicConfig = action.expectedTopicConfig,
            )
        }
    }

}