package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.model.KafkaCluster
import org.springframework.stereotype.Component
import java.util.concurrent.CompletionException
import kotlin.math.max

@Component
class KafkaConsumerGroupsProvider(
    components: StateProviderComponents,
    private val clientProvider: KafkaClientProvider,
) : AbstractKafkaStateProvider<ClusterConsumerGroups>(components) {

    companion object {
        const val CONSUMER_GROUPS = "consumer_groups"
    }

    override fun stateTypeName() = CONSUMER_GROUPS

    private val ignoreConsumerGroup = components.poolingProperties.ignoredConsumerPattern
        .takeIf { it.isNotEmpty() }
        ?.let { Regex(it) }

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterConsumerGroups {
        val consumerGroupIds = clientProvider.doWithClient(kafkaCluster) { it.consumerGroups().get() }
        val consumers = consumerGroupIds
            .filterNot { ignoreConsumerGroup?.matches(it) ?: false }
            .chunked(max(20, consumerGroupIds.size / clientProvider.perClusterConcurrency()))
            .associateWith { topicsBatch -> clientProvider.doWithClient(kafkaCluster) { it.consumerGroups(topicsBatch) } }
            .flatMap { (groupIds, batchFuture) ->
                try {
                    batchFuture.get().map { it.id to Maybe.Result(it) }
                } catch (ex: Throwable) {
                    val exception = (ex as? CompletionException)?.cause ?: ex
                    log.warn("Exception on fetching consumers '{}' on cluster '{}'",
                        groupIds, kafkaCluster.identifier, exception
                    )
                    groupIds.map { it to Maybe.Absent(ex) }
                }
            }
            .associate { it }
        return ClusterConsumerGroups(
                consumerGroups = consumers,
        )
    }

}
