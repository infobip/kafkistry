package com.infobip.kafkistry.ownership

import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.ownership.ConsumerGroupToPrincipalResolver
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.auth.owners.UserOwnerVerifier
import org.springframework.stereotype.Component

@Component
class UserOwnershipClassifier(
    private val currentRequestUserResolver: CurrentRequestUserResolver,
    private val consumerGroupOwnerResolver: ConsumerGroupToPrincipalResolver,
    private val userOwnerVerifiers: List<UserOwnerVerifier>,
) {

    fun isOwnerOfTopic(topic: TopicDescription?): Boolean {
        return topic?.owner?.let { isUserOwner(it) } ?: false
    }

    fun isOwnerOfConsumerGroup(consumerGroupId: ConsumerGroupId): Boolean {
        return consumerGroupOwnerResolver.resolvePrincipalOwner(consumerGroupId)
            ?.let { principalOwners -> principalOwners.owners.any { isUserOwner(it) } }
            ?: false
    }

    fun isOwnerOfPrincipal(principal: PrincipalAclRules?): Boolean {
        return principal?.owner?.let { isUserOwner(it) } ?: false
    }

    fun isUserOwner(ownersCsv: String): Boolean {
        val owners = ownersCsv.split(',').map { it.trim() }
        val user = currentRequestUserResolver.resolveUser() ?: return false
        return userOwnerVerifiers.any { verifier ->
            owners.any { verifier.isUserOwner(user, it) }
        }
    }
}