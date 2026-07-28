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
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.InvalidCsrfTokenException
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.*
import tools.jackson.databind.json.JsonMapper

@Component
class UnauthorizedEntryPoint(
    httpProperties: WebHttpProperties,
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    private val errorPage: String = "${httpProperties.rootPath}/error"
    private val loginPage: String = "${httpProperties.rootPath}/login"

    private val loginCallMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, loginPage)
    private val errorCallMatcher = PathPatternRequestMatcher.pathPattern(errorPage)

    private val apiCallMatcher: RequestMatcher = OrRequestMatcher(
        //don't redirect to /login when not authenticated using rest /api/** or rendered ajax calls
        PathPatternRequestMatcher.pathPattern("${httpProperties.rootPath}/api/**"),
        ELRequestMatcher("hasHeader('ajax', 'true')"),
    )

    private val defaultDeniedHandler = AccessDeniedHandlerImpl().apply {
        setErrorPage(errorPage)
    }

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
        if (apiCallMatcher.matches(request) || loginCallMatcher.matches(request) || errorCallMatcher.matches(request)) {
            sendErrorResponse(HttpStatus.FORBIDDEN, response, accessDeniedException)
        } else {
            defaultDeniedHandler.handle(request, response, accessDeniedException)
        }
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
        jsonMapper.writeValue(response.outputStream, apiError)
    }
}

