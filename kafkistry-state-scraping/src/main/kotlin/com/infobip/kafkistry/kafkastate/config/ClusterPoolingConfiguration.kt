package com.infobip.kafkistry.kafkastate.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties
class ClusterPoolingConfiguration {

    @Bean
    @ConfigurationProperties("app.pooling")
    fun poolingProperties() = PoolingProperties()

}

class PoolingProperties {

    /**
     * Interval for fixed rate on how often to run one type of scraping job
     */
    var intervalMs: Long = 10_000L

    var recordSamplingIntervalMs: Long = 120_000

    var ignoredConsumerPattern: String = ""

    //field-like methods to allow invocation/usage in @Scheduled SpEL expressions
    fun intervalMs() = intervalMs
    fun recordSamplingIntervalMs() = recordSamplingIntervalMs

}