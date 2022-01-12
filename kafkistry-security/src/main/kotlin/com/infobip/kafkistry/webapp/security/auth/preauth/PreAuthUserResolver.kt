package com.infobip.kafkistry.webapp.security.auth.preauth

import com.infobip.kafkistry.webapp.security.User
import javax.servlet.http.HttpServletRequest

interface PreAuthUserResolver {

    /**
     * Resolve pre-authenticated user based on http request (commonly by headers)
     * @return authenticated User or null
     */
    fun getPreAuthenticatedPrincipal(request: HttpServletRequest): User?
}