package com.infobip.kafkistry.metric

import io.prometheus.client.Collector
import com.infobip.kafkistry.kafkastate.*
import org.springframework.stereotype.Component

@Component
class LastRefreshLagCollector(
        private val providers: List<AbstractKafkaStateProvider<*>>
) : Collector() {

    companion object {
        const val METRIC_NAME = "kafkistry_pool_refresh_lag"
    }

    val labelNames = listOf("cluster", "type")

    override fun collect(): MutableList<MetricFamilySamples> {
        val now = System.currentTimeMillis()
        val allSamples = providers.flatMap { provider ->
            val latestStates = provider.getAllLatestStates()
            latestStates.map { (clusterIdentifier, state) ->
                MetricFamilySamples.Sample(
                        METRIC_NAME,
                        labelNames,
                        listOf(clusterIdentifier, provider.stateTypeName),
                        (now - state.lastRefreshTime).toDouble()
                )
            }
        }
        return mutableListOf(
                MetricFamilySamples(
                        METRIC_NAME,
                        Type.GAUGE,
                        "How much time (ms) has passed since last pool from cluster",
                        allSamples.toMutableList()
                )
        )
    }

}