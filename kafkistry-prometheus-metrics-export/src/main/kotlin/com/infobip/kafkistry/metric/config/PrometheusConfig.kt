package com.infobip.kafkistry.metric.config

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Configuration
@ConditionalOnProperty(APP_METRICS_ENABLED_PROPERTY, matchIfMissing = true)
class PrometheusConfigs(
    collectorBeans: Optional<List<Collector>>,
    prometheusProperties: PrometheusMetricsProperties,
) {

    companion object {
        private var initialized = AtomicBoolean(false)

        //static instance which is re-used when separate ApplicationContext-s spin up within same JVM
        // to avoid duplicate meters registrations into same prometheus CollectorRegistry.defaultRegistry
        private val prometheusRegistry = PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM
        )
    }

    init {
        if (initialized.compareAndSet(false, true)) {
            if (prometheusProperties.defaultMetrics) {
                DefaultExports.initialize()
            }
            collectorBeans.ifPresent { collectors ->
                collectors.forEach { it.register<Collector>() }
            }
        }
    }

    @Bean
    fun prometheusRegistry(): PrometheusMeterRegistry = prometheusRegistry

}