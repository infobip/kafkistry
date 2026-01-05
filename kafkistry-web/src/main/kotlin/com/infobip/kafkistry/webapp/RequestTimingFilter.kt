package com.infobip.kafkistry.webapp

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean

const val REQUEST_START_TIME_ATTR = "REQUEST_START_TIME_MS"

@Component
@Order(0)  // Execute as early as possible in the filter chain
class RequestTimingFilter : GenericFilterBean() {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequest) {
            request.setAttribute(REQUEST_START_TIME_ATTR, System.currentTimeMillis())
        }
        chain.doFilter(request, response)
    }
}