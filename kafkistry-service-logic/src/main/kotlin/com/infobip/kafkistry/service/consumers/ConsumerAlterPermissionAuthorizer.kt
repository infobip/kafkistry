package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.model.AclOperation.Policy.ALLOW
import com.infobip.kafkistry.model.AclOperation.Type.ALL
import com.infobip.kafkistry.model.AclOperation.Type.READ
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.KafkistryPermissionException
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.auth.owners.UserOwnerVerifier
import com.infobip.kafkistry.webapp.security.isAdmin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.security.consumer-groups")
class ConsumerGroupAccessProperties {
    var allowNoOwnersAccess = false
    var onlyAdmin = false
}

@Component
class ConsumerAlterPermissionAuthorizer(
    private val currentRequestUserResolver: CurrentRequestUserResolver,
    private val aclLinkResolver: AclLinkResolver,
    private val aclsRegistry: AclsRegistryService,
    private val userIsOwnerVerifiers: List<UserOwnerVerifier>,
    private val accessProperties: ConsumerGroupAccessProperties,
) {

    fun authorizeGroupAccess(
        consumerGroupId: ConsumerGroupId,
        clusterIdentifier: KafkaClusterIdentifier,
    ) {
        val user = currentRequestUserResolver.resolveUserOrUnknown()
        if (user.isAdmin()) {
            return
        } else if (accessProperties.onlyAdmin) {
            throw KafkistryPermissionException("Only admins can alter consumer groups")
        }
        val aclRules = aclLinkResolver.findConsumerGroupAffectingAclRules(consumerGroupId, clusterIdentifier)
        if (aclRules.isEmpty() && !accessProperties.allowNoOwnersAccess) {
            throw KafkistryPermissionException("Group '$consumerGroupId' has no affecting ACL rules to infer owner(s)")
        }
        val allowedOwners = aclRules.asSequence()
            .filter { it.operation.policy == ALLOW && it.operation.type in setOf(ALL, READ)}
            .mapNotNull { aclsRegistry.findPrincipalAcls(it.principal) }
            .flatMap { it.owner.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (allowedOwners.isEmpty()) {
            if (accessProperties.allowNoOwnersAccess) {
                return
            }
            throw KafkistryPermissionException("Group '$consumerGroupId' has no allowing ACL principal's owners")
        }
        val userIsOwner = userIsOwnerVerifiers.any { verifier ->
            allowedOwners.any { owner -> verifier.isUserOwner(user, owner) }
        }
        if (!userIsOwner) {
            val msg = if (allowedOwners.size == 1) {
                "You are not member of owner's group '${allowedOwners.first()}' which owns consumer group '$consumerGroupId'"
            } else {
                "You are not member of any of owner's groups '${allowedOwners}' which are owning consumer group '$consumerGroupId'"
            }
            throw KafkistryPermissionException(msg)
        }
    }

}