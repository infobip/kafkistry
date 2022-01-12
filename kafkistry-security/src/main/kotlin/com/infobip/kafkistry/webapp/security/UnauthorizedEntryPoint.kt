package com.infobip.kafkistry.webapp.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.infobip.kafkistry.api.exception.ApiError
import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.webapp.WebHttpProperties
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.InvalidCsrfTokenException
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.ELRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class UnauthorizedEntryPoint(
    httpProperties: WebHttpProperties,
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    private var apiCallMatcher: RequestMatcher = OrRequestMatcher(
        //don't redirect to /login when not authenticated using rest /api/** or rendered ajax calls
        AntPathRequestMatcher("${httpProperties.rootPath}/api/**"),
        ELRequestMatcher("hasHeader('ajax', 'true')")
    )

    private var errorPage: String = "${httpProperties.rootPath}/error"

    fun apiCallMatcher(): RequestMatcher = apiCallMatcher

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) = sendErrorResponse(HttpStatus.UNAUTHORIZED, response, authException)

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        if (!apiCallMatcher.matches(request)) {
            // Put exception into request scope (perhaps of use to a view)
            request.setAttribute(WebAttributes.ACCESS_DENIED_403, accessDeniedException)
            response.status = HttpStatus.FORBIDDEN.value()
            request.getRequestDispatcher(errorPage).forward(request, response)
            return
        }
        sendErrorResponse(HttpStatus.FORBIDDEN, response, accessDeniedException)
    }

    private fun sendErrorResponse(
        status: HttpStatus,
        response: HttpServletResponse,
        cause: Exception
    ) {
        response.status = status.value()
        val causeMsg = when (cause) {
            is InsufficientAuthenticationException -> "You need to logged-in to do this action, please refresh page"
            is InvalidCsrfTokenException -> "Invalid csrf token, either there was CSRF attack or you have page loaded with old session, please refresh page"
            else -> cause.deepToString()
        }
        val apiError = ApiError(
            status = status,
            message = "${status.name}\nCause: $causeMsg"
        )
        response.contentType = "application/json"
        objectMapper.writeValue(response.outputStream, apiError)
    }
}

