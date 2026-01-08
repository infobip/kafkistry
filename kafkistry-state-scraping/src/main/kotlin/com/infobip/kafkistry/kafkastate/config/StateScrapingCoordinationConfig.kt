package com.infobip.kafkistry.kafkastate.config

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.kafkastate.coordination.HazelcastStateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.HazelcastStateScrapingCoordinator
import com.infobip.kafkistry.kafkastate.coordination.LocalStateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.LocalStateScrapingCoordinator
import com.infobip.kafkistry.kafkastate.coordination.MetricsDelegatingStateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.StateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.StateScrapingCoordinator
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for distributed state scraping coordination.
 *
 * Conditionally creates Hazelcast-based or local-only implementations
 * based on whether HazelcastInstance is available.
 *
 * When Hazelcast is available:
 * - Multiple instances coordinate via distributed locks
 * - Winners share scraped data with losers
 *
 * When Hazelcast is not available:
 * - Each instance operates independently (current behavior)
 * - No coordination or data sharing
 */
@Configuration
class StateScrapingCoordinationConfig {

    /**
     * Coordinator for racing to determine which instance scrapes each cluster.
     */
    @Bean
    fun stateScrapingCoordinator(
        hazelcast: ObjectProvider<HazelcastInstance>
    ): StateScrapingCoordinator {
        return when (val hazelcastInstance = hazelcast.ifAvailable) {
            null -> LocalStateScrapingCoordinator()
            else -> HazelcastStateScrapingCoordinator(hazelcastInstance)
        }
    }

    /**
     * Publisher for sharing scraped state data across instances.
     * Wrapped with metrics decorator to track Prometheus metrics.
     */
    @Bean
    fun stateDataPublisher(
        hazelcast: ObjectProvider<HazelcastInstance>,
        promProperties: PrometheusMetricsProperties,
        hostnameResolver: HostnameResolver,
    ): StateDataPublisher {
        val delegate = when (val hazelcastInstance = hazelcast.ifAvailable) {
            null -> LocalStateDataPublisher()
            else -> HazelcastStateDataPublisher(hazelcastInstance, hostnameResolver)
        }
        return MetricsDelegatingStateDataPublisher(delegate, promProperties)
    }
}
