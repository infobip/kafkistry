package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.model.KafkaCluster
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.max

@Component
class KafkaTopicOffsetsProvider(
    components: StateProviderComponents,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<ClusterTopicOffsets>(components) {

    companion object {
        const val TOPIC_OFFSETS = "topic_offsets"
    }

    override fun stateTypeName() = TOPIC_OFFSETS

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterTopicOffsets {
        val topicNames = clientProvider.doWithClient(kafkaCluster) { it.listAllTopicNames().get() }
        val topicsOffsets = topicNames
            .chunked(max(20, topicNames.size / clientProvider.perClusterConcurrency()))
            .map { topicsBatch -> clientProvider.doWithClient(kafkaCluster) { it.topicsOffsets(topicsBatch) } }
            .apply { CompletableFuture.allOf(*toTypedArray()).get() }
            .flatMap { batchFuture -> batchFuture.get().map { it.toPair() } }
            .associate { it }
        return ClusterTopicOffsets(
            topicsOffsets = topicsOffsets
        )
    }

}
