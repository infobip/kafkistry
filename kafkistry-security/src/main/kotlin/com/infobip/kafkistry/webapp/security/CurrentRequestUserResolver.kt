package com.infobip.kafkistry.webapp.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CurrentRequestUserResolver {

    private val unknown = User("Unknown", "Unknown", "Unknown", "Unknown", UserRole.USER)
    private val scopedUser: ThreadLocal<User> = ThreadLocal()

    fun resolveUser(): User? {
        scopedUser.get()?.run { return this }
        val context = SecurityContextHolder.getContext()
        val authentication = context.authentication ?: return null
        val userObj = authentication.principal
        return if (userObj is User) userObj else null
    }

    fun resolveUserOrUnknown(): User = resolveUser() ?: unknown

    fun <T> withUser(user: User, block: () -> T): T {
        return try {
            scopedUser.set(user)
            block()
        } finally {
            scopedUser.remove()
        }
    }

}