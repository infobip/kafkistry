package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import org.springframework.stereotype.Component

@Component
class BackgroundJobsIssuesMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry,
) : KafkistryMetricsCollector {

    //default: kafkistry_background_job_issues
    private val statusMetricName = promProperties.prefix + "background_job_issues"

    private val labelNames = listOf(
        "class", "category", "phase", "cluster"
    )

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val issuesSamples = backgroundJobIssuesRegistry.currentIssues()
            .map {
                MetricFamilySamples.Sample(
                    statusMetricName, labelNames,
                    listOf(it.job.key.jobClass, it.job.key.category, it.job.key.phase, it.job.key.cluster ?: "unavailable"),
                    1.0,
                )
            }
        return mutableListOf(
            MetricFamilySamples(statusMetricName, Type.STATE_SET, "Failing background jobs", issuesSamples)
        )
    }


}