package com.infobip.kafkistry.api

import com.infobip.kafkistry.metric.config.APP_METRICS_ENABLED_PROPERTY
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import jakarta.servlet.http.HttpServletResponse

@Controller
@RequestMapping("\${app.http.root-path}")
@ConditionalOnProperty(APP_METRICS_ENABLED_PROPERTY, matchIfMissing = true)
class PrometheusApi(
    private val registry: PrometheusMeterRegistry
) {

    @GetMapping("\${app.metrics.http-path}")
    fun scrape(httpResponse: HttpServletResponse) {
        httpResponse.status = 200
        httpResponse.outputStream
            .writer(Charsets.UTF_8)
            .use { registry.scrape(it) }
    }
}