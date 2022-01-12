package com.infobip.kafkistry.webapp.security.auth.providers

import com.infobip.kafkistry.webapp.security.User
import org.springframework.core.Ordered
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

/**
 * This is the last ordered auth provider which always throws [BadCredentialsException].
 * It is a fallback option if none of the other auth providers did not perform successfull authentication.
 */
@Component
class BadCredentialsAuthProvider : KafkistryUsernamePasswordAuthProvider {

    override fun authenticateByUsernamePassword(
        usernamePasswordAuth: UsernamePasswordAuthenticationToken
    ): User? = throw BadCredentialsException("Invalid login credentials")

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE

}