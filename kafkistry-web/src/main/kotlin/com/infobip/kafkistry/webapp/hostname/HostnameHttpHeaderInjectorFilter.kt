package com.infobip.kafkistry.webapp.hostname

import com.infobip.kafkistry.hostname.HostnameResolver
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse

@Suppress("UastIncorrectHttpHeaderInspection")
const val SERVER_HOSTNAME_HEADER = "Server-Hostname"

@Component
class HostnameHttpHeaderInjectorFilter(
    private val hostnameResolver: HostnameResolver
) : GenericFilterBean() {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        (response as? HttpServletResponse)?.addHeader(SERVER_HOSTNAME_HEADER, hostnameResolver.hostname)
        chain.doFilter(request, response)
    }
}
