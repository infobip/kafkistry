package com.infobip.kafkistry.webapp.security

import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import java.util.function.Supplier

data class DescribedAuthorizationDecision(
    val description: String,
    val decision: AuthorizationDecision,
): AuthorizationDecision(decision.isGranted)

class DescribedAuthorizationManager(
    private val delegate: AuthorizationManager<RequestAuthorizationContext>,
    private val description: String,
) : AuthorizationManager<RequestAuthorizationContext> {

    override fun check(
        authentication: Supplier<Authentication?>,
        `object`: RequestAuthorizationContext,
    ): AuthorizationDecision? {
        val decision = delegate.check(authentication, `object`)
        return if (decision == null) {
            null
        } else {
            DescribedAuthorizationDecision(description, decision)
        }
    }

}