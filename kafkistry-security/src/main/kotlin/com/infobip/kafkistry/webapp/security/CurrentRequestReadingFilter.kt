package com.infobip.kafkistry.webapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean
import java.lang.UnsupportedOperationException

@Component
class CurrentRequestReadingFilter(
        private val currentRequestHolder: ThreadLocalCurrentHttpRequestHolder
) : GenericFilterBean() {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request !is HttpServletRequest) {
            throw UnsupportedOperationException("Application only supports http requests")
        }
        currentRequestHolder.set(request)
        try {
            chain.doFilter(request, response)
        } finally {
            currentRequestHolder.reset()
        }
    }
}