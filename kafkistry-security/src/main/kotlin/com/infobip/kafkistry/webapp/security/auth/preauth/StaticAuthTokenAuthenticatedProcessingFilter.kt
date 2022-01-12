package com.infobip.kafkistry.webapp.security.auth.preauth

import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.auth.StaticUsers
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

const val ACCESS_TOKEN = "X-Auth-Token"

@Component
class StaticAuthTokenAuthenticatedProcessingFilter(
        staticUsers: StaticUsers
) : PreAuthUserResolver {

    private val users: Map<String, User> = staticUsers.users
            .mapNotNull { it.token?.let { token -> token to it.user } }
            .associate { it }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): User? {
        val token = request.getHeader(ACCESS_TOKEN)
                ?: request.getParameter(ACCESS_TOKEN)
                ?: return null
        return users[token]
    }

}