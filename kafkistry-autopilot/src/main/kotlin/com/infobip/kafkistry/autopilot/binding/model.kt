package com.infobip.kafkistry.autopilot.binding

import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.renderMessage
import kotlin.reflect.KClass

const val CLUSTER_IDENTIFIER_ATTR: String = "clusterIdentifier"

/**
 * Globally unique identifier for some [AutopilotAction].
 * Two equal [AutopilotAction]s must have equal [AutopilotActionIdentifier]s.
 */
typealias AutopilotActionIdentifier = String

/**
 * Tells on which _type_ [AutopilotAction] operates on. (`TOPIC`, `ACL`,...)
 */
typealias ActionTargetType = String

/**
 * Root abstraction representing some _action_ that can be performed by autopilot.
 *
 */
interface AutopilotAction {
    val metadata: ActionMetadata
    val actionIdentifier: AutopilotActionIdentifier get() = metadata.actionIdentifier
}

/**
 * Describes particular implementation/class of [AutopilotAction]
 */
data class ActionDescription(
    val actionName: String,
    val actionClass: String,
    val targetType: ActionTargetType,
    val doc: String,
) {
    companion object {
        fun of(clazz: KClass<out AutopilotAction>, targetType: ActionTargetType, doc: String) = ActionDescription(
            actionName = clazz.java.simpleName,
            actionClass = clazz.java.name,
            targetType = targetType,
            doc = doc,
        )
    }
}

/**
 * Describes particular instance of [AutopilotAction]
 */
data class ActionMetadata(
    val actionIdentifier: AutopilotActionIdentifier,
    val description: ActionDescription,
    val clusterRef: ClusterRef? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * Explains why some [AutopilotAction] can't/shouldn't be executed.
 */
data class AutopilotActionBlocker(
    val message: String,
    val placeholders: Map<String, Placeholder> = emptyMap(),
)

fun AutopilotActionBlocker.renderMessage(): String = renderMessage(message, placeholders)

data class ClusterUnstable(
    val stateType: StateType,
    val stateTypeName: String,
    val timestamp: Long,
)

