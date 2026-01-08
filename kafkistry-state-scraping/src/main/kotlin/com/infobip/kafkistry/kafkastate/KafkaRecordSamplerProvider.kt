package com.infobip.kafkistry.kafkastate

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.kafkastate.coordination.SampledConsumerRecord
import com.infobip.kafkistry.kafkastate.coordination.SamplingCompletedEvent
import com.infobip.kafkistry.kafkastate.coordination.SamplingEventListener
import com.infobip.kafkistry.kafkastate.coordination.SamplingStartedEvent
import com.infobip.kafkistry.kafkastate.coordination.StateDataPublisher
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.utils.deepToString
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class SamplersStates(
    val states: List<SamplerState<*>>,
) {
    private val allStates: Map<Any, SamplerState<*>> = states.associateBy { it.samplingListener }

    fun <T> getFor(clazz: Class<in RecordSamplingListener<T>>): T? {
        val value = allStates[clazz] ?: return null
        @Suppress("UNCHECKED_CAST")
        return value.value as T?
    }
}

@Component
class KafkaRecordSamplerProvider(
    private val clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    promProperties: PrometheusMetricsProperties,
    poolingProperties: PoolingProperties,
    scrapingCoordinator: com.infobip.kafkistry.kafkastate.coordination.StateScrapingCoordinator,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val stateDataPublisher: StateDataPublisher,
    private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
    private val clientProvider: KafkaClientProvider,
    private val recordSamplerListeners: Optional<List<RecordSamplingListener<*>>>,
) : AbstractKafkaStateProvider<Unit>(
    clustersRepository, clusterFilter, promProperties, poolingProperties,
    scrapingCoordinator, issuesRegistry, stateDataPublisher,
) : AbstractKafkaStateProvider<SamplersStates>(
    clustersRepository, clusterFilter, promProperties, issuesRegistry,
) {
    companion object {
        const val RECORDS_SAMPLING = "records_sampling"
    }

    // Store prepared samplers for lifecycle coordination
    // Key: "clusterIdentifier:samplingPosition"
    private val preparedSamplers = ConcurrentHashMap<String, Samplers>()

    init {
        // Subscribe to sampling lifecycle events from other instances
        stateDataPublisher.subscribeToSamplingEvents(object : SamplingEventListener {
            override fun onSamplingStarted(event: SamplingStartedEvent) {
                log.debug("Received sampling started for {}/{} from another instance ({} topics)",
                    event.clusterIdentifier, event.samplingPosition, event.topics.size)
                event.prepareSamplersForRemoteInstance()
            }

            override fun onSampledRecord(event: SampledConsumerRecord) {
                log.trace("Received sampled record for {}/{}/{} from another instance (offset: {})",
                    event.clusterIdentifier, event.topic, event.partition, event.offset)
                event.forwardToLocalListeners()
            }

            override fun onSamplingCompleted(event: SamplingCompletedEvent) {
                log.debug("Received sampling completed for {}/{} from another instance (success: {})",
                    event.clusterIdentifier, event.samplingPosition, event.success)
                event.finalizeSamplersForRemoteInstance()
            }
        })

        log.info("Initialized record sampler provider with distributed coordination")
    }

    override fun stateTypeName() = RECORDS_SAMPLING

    @Scheduled(
        fixedRateString = "#{poolingProperties.recordSamplingIntervalMs()}",
        initialDelayString = "#{poolingProperties.recordSamplingIntervalMs() / 2}"
    )
    override fun scheduledRefreshClustersStates() = doRefreshClustersStates(RefreshInitiation.SCHEDULED)

    override fun refreshIntervalMs(): Long = poolingProperties.recordSamplingIntervalMs

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
            recordSamplerListeners.orElse(emptyList()).map { it.sampledState(kafkaCluster.identifier) }
        )
    }

    private fun handleSampling(
        kafkaCluster: KafkaCluster,
        topicPartitionOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
        samplingPosition: SamplingPosition,
    ) {
        val samplers = createTopicSamplers(kafkaCluster.identifier, topicPartitionOffsets.keys, samplingPosition)
            ?: return
        val visitor = SamplersRecordVisitor(samplers, stateDataPublisher, kafkaCluster.ref(), samplingPosition,)
        val filteredTopicsOffsets = topicPartitionOffsets
            .filterKeys { visitor.needsTopic(it) } // read only topics that sampler(s) want

        runCatching {
            // Publish sampling started event and don't fail if publishing fails
            val event = SamplingStartedEvent(
                clusterIdentifier = kafkaCluster.identifier,
                samplingPosition = samplingPosition,
                topics = filteredTopicsOffsets.keys.toSet(), //keys from map are not Serializable
            )
            stateDataPublisher.publishSamplingStarted(event)
        }

        try {
            clientProvider.doWithClient(kafkaCluster) { client ->
                client.sampleRecords(filteredTopicsOffsets, samplingPosition, visitor)
            }
            visitor.samplingRoundCompleted()
            runCatching {
                // Publish sampling completed (success) and don't fail if publishing fails
                val event = SamplingCompletedEvent(
                    clusterIdentifier = kafkaCluster.identifier,
                    samplingPosition = samplingPosition,
                    success = true,
                )
                stateDataPublisher.publishSamplingCompleted(event)
            }
        } catch (ex: Throwable) {
            visitor.samplingRoundFailed(ex)
            runCatching {
                // Publish sampling completed (failure) and don't fail if publishing fails
                val event = SamplingCompletedEvent(
                    clusterIdentifier = kafkaCluster.identifier,
                    samplingPosition = samplingPosition,
                    success = false,
                    cause = ex.deepToString(),
                )
                stateDataPublisher.publishSamplingCompleted(event)
            }
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

        val clusterRef = clustersRepository.findById(clusterIdentifier)?.ref() ?: return null
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

    private fun samplerKey(clusterIdentifier: KafkaClusterIdentifier, samplingPosition: SamplingPosition): String {
        return "${clusterIdentifier}:$samplingPosition"
    }

    private fun SamplingStartedEvent.prepareSamplersForRemoteInstance() {
        val samplers = createTopicSamplers(clusterIdentifier, topics, samplingPosition) ?: return  // No listeners interested

        // Store prepared samplers for later use
        val key = samplerKey(clusterIdentifier, samplingPosition)
        preparedSamplers[key] = samplers

        log.debug("Prepared samplers for {}/{} ({} topics)", clusterIdentifier, samplingPosition, samplers.forTopic.size)
    }

    private fun SamplingCompletedEvent.finalizeSamplersForRemoteInstance() {
        val key = samplerKey(clusterIdentifier, samplingPosition)
        val samplers = preparedSamplers.remove(key) ?: return  // No prepared samplers

        // Call completion/failure callbacks on all samplers
        if (success) {
            samplers.all.forEachNoException { it.samplingRoundCompleted() }
        } else {
            val cause = Throwable(cause ?: "Unknown error")
            samplers.all.forEachNoException { it.samplingRoundFailed(cause) }
        }

        log.debug("Finalized samplers for {}/{} (success: {})", clusterIdentifier, samplingPosition, success)
    }

    private fun SampledConsumerRecord.forwardToLocalListeners() {
        // Check if we have prepared samplers for this cluster/position
        val key = samplerKey(clusterIdentifier, samplingPosition)
        val samplers = preparedSamplers[key] ?: return

        // Get samplers for this specific topic
        val matchingSamplers = samplers.forTopic[topic] ?: return


        // Convert event back to ConsumerRecord
        val consumerRecord = toConsumerRecord()

        // Forward to each sampler
        matchingSamplers.forEachNoException {
            it.acceptRecord(consumerRecord)
        }
    }

}

private class Samplers(
    val all: List<RecordSampler>,
    val forTopic: Map<TopicName, List<RecordSampler>>,
)

/**
 * Adapter which visits consumed records and forwards record to each sampler while making sure that
 * uncaught exception will not propagate back to visitor invocation.
 *
 * Also publishes sampled records to Hazelcast for sharing with other instances.
 */
private class SamplersRecordVisitor(
    private val samplers: List<RecordSampler<*>>,
    private val stateDataPublisher: StateDataPublisher,
    private val clusterRef: ClusterRef,
    private val samplingPosition: SamplingPosition,
    private val topicSamplers: Map<TopicName, List<RecordSampler<*>>>,
) : RecordVisitor {

    override fun visit(record: ConsumerRecord<ByteArray?, ByteArray?>) {
        // Forward to local samplers
        samplers.forTopic[record.topic()]?.forEachNoException {
            it.acceptRecord(record)
        }

        // Publish to other instances via Hazelcast
        runCatching {
            val event = SampledConsumerRecord.from(clusterRef.identifier, samplingPosition, record)
            stateDataPublisher.publishSampledRecord(event)
        }
    }

    fun needsTopic(topic: TopicName) = topic in samplers.forTopic.keys

    fun samplingRoundCompleted() = samplers.all.forEachNoException { it.samplingRoundCompleted() }

    fun samplingRoundFailed(cause: Throwable) = samplers.all.forEachNoException { it.samplingRoundFailed(cause) }

}

private inline fun List<RecordSampler>.forEachNoException(op: (RecordSampler) -> Unit) {
    forEach {
        try {
            op(it)
        } catch (_: Exception) {
            //make sure exception from one sampler doesn't propagate
        }
    }
}
    private inline fun List<RecordSampler<*>>.forEachNoException(op: (RecordSampler<*>) -> Unit) {
        forEach {
            try {
                op(it)
            } catch (_: Exception) {
                //make sure exception from one sampler doesn't propagate
            }
        }
    }
