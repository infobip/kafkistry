package com.infobip.kafkistry.webapp.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentRequestUserResolver {

    private val unknown = User("Unknown", "Unknown", "Unknown", "Unknown", UserRole.USER)

    fun resolveUser(): User? {
        val context = SecurityContextHolder.getContext()
        val authentication = context.authentication ?: return null
        val userObj = authentication.principal
        return if (userObj is User) userObj else null
    }

    fun resolveUserOrUnknown(): User = resolveUser() ?: unknown

}