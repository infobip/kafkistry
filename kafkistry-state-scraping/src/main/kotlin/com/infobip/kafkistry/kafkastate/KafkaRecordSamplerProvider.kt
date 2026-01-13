package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*

data class SamplersStates(
    val states: List<SamplerState<*>>,
) {
    private val allStates: Map<Any, SamplerState<*>> = states.associateBy { it.samplingListener }

    fun stateFor(clazz: Class<in RecordSamplingListener<*>>): SamplerState<*>? = allStates[clazz]
}

@Component
class KafkaRecordSamplerProvider(
    components: StateProviderComponents,
    private val clientProvider: KafkaClientProvider,
    private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
    private val recordSamplerListeners: Optional<List<RecordSamplingListener<*>>>,
) : AbstractKafkaStateProvider<SamplersStates>(components) {

    companion object {
        const val RECORDS_SAMPLING = "records_sampling"
    }

    override fun stateTypeName() = RECORDS_SAMPLING

    @Scheduled(
        fixedRateString = "#{poolingProperties.recordSamplingIntervalMs()}",
        initialDelayString = "#{poolingProperties.recordSamplingIntervalMs() / 2}"
    )
    override fun scheduledRefreshClustersStates() = doRefreshClustersStates(RefreshInitiation.SCHEDULED)

    override fun refreshIntervalMs(): Long = components.poolingProperties.recordSamplingIntervalMs

    override fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) {
        recordSamplerListeners.orElse(emptyList()).forEach { it.clusterRemoved(clusterIdentifier) }
    }

    override fun fetchState(kafkaCluster: KafkaCluster): SamplersStates {
        val offsetsLatestState = topicOffsetsProvider.getLatestState(kafkaCluster.identifier)
        if (offsetsLatestState.stateType != StateType.VISIBLE) {
            return SamplersStates(emptyList())
        }
        val latestOffsets = offsetsLatestState.value()
        handleSampling(kafkaCluster, latestOffsets.topicsOffsets, SamplingPosition.OLDEST)
        handleSampling(kafkaCluster, latestOffsets.topicsOffsets, SamplingPosition.NEWEST)
        return SamplersStates(
            states = recordSamplerListeners.orElse(emptyList()).map {
                it.sampledState(kafkaCluster.identifier)
            },
        )
    }

    override fun receivedStateData(data: StateData<SamplersStates>) {
        recordSamplerListeners.orElse(emptyList()).forEach {
            val samplerState = data.valueOrNull()
                ?.stateFor(it.javaClass)
                ?: return@forEach
            it.maybeAcceptStateUpdate(data.clusterIdentifier, samplerState)
        }
    }

    private fun handleSampling(
        kafkaCluster: KafkaCluster,
        topicPartitionOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
        samplingPosition: SamplingPosition,
    ) {
        val samplers = createTopicSamplers(kafkaCluster.identifier, topicPartitionOffsets.keys, samplingPosition) ?: return
        val visitor = SamplersRecordVisitor(samplers)
        val filteredTopicsOffsets = topicPartitionOffsets
            .filterKeys { visitor.needsTopic(it) } // read only topics that sampler(s) want
        try {
            clientProvider.doWithClient(kafkaCluster) { client ->
                client.sampleRecords(filteredTopicsOffsets, samplingPosition, visitor)
            }
            visitor.samplingRoundCompleted()
        } catch (ex: Throwable) {
            visitor.samplingRoundFailed(ex)
            throw ex
        }
    }

    /**
     * Create topic samplers for the given cluster, topics, and sampling position.
     * Returns a pair of (all samplers, topic-to-samplers map), or null if no listeners are interested.
     */
    private fun createTopicSamplers(
        clusterIdentifier: KafkaClusterIdentifier,
        topics: Set<TopicName>,
        samplingPosition: SamplingPosition,
    ): Samplers? {
        val allListeners = recordSamplerListeners.orElse(emptyList())
            .takeIf { it.isNotEmpty() }
            ?: return null  // No listeners

        val clusterRef = components.clustersRepository.findById(clusterIdentifier)?.ref() ?: return null
        val samplersTopics = allListeners
            .map {
                it to topics.filter { topic ->
                    it.need(samplingPosition, clusterRef, topic)
                }
            }
            .filter { (_, topics) -> topics.isNotEmpty() }
            .map { (listener, topics) ->
                listener.sampler(samplingPosition, clusterRef) to topics.toSet()
            }
            .takeIf { it.isNotEmpty() }
            ?: return null  // Nobody wants anything

        val topicSamplers = samplersTopics
            .flatMap { (sampler, topics) -> topics.map { sampler to it } }
            .groupBy({ it.second }, { it.first })

        return Samplers(all = samplersTopics.map { it.first }, forTopic = topicSamplers)
    }

}

private class Samplers(
    val all: List<RecordSampler<*>>,
    val forTopic: Map<TopicName, List<RecordSampler<*>>>,
)

/**
 * Adapter which visits consumed records and forwards record to each sampler while making sure that
 * uncaught exception will not propagate back to visitor invocation.
 */
private class SamplersRecordVisitor(
    private val samplers: Samplers,
) : RecordVisitor {

    override fun visit(record: ConsumerRecord<ByteArray?, ByteArray?>) {
        samplers.forTopic[record.topic()]?.forEachNoException {
            it.acceptRecord(record)
        }
    }

    fun needsTopic(topic: TopicName) = topic in samplers.forTopic.keys

    fun samplingRoundCompleted() = samplers.all.forEachNoException { it.samplingRoundCompleted() }

    fun samplingRoundFailed(cause: Throwable) = samplers.all.forEachNoException { it.samplingRoundFailed(cause) }

    private inline fun List<RecordSampler<*>>.forEachNoException(op: (RecordSampler<*>) -> Unit) {
        forEach {
            try {
                op(it)
            } catch (_: Exception) {
                //make sure exception from one sampler doesn't propagate
            }
        }
    }

}

