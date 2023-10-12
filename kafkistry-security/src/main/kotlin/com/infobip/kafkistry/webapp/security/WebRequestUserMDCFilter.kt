package com.infobip.kafkistry.webapp.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean
import java.util.concurrent.atomic.AtomicLong

private const val USER = "user"
private const val REQUEST_ID = "reqId"

@Component
@Order(10) //after security but before logging
class WebRequestUserMDCFilter(
    private val userResolver: CurrentRequestUserResolver,
) : GenericFilterBean() {

    private val requestCounter = AtomicLong(0)

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val username = userResolver.resolveUserOrUnknown().username
        val requestId = requestCounter.incrementAndGet()
        MDC.put(USER, username)
        MDC.put(REQUEST_ID, requestId.toString())
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(USER)
            MDC.remove(REQUEST_ID)
        }
    }

}