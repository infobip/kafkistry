package com.infobip.kafkistry.kafkastate

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*

class KafkaRecordSamplingIncompleteException(msg: String) : RuntimeException(msg)

@Component
class KafkaRecordSamplerProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    promProperties: PrometheusMetricsProperties,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
    private val clientProvider: KafkaClientProvider,
    private val recordSamplerListeners: Optional<List<RecordSamplingListener>>,
) : AbstractKafkaStateProvider<Unit>(
    clustersRepository, clusterFilter, promProperties, issuesRegistry,
) {
    companion object {
        const val RECORDS_SAMPLING = "records_sampling"
    }

    override fun stateTypeName() = RECORDS_SAMPLING

    @Scheduled(
        fixedRateString = "#{poolingProperties.recordSamplingIntervalMs()}",
        initialDelayString = "#{poolingProperties.recordSamplingIntervalMs() / 2}"
    )
    override fun refreshClustersStates() = doRefreshClustersStates()

    override fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) {
        recordSamplerListeners.orElse(emptyList()).forEach { it.clusterRemoved(clusterIdentifier) }
    }

    override fun fetchState(kafkaCluster: KafkaCluster) {
        val offsetsLatestState = topicOffsetsProvider.getLatestState(kafkaCluster.identifier)
        if (offsetsLatestState.stateType != StateType.VISIBLE) {
            return
        }
        val latestOffsets = offsetsLatestState.value()
        val oldestStats = handleSampling(kafkaCluster, latestOffsets.topicsOffsets, SamplingPosition.OLDEST)
        val newestStats = handleSampling(kafkaCluster, latestOffsets.topicsOffsets, SamplingPosition.NEWEST)
        if (!oldestStats.isCompleteCovered()) {
            throw KafkaRecordSamplingIncompleteException(
                "Did not cover all non empty partitions, stats: OLDEST=$oldestStats, NEWEST=$newestStats"
            )
        }
    }

    private fun handleSampling(
        kafkaCluster: KafkaCluster,
        topicPartitionOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
        samplingPosition: SamplingPosition,
    ): SamplingStats {
        val visitor = visitorFor(kafkaCluster, topicPartitionOffsets.keys, samplingPosition)
            ?: return SamplingStats.EMPTY
        val filteredTopicsOffsets = topicPartitionOffsets
            .filterKeys { visitor.needsTopic(it) } // read only topics that sampler(s) want
        try {
            val stats = clientProvider.doWithClient(kafkaCluster) { client ->
                client.sampleRecords(filteredTopicsOffsets, samplingPosition, visitor)
            }
            visitor.samplingRoundCompleted()
            return stats
        } catch (ex: Throwable) {
            visitor.samplingRoundFailed(ex)
            throw ex
        }
    }

    private fun visitorFor(
        kafkaCluster: KafkaCluster,
        topics: Set<TopicName>,
        samplingPosition: SamplingPosition
    ): SamplersRecordVisitor? {
        val allListeners = recordSamplerListeners.orElse(emptyList())
            .takeIf { it.isNotEmpty() }
            ?: return null  //no listeners -> no visitor
        val samplersTopics = allListeners
            .map {
                it to topics.filter { topic ->
                    it.need(samplingPosition, kafkaCluster.ref(), topic)
                }
            }
            .filter { (_, topics) -> topics.isNotEmpty() }
            .map { (listener, topics) ->
                listener.sampler(samplingPosition, kafkaCluster.ref()) to topics.toSet()
            }
            .takeIf { it.isNotEmpty() }
            ?: return null  //nobody wants anything
        val topicSamplers = samplersTopics
            .flatMap { (sampler, topics) -> topics.map { sampler to it } }
            .groupBy({ it.second }, { it.first })
        return SamplersRecordVisitor(samplersTopics.map { it.first }, topicSamplers)
    }

}

/**
 * Adapter which visits consumed records and forwards record to each sampler while making sure that
 * uncaught exception will not propagate back to visitor invocation
 */
private class SamplersRecordVisitor(
    private val samplers: List<RecordSampler>,
    private val topicSamplers: Map<TopicName, List<RecordSampler>>,
) : RecordVisitor {

    override fun visit(record: ConsumerRecord<ByteArray?, ByteArray?>) {
        topicSamplers[record.topic()]?.forEachNoException {
            it.acceptRecord(record)
        }
    }

    fun needsTopic(topic: TopicName) = topic in topicSamplers.keys

    fun samplingRoundCompleted() = samplers.forEachNoException { it.samplingRoundCompleted() }

    fun samplingRoundFailed(cause: Throwable) = samplers.forEachNoException { it.samplingRoundFailed(cause) }

    private inline fun List<RecordSampler>.forEachNoException(op: (RecordSampler) -> Unit) {
        forEach {
            try {
                op(it)
            } catch (_: Exception) {
                //make sure exception from one sampler doesn't propagate
            }
        }
    }

}


