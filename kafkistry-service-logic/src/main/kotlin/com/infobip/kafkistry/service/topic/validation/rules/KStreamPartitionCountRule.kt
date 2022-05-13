package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.service.kafkastreams.KStreamsAppsProvider
import com.infobip.kafkistry.service.kafkastreams.TopicKStreamsInvolvement
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryIntegrityException
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.service.topic.withClusterProperties
import com.infobip.kafkistry.utils.ClusterTopicFilter
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties("app.topic-validation.kstream-partition-count")
class KStreamPartitionCountRuleProperties {

    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()
}


@Component
class KStreamPartitionCountRule(
    private val kStreamsAppsProvider: KStreamsAppsProvider,
    private val clusterStateProvider: KafkaClustersStateProvider,
    properties: KStreamPartitionCountRuleProperties,
) : ValidationRule {

    private val filter = ClusterTopicFilter(properties.enabledOn)

    override fun check(topicDescriptionView: TopicDescriptionView, clusterMetadata: ClusterMetadata): RuleViolation? {
        if (!filter(clusterMetadata.ref.identifier, topicDescriptionView.name)) {
            return valid()
        }
        val missMatchingAppTopics = missMatchingTopics(
            topicDescriptionView.name,
            topicDescriptionView.properties.partitionCount,
            clusterMetadata.ref.identifier,
        )
        if (missMatchingAppTopics.isEmpty()) {
            return valid()
        }
        return violated(
            message = "Topic's partition count of %partitionCount% violates KStream co-partitioning with following " +
                    "(${missMatchingAppTopics.size}) other topics involved within the same KStream application: \n" +
                    missMatchingAppTopics.mapIndexed { index, _ ->
                        "%topic$index%[%partitions$index%] @ %kStreamApp$index%"
                    }.joinToString(", "),
            placeholders = missMatchingAppTopics
                .flatMapIndexed { index, kStreamTopicPartitions ->
                    listOf(
                        "kStreamApp$index" to Placeholder("kStream-app", kStreamTopicPartitions.kStreamAppId),
                        "topic$index" to Placeholder("topic-name", kStreamTopicPartitions.topic),
                        "partitions$index" to Placeholder("partition-count", kStreamTopicPartitions.partitions),
                    )
                }
                .toMap()
                .plus(
                    "partitionCount" to Placeholder("partition-count", topicDescriptionView.properties.partitionCount),
                ),
            severity = RuleViolation.Severity.WARNING,
        )
    }

    private fun missMatchingTopics(
        topicName: TopicName,
        partitionCount: Int,
        clusterIdentifier: KafkaClusterIdentifier,
    ): List<KStreamTopicPartitions> {
        val kStreamAppsInvolvement = kStreamsAppsProvider.topicKStreamAppsInvolvement(
            clusterIdentifier = clusterIdentifier,
            topicName = topicName,
        )
        if (kStreamAppsInvolvement == TopicKStreamsInvolvement.NONE) {
            return emptyList()
        }
        return (kStreamAppsInvolvement.inputOf + listOfNotNull(kStreamAppsInvolvement.internalIn))
            .map { app ->
                app.kafkaStreamAppId to (app.inputTopics + app.kStreamInternalTopics)
                    .filter { it != topicName }
                    .mapNotNull { topic ->
                        partitionCountOf(clusterIdentifier, topic)?.let { topic to it }
                    }
                    .filter { (_, partitions) -> partitions != partitionCount }
                    .toMap()
            }
            .flatMap { (appId, missMatches) ->
                missMatches.map { (topic, partitions) ->
                    KStreamTopicPartitions(appId, topic, partitions)
                }
            }
    }

    private data class KStreamTopicPartitions(
        val kStreamAppId: KStreamAppId,
        val topic: TopicName,
        val partitions: Int,
    )

    private fun partitionCountOf(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName
    ): Int? = clusterStateProvider.getLatestClusterState(clusterIdentifier)
        .valueOrNull()
        ?.topics?.find { it.name == topicName }
        ?.partitionsAssignments?.size

    override fun doFixConfig(topicDescription: TopicDescription, clusterMetadata: ClusterMetadata): TopicDescription {
        val topicProperties = topicDescription.propertiesForCluster(clusterMetadata.ref)
        val partitionCount = topicProperties.partitionCount
        val missMatchingTopics = missMatchingTopics(
            topicName = topicDescription.name,
            partitionCount = partitionCount,
            clusterIdentifier = clusterMetadata.ref.identifier,
        )
        if (missMatchingTopics.isEmpty()) {
            return topicDescription
        }
        val partitionCountTopics = missMatchingTopics.groupBy({ it.partitions }, { it.topic })
        if (partitionCountTopics.size > 2) {
            throw KafkistryIntegrityException(
                "Impossible to fix partition count due to conflicting partition counts of other topics: $partitionCountTopics"
            )
        }
        val resolvedPartitionCount = partitionCountTopics.keys.first()
        if (resolvedPartitionCount < partitionCount) {
            throw KafkistryIntegrityException(
                "Can't fix partition count because partition count cant be reduced from $partitionCount to $resolvedPartitionCount"
            )
        }
        return topicDescription.withClusterProperties(
            clusterMetadata.ref.identifier, topicProperties.copy(partitionCount = resolvedPartitionCount)
        )
    }
}