package com.infobip.kafkistry.webapp.security.auth.preauth

import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import jakarta.servlet.http.HttpServletRequest

class PreAuthenticatedUserProcessingFiler(
        private val resolvers: List<PreAuthUserResolver>
)  : AbstractPreAuthenticatedProcessingFilter() {

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): Any? {
        return resolvers.asSequence()
                .mapNotNull { it.getPreAuthenticatedPrincipal(request) }
                .firstOrNull()
    }

    override fun getPreAuthenticatedCredentials(request: HttpServletRequest): Any {
        return "N/A"
    }

}