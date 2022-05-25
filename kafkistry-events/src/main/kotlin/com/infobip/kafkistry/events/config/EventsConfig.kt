package com.infobip.kafkistry.events.config

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.events.*
import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConfigurationProperties(prefix = "app.hazelcast.publisher")
class HazelcastEventPublisherProperties {
    var ackWaitTime = 5000L
}

@Configuration
class EventsConfig {

    @Bean
    fun eventPublisher(
        listeners: Optional<List<EventListener<out KafkistryEvent>>>,
        hazelcast: ObjectProvider<HazelcastInstance>,
        hazelcastEventPublisherProperties: HazelcastEventPublisherProperties,
        promProperties: PrometheusMetricsProperties,
    ): EventPublisher {
        val publisher = when (val hazelcastInstance = hazelcast.ifAvailable) {
            null -> DefaultEventPublisher(listeners.orElse(emptyList()))
            else -> HazelcastEventPublisher(
                listeners.orElse(emptyList()),
                hazelcastInstance,
                hazelcastEventPublisherProperties.ackWaitTime,
                promProperties,
            )
        }
        return LoggingEventPublisher(publisher)
    }

}