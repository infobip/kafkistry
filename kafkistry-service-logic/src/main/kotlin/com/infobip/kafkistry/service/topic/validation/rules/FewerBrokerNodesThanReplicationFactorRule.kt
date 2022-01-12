package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.propertiesForCluster
import com.infobip.kafkistry.service.withClusterProperties
import com.infobip.kafkistry.model.ClusterRef
import org.springframework.stereotype.Component

@Component
class FewerBrokerNodesThanReplicationFactorRule : ValidationRule {

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!topicDescriptionView.presentOnCluster) return valid()
        val numBrokers = clusterMetadata.info?.nodeIds?.size ?: return valid()
        val replicationFactor = topicDescriptionView.properties.replicationFactor
        return if (replicationFactor > numBrokers) {
            violated(
                message = "There are too few cluster nodes ($numBrokers) for replication factor of $replicationFactor",
                severity = RuleViolation.Severity.ERROR,
            )
        } else {
            valid()
        }
    }

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val clusterInfo = clusterMetadata.info ?: throw KafkistryIllegalStateException(
            "Can't fix config for topic '${topicDescription.name}' for cluster '${clusterMetadata.ref.identifier}' with no ClusterInfo (unreachable?)"
        )
        return topicDescription.withReplicationFactor(clusterInfo.nodeIds.size, clusterMetadata.ref)
    }

    private fun TopicDescription.withReplicationFactor(replicationFactor: Int, clusterRef: ClusterRef): TopicDescription {
        val properties = propertiesForCluster(clusterRef).copy(replicationFactor = replicationFactor)
        return withClusterProperties(clusterRef.identifier, properties)
    }

}