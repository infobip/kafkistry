package com.infobip.kafkistry.webapp.security.auth.owners

import com.infobip.kafkistry.service.existingvalues.ExistingValuesSupplier
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.auth.StaticUsers
import org.springframework.stereotype.Component

@Component
class StaticUsersOwnerGroupsVerifier(
    staticUsers: StaticUsers
) : UserOwnerVerifier, ExistingValuesSupplier {

    private val owners = staticUsers.owners()

    private val ownerUsers = staticUsers.ownersUsers
        .flatMap { (owner, usernames) -> usernames.map { owner to it } }
        .toSet()

    override fun owners(): List<String> = owners

    override fun isUserOwner(user: User, owner: String): Boolean {
        return (owner to user.username) in ownerUsers
    }

}