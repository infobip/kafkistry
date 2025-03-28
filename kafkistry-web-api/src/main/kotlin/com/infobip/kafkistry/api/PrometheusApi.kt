package com.infobip.kafkistry.api

import com.infobip.kafkistry.metric.config.APP_METRICS_ENDPOINT_ENABLED_PROPERTY
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.io.OutputStreamWriter

@Controller
@RequestMapping("\${app.http.root-path}")
@ConditionalOnProperty(APP_METRICS_ENDPOINT_ENABLED_PROPERTY, matchIfMissing = true)
class PrometheusApi(
    private val meterRegistry: PrometheusMeterRegistry,
    private val collectorRegistry: CollectorRegistry,
) {

    @GetMapping("\${app.metrics.http-path}")
    fun scrape(httpResponse: HttpServletResponse) {
        httpResponse.status = 200
        val writer = OutputStreamWriter(httpResponse.outputStream)
        TextFormat.write004(
            writer,
            collectorRegistry.filteredMetricFamilySamples { true },
        )
        writer.flush()
        httpResponse.outputStream.use { meterRegistry.scrape(it) }
        writer.flush()
    }
}