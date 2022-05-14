package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.service.topic.withClusterProperty
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.RuleViolation
import org.apache.kafka.common.config.TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG
import org.springframework.stereotype.Component
import kotlin.math.max

@Component
class MinInSyncReplicasTooBigRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val config = topicDescriptionView.config
        val replicationFactor = topicDescriptionView.properties.replicationFactor
        val minInSyncReplicas =
                try {
                    config[MIN_IN_SYNC_REPLICAS_CONFIG]?.toInt()
                } catch (e: NumberFormatException) {
                    return violated(
                        message = "$MIN_IN_SYNC_REPLICAS_CONFIG property is not numeric value, (${config[MIN_IN_SYNC_REPLICAS_CONFIG]})",
                        severity = RuleViolation.Severity.ERROR,
                    )
                } ?: return valid()

        return when {
            replicationFactor == 1 && minInSyncReplicas == 1 -> valid()
            replicationFactor == 1 && minInSyncReplicas > 1 -> violated(
                message = "$MIN_IN_SYNC_REPLICAS_CONFIG property ($minInSyncReplicas) is larger than replication.factor = 1",
                severity = RuleViolation.Severity.WARNING,
            )
            minInSyncReplicas == replicationFactor -> violated(
                message = "$MIN_IN_SYNC_REPLICAS_CONFIG property ($minInSyncReplicas) not less than  replication.factor = $replicationFactor",
                severity = RuleViolation.Severity.WARNING,
            )
            minInSyncReplicas > replicationFactor -> violated(
                message = "$MIN_IN_SYNC_REPLICAS_CONFIG property ($minInSyncReplicas) must not be bigger than  replication.factor = $replicationFactor",
                severity = RuleViolation.Severity.ERROR,
            )
            else -> valid()
        }
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val replicationFactor = topicDescription.propertiesForCluster(clusterMetadata.ref).replicationFactor
        val minInSyncReplicas = max(replicationFactor - 1, 1)
        return topicDescription.withMinInSyncReplicas(minInSyncReplicas, clusterMetadata.ref.identifier)
    }

    private fun TopicDescription.withMinInSyncReplicas(minInSyncReplicas: Int, clusterIdentifier: KafkaClusterIdentifier): TopicDescription {
        return withClusterProperty(clusterIdentifier, MIN_IN_SYNC_REPLICAS_CONFIG, "$minInSyncReplicas")
    }

}
