package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.security.NoSessionRequestMatcher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class KafkistryMcpSecurityConfig(private val httpProperties: WebHttpProperties) {

    @Bean
    fun mcpNoSessionMatcher(): NoSessionRequestMatcher = NoSessionRequestMatcher.of(
        AntPathRequestMatcher("${httpProperties.rootPath}/mcp/**")
    )
}
