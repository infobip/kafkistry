package com.infobip.kafkistry.kafkastate.coordination

import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.metric.MetricHolder
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import io.prometheus.client.Summary

private val stateDataPublisherDurationHolder = MetricHolder { prefix ->
    Summary.build()
        .name(prefix + "state_data_publisher_duration_seconds")
        .help("Duration of state data publisher operations in seconds")
        .labelNames("operation", "status")
        .quantile(0.5, 0.05)
        .quantile(0.9, 0.01)
        .quantile(0.99, 0.001)
        .register()
}

/**
 * Delegating decorator for StateDataPublisher that tracks Prometheus metrics.
 * Measures duration and counts for all publish/subscribe operations.
 */
class MetricsDelegatingStateDataPublisher(
    private val delegate: StateDataPublisher,
    promProperties: PrometheusMetricsProperties,
) : StateDataPublisher by delegate {

    private val duration = stateDataPublisherDurationHolder.metric(promProperties)

    override fun <V> publishStateData(stateData: StateData<V>) = measureDuration("publishStateData") {
        delegate.publishStateData(stateData)
    }

    override fun <V> subscribeToStateUpdates(stateTypeName: String, listener: (StateData<V>) -> Unit) {
        delegate.subscribeToStateUpdates(stateTypeName) {
            measureDuration("listenerStateDataDelay", it.computedTime) {}
            measureDuration("listenerStateDataProcessing") { listener(it) }
        }
    }

    private inline fun <T> measureDuration(
        operation: String,
        startTime: Long = System.currentTimeMillis(),
        block: () -> T
    ): T {
        return try {
            block().also {
                val durationSeconds = (System.currentTimeMillis() - startTime) / 1_000.0
                duration.labels(operation, "success").observe(durationSeconds)
            }
        } catch (ex: Exception) {
            throw ex.also {
                val durationSeconds = (System.currentTimeMillis() - startTime) / 1_000.0
                duration.labels(operation, "failure").observe(durationSeconds)
            }
        }
    }
}
