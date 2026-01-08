package com.infobip.kafkistry.metric

import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import org.springframework.stereotype.Component

@Component
class LastRefreshLagCollector(
    promProperties: PrometheusMetricsProperties,
    private val providers: List<AbstractKafkaStateProvider<*>>
) : KafkistryMetricsCollector {

    //default: kafkistry_pool_refresh_lag
    private val metricName = promProperties.prefix + "pool_refresh_lag"

    val labelNames = listOf("cluster", "type")

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val now = System.currentTimeMillis()
        val allSamples = providers.flatMap { provider ->
            val latestStates = provider.getAllLatestStates()
            latestStates
                .filterValues { it.stateType != StateType.DISABLED }
                .map { (clusterIdentifier, state) ->
                    MetricFamilySamples.Sample(
                        metricName, labelNames,
                        listOf(clusterIdentifier, provider.stateTypeName()),
                        (now - state.lastRefreshTime).toDouble()
                    )
                }
        }
        return mutableListOf(
                MetricFamilySamples(
                    metricName, Type.GAUGE,
                    "How much time (ms) has passed since last pool from cluster",
                    allSamples.toMutableList()
                )
        )
    }

}