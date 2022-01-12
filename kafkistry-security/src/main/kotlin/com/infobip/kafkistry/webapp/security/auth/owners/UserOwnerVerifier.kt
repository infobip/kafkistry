package com.infobip.kafkistry.webapp.security.auth.owners

import com.infobip.kafkistry.webapp.security.User

interface UserOwnerVerifier {

    /**
     * Verify if user is specified owner.
     * @return `true` - when [user] is specified [owner], `false` - when [user] is NOT specified [owner]
     */
    fun isUserOwner(user: User, owner: String): Boolean
}