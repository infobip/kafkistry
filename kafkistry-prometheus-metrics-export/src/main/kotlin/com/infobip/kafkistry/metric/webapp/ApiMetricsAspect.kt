package com.infobip.kafkistry.metric.webapp

import io.prometheus.client.Summary
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val apiServiceRequestLatencies = Summary.build()
        .name("kafkistry_service_api_latencies")
        .help("Summary of latencies of each api service broken down per separate api service method")
        .labelNames("service_method")
        .ageBuckets(5)
        .maxAgeSeconds(TimeUnit.MINUTES.toSeconds(5))
        .quantile(0.5, 0.05)   // Add 50th percentile (= median) with 5% tolerated error
        .quantile(0.9, 0.01)   // Add 90th percentile with 1% tolerated error
        .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
        .register()

@Component
@Aspect
@ConditionalOnProperty("app.metrics.api-calls", matchIfMissing = true)
class ApiMetricsAspect {

    private val log = LoggerFactory.getLogger(ApiMetricsAspect::class.java)

    @Around("execution(* com.infobip.kafkistry.api.*.*(..))")
    fun exec(joinPoint: ProceedingJoinPoint): Any? {
        val start = System.currentTimeMillis()
        try {
            return joinPoint.proceed()
        } finally {
            val end = System.currentTimeMillis()
            trackDuration(joinPoint, end - start)
        }
    }

    private fun trackDuration(joinPoint: ProceedingJoinPoint, durationMs: Long) {
        val method = joinPoint.target.javaClass.simpleName + "." + joinPoint.signature.name + "(*)"
        log.info("Execution of API '{}' took {} ms", method, durationMs)
        apiServiceRequestLatencies.labels(method).observe(durationMs.toDouble())
    }
}