package com.infobip.kafkistry.metric

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface ClusterMetricLabelProvider {
    fun labelName(): String
    fun labelValue(clusterIdentifier: KafkaClusterIdentifier): String
}

class DefaultClusterMetricLabelProvider: ClusterMetricLabelProvider {
    override fun labelName(): String = "cluster"
    override fun labelValue(clusterIdentifier: KafkaClusterIdentifier): String = clusterIdentifier
}


@Component
@ConfigurationProperties("app.metrics.cluster-labels.transforming")
class TransformingClusterMetricLabelProviderConfig {

    var enabled = false
    var labelName = "cluster"
    var regex = ".*"
}

@Component
@ConditionalOnProperty("app.metrics.cluster-labels.transforming.enabled", matchIfMissing = false)
class TransformingClusterMetricLabelProvider(
    private val props: TransformingClusterMetricLabelProviderConfig
) : ClusterMetricLabelProvider {

    private val regex = Regex(props.regex)

    private val identifierToLabel = ConcurrentHashMap<KafkaClusterIdentifier, String>()

    private fun KafkaClusterIdentifier.transform(): String {
        return identifierToLabel.computeIfAbsent(this) {
            clusterIdentifierToLabel(it)
        }
    }

    fun clusterIdentifierToLabel(clusterIdentifier: KafkaClusterIdentifier): String {
        val match = regex.find(clusterIdentifier) ?: return clusterIdentifier
        for (group in match.groupValues.reversed()) {
            if (group.isNotEmpty()) {
                return group
            }
        }
        return clusterIdentifier
    }

    override fun labelName(): String = props.labelName

    override fun labelValue(clusterIdentifier: KafkaClusterIdentifier): String = clusterIdentifier.transform()

}
