package com.infobip.kafkistry.metric

import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.acl.AclInspectionResultType
import com.infobip.kafkistry.utils.ClusterFilter
import com.infobip.kafkistry.utils.ClusterFilterProperties
import io.prometheus.client.Collector.MetricFamilySamples
import io.prometheus.client.Collector.Type
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.metrics.acl-statuses")
class AclStatusesMetricsProperties {
    var enabled = true
    var includeDisabledClusters = false
    var omitStatusNames = mutableSetOf<String>()
    @NestedConfigurationProperty
    var enabledOn = ClusterFilterProperties()
}

@Component
@ConditionalOnProperty("app.metrics.acl-statuses.enabled", matchIfMissing = true)
class AclStatusesMetricsCollector(
    promProperties: PrometheusMetricsProperties,
    private val properties: AclStatusesMetricsProperties,
    clusterLabelProvider: ObjectProvider<ClusterMetricLabelProvider>,
) : KafkistryMetricsCollector {

    //default: kafkistry_acl_status
    private val statusMetricName = promProperties.prefix + "acl_status"

    private val filter = ClusterFilter(properties.enabledOn)

    private val clusterLabelProvider = clusterLabelProvider.getIfAvailable {
        DefaultClusterMetricLabelProvider()
    }

    private val labelNames = listOf(
        this.clusterLabelProvider.labelName(), "acl", "status", "valid", "level", "owners",
    )

    override fun expose(context: MetricsDataContext): List<MetricFamilySamples> {
        val statusSamples = context.aclPrincipalInspections.asSequence()
            .flatMap { principalStatuses ->
                val owner = principalStatuses.principalAcls?.owner
                    ?.replace(" ", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: "unknown"
                principalStatuses.clusterInspections.asSequence()
                    .filter { filter.enabled(ClusterRef(it.clusterIdentifier, it.clusterTags)) }
                    .flatMap { clusterRulesStatuses ->
                        val clusterDisabled =
                            clusterRulesStatuses.status.statusCounts.any { it.type == AclInspectionResultType.CLUSTER_DISABLED }
                        if (properties.includeDisabledClusters || !clusterDisabled) {
                            val clusterLabel = clusterLabelProvider.labelValue(clusterRulesStatuses.clusterIdentifier)
                            clusterRulesStatuses.statuses
                                .flatMap { ruleStatuses ->
                                    val aclRule = ruleStatuses.rule.asString()
                                    ruleStatuses.statusTypes
                                        .filter { it.name !in properties.omitStatusNames }
                                        .map {
                                            MetricFamilySamples.Sample(
                                                statusMetricName, labelNames,
                                                listOf(
                                                    clusterLabel, aclRule, it.name, it.valid.toString(),
                                                    it.level.name, owner,
                                                ),
                                                1.0,
                                            )
                                        }
                                }
                        } else {
                            emptyList()
                        }
                    }
            }
            .toList()
        return mutableListOf(
            MetricFamilySamples(
                statusMetricName,
                Type.STATE_SET,
                "Individual state type per ACL rule on cluster",
                statusSamples,
            )
        )
    }

}