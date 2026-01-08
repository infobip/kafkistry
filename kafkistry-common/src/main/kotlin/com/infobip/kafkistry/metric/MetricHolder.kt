package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import java.util.concurrent.ConcurrentHashMap

typealias MetricNamePrefix = String

class MetricHolder<T : Any>(
    private val creator: (MetricNamePrefix) -> T
) {

    private val namePrefixMetric = ConcurrentHashMap<MetricNamePrefix, T>()

    fun metric(promProperties: PrometheusMetricsProperties): T {
        return metric(promProperties.prefix)
    }

    fun metric(prefix: MetricNamePrefix): T {
        return namePrefixMetric.computeIfAbsent(prefix, creator)
    }
}