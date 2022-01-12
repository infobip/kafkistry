package com.infobip.kafkistry.webapp.security.auth.providers

import com.infobip.kafkistry.webapp.security.User
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.stereotype.Component

@Component
class PreAuthenticatedUserAuthProvider : KafkistryAuthProvider {

    private val delegate = PreAuthenticatedAuthenticationProvider().apply {
        setPreAuthenticatedUserDetailsService { token -> token.principal as User }
    }

    override fun authenticate(authentication: Authentication?): Authentication = delegate.authenticate(authentication)

    override fun supports(authentication: Class<*>?): Boolean = delegate.supports(authentication)

    override fun getOrder(): Int = 0
}