package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.security.NoSessionRequestMatcher
import io.modelcontextprotocol.server.McpStatelessServerHandler
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport
import io.modelcontextprotocol.spec.McpStatelessServerTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import reactor.core.publisher.Mono

@Configuration
class KafkistryMcpSecurityConfig(private val httpProperties: WebHttpProperties) {

    companion object {
        const val MCP_ENDPOINT_PROPERTY = "\${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}"
    }

    @Bean
    fun mcpNoSessionMatcher(): NoSessionRequestMatcher = NoSessionRequestMatcher.of(
        AntPathRequestMatcher("${httpProperties.rootPath}/mcp/**")
    )

    /**
     * Override the default WebMvcStatelessServerTransport to advertise support for
     * MCP protocol version 2025-11-25, which is required by Claude Code client 2.x.
     * The default transport only lists ["2025-03-26", "2025-06-18"].
     */
    @Bean
    fun webMvcStatelessServerTransport(
        @Value(MCP_ENDPOINT_PROPERTY) mcpEndpoint: String,
    ): WebMvcStatelessServerTransport {
        return WebMvcStatelessServerTransport.builder()
            .messageEndpoint(mcpEndpoint)
            .build()
    }

    @Bean
    @Primary
    fun mcpStatelessServerTransport(
        webMvcTransport: WebMvcStatelessServerTransport,
    ): McpStatelessServerTransport = object : McpStatelessServerTransport {
        override fun setMcpHandler(handler: McpStatelessServerHandler) =
            webMvcTransport.setMcpHandler(handler)
        override fun closeGracefully(): Mono<Void> =
            webMvcTransport.closeGracefully()
        override fun protocolVersions(): List<String> =
            listOf("2025-03-26", "2025-06-18", "2025-11-25")
    }
}
