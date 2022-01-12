package com.infobip.kafkistry.service.consume.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ConsumeProperties::class)
class ConsumeConfig

@ConfigurationProperties("app.consume")
class ConsumeProperties {

    var enabled = true
    var maxRecords: Int? = null
    var maxWaitMs: Long? = null
    var poolBatchSize: Int? = null
    var poolInterval: Long? = null

    fun maxRecords() = maxRecords ?: notInitialized("maxRecords")
    fun maxWaitMs() = maxWaitMs ?: notInitialized("maxWaitMs")
    fun poolBatchSize() = poolBatchSize ?: notInitialized("poolBatchSize")
    fun poolInterval() = poolInterval ?: notInitialized("poolInterval")

    private fun notInitialized(name: String): Nothing = throw IllegalStateException("Not initialized: $name")
}