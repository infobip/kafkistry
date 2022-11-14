package com.infobip.kafkistry.autopilot.binding.acls

import com.infobip.kafkistry.autopilot.binding.ActionDescription
import com.infobip.kafkistry.autopilot.binding.ActionMetadata
import com.infobip.kafkistry.autopilot.binding.ActionTargetType
import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.acl.AclRuleStatus

const val ACL_TARGET_TYPE: ActionTargetType = "ACL"

sealed interface AclAutopilotAction : AutopilotAction {
    val clusterRef: ClusterRef
    val aclStatus: AclRuleStatus
    val description: ActionDescription

    override val metadata: ActionMetadata
        get() = ActionMetadata(
            actionIdentifier = javaClass.name + ": " + aclStatus.rule.asString() + " @ " + clusterRef,
            description, clusterRef, mapOf(
                "clusterIdentifier" to clusterRef.identifier,
                "principal" to aclStatus.rule.principal,
                "aclRule" to aclStatus.rule.asString(),
            )
        )
}

data class CreateMissingAclAction(
    override val clusterRef: ClusterRef,
    override val aclStatus: AclRuleStatus,
) : AclAutopilotAction {
    override val description: ActionDescription get() = DESCRIPTION

    companion object {
        val DESCRIPTION = ActionDescription.of(
            clazz = CreateMissingAclAction::class,
            targetType = ACL_TARGET_TYPE,
            doc = "Action which creates missing ACL rule on cluster that is expected to exist but it does not.",
        )
    }
}

data class DeleteUnwantedAclAction(
    override val clusterRef: ClusterRef,
    override val aclStatus: AclRuleStatus,
) : AclAutopilotAction {
    override val description: ActionDescription get() = DESCRIPTION

    companion object {
        val DESCRIPTION = ActionDescription.of(
            clazz = DeleteUnwantedAclAction::class,
            targetType = ACL_TARGET_TYPE,
            doc = "Action which deletes unwanted ACL rule from cluster. " +
                    "Such ACL rule either doesn't exist in Kafkistry's repository or it's configured not to exist on this kafka cluster.",
        )
    }

}
