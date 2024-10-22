package com.infobip.kafkistry.metric.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

const val APP_METRICS_ENABLED_PROPERTY = "app.metrics.enabled"
const val APP_METRICS_ENDPOINT_ENABLED_PROPERTY = "app.metrics.endpoint-enabled"

@Configuration
@ConfigurationProperties("app.metrics")
class PrometheusMetricsProperties {
    var prefix = "kafkistry_"
    var enabled = true              //defined to generate properties metadata
    var endpointEnabled = true      //defined to generate properties metadata
    var defaultMetrics = true
    var apiCalls = true
    var httpCalls = true
    var httpPath: String? = null    //defined to generate properties metadata
    var preCached = false
    var preCacheRefreshSec = 10L

    fun preCacheRefreshMs() = 1000L * preCacheRefreshSec
}