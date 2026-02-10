package com.infobip.kafkistry.kafkastate.brokerdisk

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.ClusterNode
import com.infobip.kafkistry.kafka.NodeId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.getForObject
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.function.Supplier

@Component
@ConfigurationProperties("app.kafka.metrics.prometheus")
class PrometheusBrokerDiskMetricsProperties {
    var enabled = false
    var timeOffset = 60
    var bulk = false
    lateinit var prometheusBaseUrl: String
    var totalPromQuery: String? = null
    var freePromQuery: String? = null
    lateinit var brokerLabelName: String
    var brokerLabelHostExtractPattern = "(.*)"
    var brokerHostLabelExtractPattern = "(.*)"
    var httpHeaders = mutableMapOf<String, String>()
    var httpTimeoutMs = 120_000L
}

@Component
@ConditionalOnProperty("app.kafka.metrics.prometheus.enabled")
class PrometheusNodeDiskMetricsProvider(
    private val properties: PrometheusBrokerDiskMetricsProperties,
) : NodeDiskMetricsProvider {

    private val promUrl = "${properties.prometheusBaseUrl}/api/v1/query?query={query}&time={time}"

    private val restTemplate = RestTemplateBuilder()
        .additionalInterceptors({ request, body, execution ->
            request.headers.apply {
                properties.httpHeaders.forEach { (name, value) ->
                    add(name, value)
                }
            }
            execution.execute(request, body)
        })
        .requestFactory(Supplier {
            JdkClientHttpRequestFactory(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build())
        })
        .errorHandler(object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = response.statusCode != HttpStatus.OK

            override fun handleError(url: URI, method: HttpMethod, response: ClientHttpResponse) {
                val responseBody = response.body.readAllBytes()
                with(response) {
                    throw RestClientResponseException(
                        "Prometheus API call failed: Http:${statusCode.value()} $statusText; " +
                            "Headers:$headers; Body:${responseBody.decodeToString()}",
                        statusCode.value(), statusText, headers, responseBody, null
                    )
                }
            }
        })
        .readTimeout(Duration.ofMillis(properties.httpTimeoutMs))
        .connectTimeout(Duration.ofMillis(properties.httpTimeoutMs))
        .build()

    private lateinit var brokerLabelToHostPattern: Regex
    private lateinit var brokerHostToLabelPattern: Regex

    init {
        if (properties.bulk) {
            properties.brokerLabelName.length
            brokerLabelToHostPattern = Regex(properties.brokerLabelHostExtractPattern)
            brokerHostToLabelPattern = Regex(properties.brokerHostLabelExtractPattern)
        }
    }

    override fun nodesDisk(
        clusterIdentifier: KafkaClusterIdentifier,
        nodes: List<ClusterNode>,
    ): Map<NodeId, NodeDiskMetric> {
        return if (properties.bulk) {
            val totalDisk = properties.totalPromQuery?.let { getBulkBrokersValues(it, nodes) }
            val freeDisk = properties.freePromQuery?.let { getBulkBrokersValues(it, nodes) }
            nodes.associate {
                it.nodeId to NodeDiskMetric(total = totalDisk?.get(it.nodeId), free = freeDisk?.get(it.nodeId))
            }
        } else {
            nodes.associate { broker ->
                val totalDisk = properties.totalPromQuery?.let { getBrokerValue(it, broker) }
                val freeDisk = properties.freePromQuery?.let { getBrokerValue(it, broker) }
                broker.nodeId to NodeDiskMetric(total = totalDisk, free = freeDisk)
            }
        }
    }

    private fun getBrokerValue(queryTemplate: String, broker: ClusterNode): Long? {
        val promQuery = queryTemplate
            .replace("{nodeHost}", broker.host)
            .replace("{nodeId}", broker.nodeId.toString())
            .replace("{brokerHost}", broker.host)
            .replace("{brokerId}", broker.nodeId.toString())
        val promResult = restTemplate.getForObject<PrometheusResponse>(
            promUrl, mapOf("query" to promQuery, "time" to time())
        )
        return promResult.data.result.firstNotNullOfOrNull {
            it.value[1].toString().toLongOrNull()
        }
    }

    private fun String.applyBrokerLabelToHostPattern(): String = applyPattern(brokerLabelToHostPattern)
    private fun String.applyBrokerHostToLabelPattern(): String = applyPattern(brokerHostToLabelPattern)

    private fun String.applyPattern(regex: Regex): String = regex.find(this)
        ?.let { if (it.groups.size >= 2) it.groupValues[1] else it.groupValues[0] } ?: this

    private fun getBulkBrokersValues(queryTemplate: String, brokers: List<ClusterNode>): Map<BrokerId, Long> {
        val promQuery = queryTemplate
            .replace("{nodeHosts}", brokers.joinToString(separator = "|") { it.host.applyBrokerHostToLabelPattern() })
            .replace("{nodeIds}", brokers.joinToString(separator = "|") { it.nodeId.toString() })
            .replace("{brokerHosts}", brokers.joinToString(separator = "|") { it.host.applyBrokerHostToLabelPattern() })
            .replace("{brokerIds}", brokers.joinToString(separator = "|") { it.nodeId.toString() })
        val promResult = restTemplate.getForObject<PrometheusResponse>(
            promUrl, mapOf("query" to promQuery, "time" to time())
        )
        return promResult.data.result.mapNotNull { promMetric ->
            val brokerLabel = promMetric.metric[properties.brokerLabelName] ?: return@mapNotNull null
            val brokerFromLabel = brokerLabel.applyBrokerLabelToHostPattern()
            val broker = brokers
                .find {
                    brokerFromLabel.equals(
                        it.host,
                        ignoreCase = true
                    ) || brokerFromLabel == it.nodeId.toString()
                }
                ?: brokers.find { it.host.applyBrokerHostToLabelPattern().equals(brokerFromLabel, ignoreCase = true) }
                ?: return@mapNotNull null
            val value = promMetric.value[1].toString().toLongOrNull() ?: return@mapNotNull null
            broker.nodeId to value
        }.toMap()
    }

    private fun time() = System.currentTimeMillis().div(1000).minus(properties.timeOffset).toString()

    private data class PrometheusResponse(
        val data: PrometheusData,
    )

    private data class PrometheusData(
        val result: List<PrometheusMetric>,
    )

    private data class PrometheusMetric(
        val metric: Map<String, String>,
        val value: List<Any>,
    )

}
