package com.infobip.kafkistry.service.produce.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ProduceProperties::class)
class ProduceConfig

@ConfigurationProperties("app.produce")
class ProduceProperties {

    var enabled = true
    var requestTimeoutMs: Long? = null
    var deliveryTimeoutMs: Long? = null
    var defaultKeySerializer: String? = null
    var defaultValueSerializer: String? = null
    var defaultHeaderSerializer: String? = null
    var injectUsernameHeader: Boolean = true
    var usernameHeaderName: String = "KAFKISTRY_PRODUCED"

    fun requestTimeoutMs() = requestTimeoutMs ?: notInitialized("requestTimeoutMs")
    fun deliveryTimeoutMs() = deliveryTimeoutMs ?: notInitialized("deliveryTimeoutMs")
    fun defaultKeySerializer() = defaultKeySerializer ?: "STRING"
    fun defaultValueSerializer() = defaultValueSerializer ?: "STRING"
    fun defaultHeaderSerializer() = defaultHeaderSerializer ?: "STRING"

    private fun notInitialized(name: String): Nothing =
        throw IllegalStateException("Not initialized: $name")
}
