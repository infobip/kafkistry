package com.infobip.kafkistry.webapp.security

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface NoSessionRequestMatcher : RequestMatcher {
    companion object {
        val NONE = object : NoSessionRequestMatcher {
            override fun matches(request: HttpServletRequest): Boolean = false
        }

        fun of(matcher: RequestMatcher) = object : NoSessionRequestMatcher {
            override fun matches(request: HttpServletRequest): Boolean = matcher.matches(request)
        }

        fun ofAll(matchers: Collection<RequestMatcher>) = object : NoSessionRequestMatcher {
            override fun matches(request: HttpServletRequest): Boolean = matchers.any { it.matches(request) }
        }
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExcludeSessionRepositoryFilter(
    requestMatchers: Optional<List<NoSessionRequestMatcher>>
) : OncePerRequestFilter() {

    private val matcher = requestMatchers
        .map { NoSessionRequestMatcher.ofAll(it) }
        .orElse(NoSessionRequestMatcher.NONE)

    override fun doFilterInternal(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (matcher.matches(httpRequest)) {
            httpRequest.setAttribute("org.springframework.session.web.http.SessionRepositoryFilter.FILTERED", true)
        }
        filterChain.doFilter(httpRequest, httpResponse)
    }
}