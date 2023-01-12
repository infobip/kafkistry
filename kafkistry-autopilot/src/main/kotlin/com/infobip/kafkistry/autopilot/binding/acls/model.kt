package com.infobip.kafkistry.autopilot.binding.acls

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.acl.AclRuleStatus

const val ACL_TARGET_TYPE: ActionTargetType = "ACL"
const val PRINCIPAL_ATTR: String = "principal"
const val ACL_RULE_ATTR: String = "aclRule"

sealed interface AclAutopilotAction : AutopilotAction {
    val clusterRef: ClusterRef
    val aclStatus: AclRuleStatus
    val description: ActionDescription

    override val metadata: ActionMetadata
        get() = ActionMetadata(
            actionIdentifier = javaClass.name + ": " + aclStatus.rule.asString() + " @ " + clusterRef.identifier,
            description, clusterRef, mapOf(
                CLUSTER_IDENTIFIER_ATTR to clusterRef.identifier,
                PRINCIPAL_ATTR to aclStatus.rule.principal,
                ACL_RULE_ATTR to aclStatus.rule.asString(),
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
