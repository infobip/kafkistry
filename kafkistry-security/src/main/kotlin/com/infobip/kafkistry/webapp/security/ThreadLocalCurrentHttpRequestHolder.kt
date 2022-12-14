package com.infobip.kafkistry.webapp.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class ThreadLocalCurrentHttpRequestHolder {

    private val currentRequest = ThreadLocal<HttpServletRequest>()

    fun get(): HttpServletRequest? = currentRequest.get()
    fun set(request: HttpServletRequest): Unit = currentRequest.set(request)
    fun reset(): Unit = currentRequest.set(null)
}