package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.service.topic.withClusterProperties
import org.springframework.stereotype.Component

@Component
class ReplicationFactorOneRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (topicDescriptionView.properties.replicationFactor > 1) {
            return valid()
        }
        if (!topicDescriptionView.presentOnCluster) {
            return valid()
        }
        val clusterNodes = clusterMetadata.info?.nodeIds ?: return valid()
        if (clusterNodes.size < 2) {
            return valid()
        }
        return violated(
            message = "Replication factor should be more than %REPLICATION_FACTOR% to support high availability. " +
                    "Cluster has %NUM_BROKERS% brokers/nodes available",
            placeholders = mapOf(
                "REPLICATION_FACTOR" to Placeholder("replication.factor", 1),
                "NUM_BROKERS" to Placeholder("brokers.count", clusterNodes.size),
            ),
            severity = RuleViolation.Severity.WARNING,
        )
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        return topicDescription.withClusterProperties(
            clusterMetadata.ref.identifier,
            topicDescription.propertiesForCluster(clusterMetadata.ref).copy(replicationFactor = 2)
        )
    }
}