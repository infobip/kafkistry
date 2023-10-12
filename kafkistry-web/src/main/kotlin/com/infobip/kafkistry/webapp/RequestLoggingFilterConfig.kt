package com.infobip.kafkistry.webapp

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.CommonsRequestLoggingFilter

@Component
@ConfigurationProperties("app.http-logging")
class RequestLoggingProperties {
    var enabled = false
    var includeQueryString = true
    var includePayload = true
    var maxPayloadLength = 10000
    var includeHeaders = true
    var afterMessagePrefix = "REQUEST DATA : "
}

@Configuration
@ConditionalOnProperty("app.http-logging.enabled")
class RequestLoggingFilterConfig(
    val properties: RequestLoggingProperties
) {

    @Bean
    @Order(20)   //after MDC is set
    fun logFilter(): CommonsRequestLoggingFilter {
        return CommonsRequestLoggingFilter().also {
            it.setIncludeQueryString(properties.includeQueryString)
            it.setIncludePayload(properties.includePayload)
            it.setMaxPayloadLength(properties.maxPayloadLength)
            it.setIncludeHeaders(properties.includeHeaders)
            it.setAfterMessagePrefix(properties.afterMessagePrefix)
        }
    }
}