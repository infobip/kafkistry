package com.infobip.kafkistry.service.kafkastreams

import com.infobip.kafkistry.kafka.ConsumerGroupStatus
import com.infobip.kafkistry.kafka.KafkaExistingTopic
import com.infobip.kafkistry.kafkastate.ClusterConsumerGroups
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.utils.ClusterTopicConsumerGroupFilter
import com.infobip.kafkistry.utils.ClusterTopicConsumerGroupFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.kstream.detection")
class KStreamConfigProperties {

    @NestedConfigurationProperty
    var enabledFor = ClusterTopicConsumerGroupFilterProperties()
}

@Component
class KStreamsAppsDetector(
    properties: KStreamConfigProperties
) {

    companion object {
        private const val K_STREAM_ASSIGNOR = "stream"
        private const val K_STREAM_NAME_MARKER = "-KSTREAM-"
    }

    private val filter = ClusterTopicConsumerGroupFilter(properties.enabledFor)

    fun findKStreamApps(
        clusterIdentifier: KafkaClusterIdentifier,
        clusterConsumerGroups: ClusterConsumerGroups,
        topics: List<KafkaExistingTopic>,
    ): List<KafkaStreamsApp> {
        if (!filter.testCluster(clusterIdentifier)) {
            return emptyList()
        }
        return clusterConsumerGroups.consumerGroups
            .filter { filter.testConsumerGroup(it.key) }
            .mapNotNull { (_, maybeGroup) ->
                maybeGroup.getOrNull()
                    ?.takeIf { it.partitionAssignor == K_STREAM_ASSIGNOR }
                    //kstream apps always have assignor "stream" which can't be changed
            }
            .map { streamGroup ->
                val topicsSeq = if (streamGroup.status == ConsumerGroupStatus.STABLE) {
                    streamGroup.assignments.asSequence().map { it.topic }
                } else {
                    streamGroup.offsets.asSequence().map { it.topic }
                }
                val inputTopics = topicsSeq
                    .filter { filter.testTopic(it) }
                    .distinct()
                    .sorted()
                    .toList()
                val startNaming = streamGroup.id + K_STREAM_NAME_MARKER
                val kstreamTopics = topics.asSequence()
                    .filter { it.name.startsWith(startNaming) }
                    .map { it.name }
                    .sorted()
                    .toList()
                KafkaStreamsApp(
                    kafkaStreamAppId = streamGroup.id, inputTopics = inputTopics, kStreamInternalTopics = kstreamTopics
                )
            }
    }
}