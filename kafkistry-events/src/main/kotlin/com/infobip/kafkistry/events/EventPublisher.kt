package com.infobip.kafkistry.events

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.topic.ITopic
import com.infobip.kafkistry.metric.MetricHolder
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import io.prometheus.client.Counter
import io.prometheus.client.Summary
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Interface which allows different parts of application to publish that something is happened without
 * worrying what other parts of application need to react to it.
 *
 * Another aspect is that implementation of this publisher can provide mechanism to deliver published
 * events to other running instances of application (HazelcastEventPublisher)
 *
 * @see EventListener
 */
interface EventPublisher {

    fun publish(event: KafkistryEvent)
}

class LoggingEventPublisher(private val delegate: EventPublisher) : EventPublisher {

    private fun callerLogger(): Logger {
        return LoggerFactory.getLogger(Exception().stackTrace[2].className)
    }

    override fun publish(event: KafkistryEvent) {
        callerLogger().info("Publishing event $event using ${delegate.javaClass.simpleName}")
        delegate.publish(event)
    }

}

class DefaultEventPublisher(
        private val listeners: List<EventListener<out KafkistryEvent>>
) : EventPublisher {

    private val log = LoggerFactory.getLogger(DefaultEventPublisher::class.java)

    override fun publish(event: KafkistryEvent) {
        listeners.forEach {
            try {
                EventListenerAdapter.maybeInvoke(it, event)
            } catch (ex: Exception) {
                //failure in one listener should not affect execution of other listeners
                log.error("Listener ${it.javaClass.simpleName} threw an exception for event $event", ex)
            }
        }
    }

}

class HazelcastEventPublisher(
    private val listeners: List<EventListener<out KafkistryEvent>>,
    private val hazelcastInstance: HazelcastInstance,
    private val ackTimeoutMs: Long,
    promProperties: PrometheusMetricsProperties,
) : EventPublisher {

    companion object {
        const val KR_EVENTS = "kafkistry-events"
        const val KR_ACK = "kafkistry-ack"

        private val publishLatencyMetricHolder = MetricHolder { prefix ->
            //default name: kafkistry_hazelcast_event_publisher_latencies
            Summary.build()
                .name(prefix + "hazelcast_event_publisher_latencies")
                .help("Summary of latencies of hazelcast event publisher")
                .labelNames("eventClass")
                .ageBuckets(5)
                .maxAgeSeconds(TimeUnit.MINUTES.toSeconds(5))
                .quantile(0.5, 0.05)   // Add 50th percentile (= median) with 5% tolerated error
                .quantile(0.9, 0.01)   // Add 90th percentile with 1% tolerated error
                .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
                .register()
        }

        private val listenerLatencyMetricHolder = MetricHolder { prefix ->
            //default name: kafkistry_hazelcast_event_listener_latencies
            Summary.build()
                .name(prefix + "hazelcast_event_listener_latencies")
                .help("Summary of latencies of hazelcast event listener")
                .labelNames("eventClass", "listenerClass")
                .ageBuckets(5)
                .maxAgeSeconds(TimeUnit.MINUTES.toSeconds(5))
                .quantile(0.5, 0.05)   // Add 50th percentile (= median) with 5% tolerated error
                .quantile(0.9, 0.01)   // Add 90th percentile with 1% tolerated error
                .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
                .register()
        }

        private val publishCounterMetricHolder = MetricHolder { prefix ->
            //default name: kafkistry_hazelcast_event_publisher_count_total
            Counter.build()
                .name(prefix + "hazelcast_event_publisher_count_total")
                .help("Count of hazelcast event publisher published events per event type and stage [SEND, ACKNOWLEDGED, UNACKNOWLEDGED]")
                .labelNames("eventClass", "stage")
                .register()
        }

        private val listenerCounterMetricHolder = MetricHolder { prefix ->
            //default name: kafkistry_hazelcast_event_listener_count_total
            Counter.build()
                .name(prefix + "hazelcast_event_listener_count_total")
                .help("Count of hazelcast event listener events per event type")
                .labelNames("eventClass", "listenerClass")
                .register()
        }
    }

    private val publishLatencies = publishLatencyMetricHolder.metric(promProperties)
    private val publishCounters = publishCounterMetricHolder.metric(promProperties)

    private val listenerLatencies = listenerLatencyMetricHolder.metric(promProperties)
    private val listenerCounters = listenerCounterMetricHolder.metric(promProperties)

    init {
        listeners.forEach { listener ->
            val listenerClassName = listener.javaClass.simpleName
            eventTopic().addMessageListener { eventMessage ->
                val event = eventMessage.messageObject
                if (EventListenerAdapter.shouldInvoke(listener, event)) {
                    val eventClassName = event.javaClass.simpleName
                    listenerCounters.labels(eventClassName, listenerClassName).inc()
                    listenerLatencies.labels(eventClassName, listenerClassName).time {
                        EventListenerAdapter.invoke(listener, event)
                    }
                }
                ackTopic().publish(event.ackId())
            }
        }
    }

    override fun publish(event: KafkistryEvent) {
        val eventClassName = event.javaClass.simpleName
        publishCounters.labels(eventClassName, "SEND").inc()
        val timer = publishLatencies.labels(eventClassName).startTimer()
        val latch = CountDownLatch(expectedNumAck())
        val registration = ackTopic().addMessageListener { ackEvent ->
            if (ackEvent.messageObject == event.ackId()) {
                latch.countDown()
            }
        }
        try {
            eventTopic().publish(event)
            val allDone = latch.await(ackTimeoutMs, TimeUnit.MILLISECONDS)
            if (allDone) {
                publishCounters.labels(eventClassName, "ACKNOWLEDGED").inc()
            } else {
                publishCounters.labels(eventClassName, "UNACKNOWLEDGED").inc()
            }
        } finally {
            ackTopic().removeMessageListener(registration)
            timer.observeDuration()
        }
    }

    private fun eventTopic(): ITopic<KafkistryEvent> = hazelcastInstance.getTopic(KR_EVENTS)
    private fun ackTopic(): ITopic<String> = hazelcastInstance.getTopic(KR_ACK)

    private fun KafkistryEvent.ackId(): String = toString().hashCode().toString()
    private fun expectedNumAck(): Int = hazelcastInstance.cluster.members.size * listeners.size

}