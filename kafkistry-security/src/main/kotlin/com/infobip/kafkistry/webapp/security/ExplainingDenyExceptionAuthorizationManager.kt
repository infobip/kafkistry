package com.infobip.kafkistry.webapp.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorityAuthorizationDecision
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import java.util.function.Supplier

class ExplainingDenyExceptionAuthorizationManager(
    private val delegate: AuthorizationManager<HttpServletRequest>,
    private val helpMessage: String?,
) : AuthorizationManager<HttpServletRequest> {

    override fun check(
        authentication: Supplier<Authentication?>,
        `object`: HttpServletRequest,
    ): AuthorizationDecision? {
        val decision: AuthorizationDecision? = delegate.check(authentication, `object`)
        if (decision != null && !decision.isGranted) {
            val auth: Authentication? = authentication.get()
            throw AccessDeniedException(explain(decision, auth, `object`))
        }
        return decision
    }

    private fun explain(
        decision: AuthorizationDecision,
        authentication: Authentication?,
        request: HttpServletRequest,
    ): String {
        val user = authentication?.principal as? User
        val explainParts = buildList {
            add("Deny details:")
            if (user != null) {
                add("User: '${user.username}'")
            } else {
                add("User: unauthenticated/anonymous")
            }
            add("Access request: ${request.method} ${request.requestURI}")
            add("Cause: " + cause(decision, user?.authorities?.toList().orEmpty().map { it.authority }))
            if (!helpMessage.isNullOrBlank()) {
                add("")
                add(helpMessage)
            }
        }
        return explainParts.joinToString(separator = "\n")
    }

    private fun cause(decision: AuthorizationDecision, authorities: List<String>): String {
        return when (decision) {
            is AuthorityAuthorizationDecision -> "\n - required authorities: ${decision.authorities}\n - having authorities: $authorities"
            else -> decision.toString()
        }
    }
}