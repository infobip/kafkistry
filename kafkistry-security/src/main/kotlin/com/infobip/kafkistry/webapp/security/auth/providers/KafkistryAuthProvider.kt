package com.infobip.kafkistry.webapp.security.auth.providers

import com.infobip.kafkistry.webapp.security.User
import org.springframework.core.Ordered
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication

interface KafkistryAuthProvider : AuthenticationProvider, Ordered

interface KafkistryUsernamePasswordAuthProvider : KafkistryAuthProvider {

    override fun authenticate(authentication: Authentication): Authentication? {
        val user = authenticateByUsernamePassword(authentication as UsernamePasswordAuthenticationToken)
            ?: return null
        return UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    /**
     * Authenticate user by username and password.
     *
     * Authentication might have one of following outcomes:
     *  - return successfully authenticated [User]
     *  - return `null` in case if this auth provider does not have this user / not capable of authenticating it, this gives chance
     *    to other [KafkistryAuthProvider] to do the authentication.
     *  - throw any kind of exception. For example if the implementation requires network remote call, and it's unreachable.
     */
    fun authenticateByUsernamePassword(usernamePasswordAuth: UsernamePasswordAuthenticationToken): User?

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }
}