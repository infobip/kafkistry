package com.infobip.kafkistry.metric.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

const val APP_METRICS_ENABLED_PROPERTY = "app.metrics.enabled"

@Configuration
@ConfigurationProperties("app.metrics")
class PrometheusMetricsProperties {
    var prefix = "kafkistry_"
    var enabled = true
    var defaultMetrics = true
    var apiCalls = true
    var httpCalls = true
    var httpPath: String? = null    //defined to generate properties metadata
}