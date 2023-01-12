package com.infobip.kafkistry.autopilot.service

import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.binding.AutopilotBinding
import com.infobip.kafkistry.autopilot.binding.CLUSTER_IDENTIFIER_ATTR
import com.infobip.kafkistry.autopilot.binding.acls.PRINCIPAL_ATTR
import com.infobip.kafkistry.autopilot.binding.topics.TOPIC_NAME_ATTR
import com.infobip.kafkistry.autopilot.config.ActionsCycleProperties
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryIntegrityException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
class AutopilotService(
    private val actionsRepository: ActionsRepository,
    private val cycleProperties: ActionsCycleProperties,
    private val enabledProperties: AutopilotEnabledFilterProperties,
    private val bindings: List<AutopilotBinding<out AutopilotAction>>,
) {

    fun listActionFlows(): List<ActionFlow> = actionsRepository.findAll()

    fun getActionFlow(actionIdentifier: AutopilotActionIdentifier): ActionFlow {
        return actionsRepository.find(actionIdentifier)
            ?: throw KafkistryIntegrityException("No action with identifier '$actionIdentifier' found in repository")
    }

    fun autopilotStatus(): AutopilotStatus {
        return AutopilotStatus(
            cyclePeriodMs = cycleProperties.repeatDelayMs,
            enableFilter = enabledProperties,
            possibleActions = bindings.flatMap { it.listCapabilities() }
        )
    }

    fun findClusterActions(clusterIdentifier: KafkaClusterIdentifier): List<ActionFlow> = findActions(
        attributes = mapOf(CLUSTER_IDENTIFIER_ATTR to clusterIdentifier)
    )

    fun findTopicActions(topicName: TopicName): List<ActionFlow> = findActions(
        attributes = mapOf(TOPIC_NAME_ATTR to topicName)
    )

    fun findTopicOnClusterActions(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
    ): List<ActionFlow> = findActions(
        attributes = mapOf(TOPIC_NAME_ATTR to topicName, CLUSTER_IDENTIFIER_ATTR to clusterIdentifier)
    )

    fun findPrincipalActions(principalId: PrincipalId): List<ActionFlow> = findActions(
        attributes = mapOf(PRINCIPAL_ATTR to principalId)
    )

    fun findActions(attributes: Map<String, String>): List<ActionFlow> {
        return actionsRepository.findBy {
            attributes.all { (key, value) -> it.attributes[key] == value }
        }
    }

}