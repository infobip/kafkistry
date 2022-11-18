package com.infobip.kafkistry.autopilot

import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.UserRole
import org.springframework.stereotype.Component

@Component
class AutopilotUser(
    hostnameResolver: HostnameResolver,
    private val userResolver: CurrentRequestUserResolver,
) {

    val user = User(
        username = "kafkistry-autopilot-${hostnameResolver.hostname}",
        firstName = "Autopilot",
        lastName = "[${hostnameResolver.hostname}]",
        email = "kafkistry@${hostnameResolver.hostname}",
        role = UserRole.ADMIN,
        attributes = mapOf("hostname" to hostnameResolver.hostname),
    )

    /**
     * Execute [block] as _Autopilot_ user so that any nested interceptors (i.e. Auditing, Logging) have some
     * concrete [User] bounded to current Thread to resolve as actor which is performing [block] execution.
     */
    fun <T> execAsAutopilot(block: () -> T): T {
        return userResolver.withUser(user, block)
    }
}