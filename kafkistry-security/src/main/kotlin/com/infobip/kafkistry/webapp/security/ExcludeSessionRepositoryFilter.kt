package com.infobip.kafkistry.webapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.session.web.http.SessionRepositoryFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

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
            markAlreadyFiltered(httpRequest)
        }
        filterChain.doFilter(httpRequest, httpResponse)
    }

    companion object {
        /**
         * Marks [request] as already processed by [SessionRepositoryFilter] causing it not to get processed by it
         * and thus avoiding creating session for given [request]
         */
        fun markAlreadyFiltered(request: HttpServletRequest) {
            request.setAttribute(SessionRepositoryFilter::class.java.name + ALREADY_FILTERED_SUFFIX, true)
        }
    }
}