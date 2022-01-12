package com.infobip.kafkistry.service.topic.offsets

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.kafkastate.KafkaTopicOffsetsProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * This is basically a container which holds instances of producing speed RateInstrument-s for
 * all topic partitions of all clusters.
 *
 * This component will periodically sample latest offsets and register/pass them to each RateInstrument
 * which can be used to ask what is producing speed for some specific topic partition.
 */
@Component
class TopicsRateProvider(
    private val clustersRepository: KafkaClustersRepository,
    private val topicOffsetsProvider: KafkaTopicOffsetsProvider,
    private val issuesRegistry: BackgroundJobIssuesRegistry
) {

    private val rates =
        ConcurrentHashMap<KafkaClusterIdentifier, ConcurrentHashMap<TopicName, ConcurrentHashMap<Partition, OffsetsRecorder>>>()

    private fun instrumentFor(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName, partition: Partition
    ): OffsetsRecorder = rates
        .computeIfAbsent(clusterIdentifier) { ConcurrentHashMap() }
        .computeIfAbsent(topicName) { ConcurrentHashMap() }
        .computeIfAbsent(partition) { OffsetsRecorder() }

    fun topicMessageRate(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName
    ): TopicMessagesRate? {
        val partitionRates = rates[clusterIdentifier]?.get(topicName) ?: return null
        return TopicMessagesRate(
            last15Sec = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate15sec),
            lastMin = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate1min),
            last5Min = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate5min),
            last15Min = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate15min),
            last30Min = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate30min),
            lastH = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate1h),
            last2H = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate2h),
            last6H = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate6h),
            last12H = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate12h),
            last24H = partitionRates.values.sumAsRatePerSec(OffsetsRecorder::rate24h),
        )
    }

    fun topicPartitionMessageRate(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName, partition: Partition
    ): PartitionRate {
        val offsetsRecorder = rates[clusterIdentifier]?.get(topicName)?.get(partition)
            ?: return PartitionRate(null, null)
        val rateUpTo15Min = offsetsRecorder.rate15MinOrLess()?.times(1000)
        val rateUpTo24H = offsetsRecorder.rate24HOrLess()?.times(1000) ?: rateUpTo15Min
        return PartitionRate(upTo15MinRate = rateUpTo15Min, upTo24HRate = rateUpTo24H)
    }

    private inline fun Iterable<OffsetsRecorder>.sumAsRatePerSec(range: OffsetsRecorder.() -> Double?): Double? {
        return mapNotNull { it.range() }
            .takeIf { it.isNotEmpty() }
            ?.let { it.sum() * 1000 }
    }

    @Scheduled(fixedRate = 15_000L)
    fun tryWriteAll() = issuesRegistry.doCapturingException("topicRate", "Topic message rate calculation") {
        refresh()
    }

    //start half hour sapling after 2min (after 8 of 15-sec samples)
    private var refreshCounter = AtomicInteger(-2 * ONE_MIN_SAMPLES)

    fun refresh() {
        val halfHourSample = refreshCounter.getAndIncrement() % HALF_HOUR_SAMPLES == 0
        val now = System.currentTimeMillis()
        val clusterIdentifiers = clustersRepository.findAll().map { it.identifier }
        clusterIdentifiers.asSequence()
            .map { it to topicOffsetsProvider.getLatestState(it) }
            .forEach { (clusterIdentifier, consumersAndOffsets) ->
                if (consumersAndOffsets.stateType == StateType.VISIBLE) {
                    observeTopicsOffsets(
                        clusterIdentifier, now, consumersAndOffsets.value().topicsOffsets, halfHourSample
                    )
                } else {
                    observeNothingOnCluster(clusterIdentifier, now, halfHourSample)
                }
            }
        rates.keys.removeIf { it !in clusterIdentifiers }
    }

    private fun observeTopicsOffsets(
        clusterIdentifier: KafkaClusterIdentifier, now: Long,
        topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
        halfHourSample: Boolean,
    ) {
        topicsOffsets.forEach { (topic, partitionsOffsets) ->
            partitionsOffsets.forEach { (partition, offsets) ->
                instrumentFor(clusterIdentifier, topic, partition)
                    .observe(offsets.end, now, halfHourSample)
            }
            rates[clusterIdentifier]?.get(topic)?.keys?.removeIf { it !in partitionsOffsets.keys }
        }
        rates[clusterIdentifier]?.keys?.removeIf { it !in topicsOffsets.keys }
    }

    private fun observeNothingOnCluster(
        clusterIdentifier: KafkaClusterIdentifier, now: Long, halfHourSample: Boolean,
    ) {
        rates[clusterIdentifier]
            ?.values
            ?.flatMap { it.values }
            ?.forEach { it.observeNothing(now, halfHourSample) }
    }

    private class OffsetsRecorder {

        val instrument15min = RateInstrument(60)    //covers 15min with sampling every 15sec
        val instrument24h = RateInstrument(48)      //covers 24h with sampling every 30min

        fun observe(offset: Long, now: Long, halfHourSample: Boolean) {
            instrument15min.observe(offset, now)
            if (halfHourSample) {
                instrument24h.observe(offset, now)
            }
        }

        fun observeNothing(now: Long, halfHourSample: Boolean) {
            instrument15min.observeNothing(now)
            if (halfHourSample) {
                instrument24h.observeNothing(now)
            }
        }

        fun rate15MinOrLess(): Double? = instrument15min.rate()
        fun rate24HOrLess(): Double? = instrument24h.rate()

        fun rate15sec(): Double? = instrument15min.rate(1)
        fun rate1min(): Double? = instrument15min.rate(4)
        fun rate5min(): Double? = instrument15min.rate(20)
        fun rate15min(): Double? = instrument15min.rate(60)
        fun rate30min(): Double? = instrument24h.rate(1)
        fun rate1h(): Double? = instrument24h.rate(2)
        fun rate2h(): Double? = instrument24h.rate(4)
        fun rate6h(): Double? = instrument24h.rate(12)
        fun rate12h(): Double? = instrument24h.rate(24)
        fun rate24h(): Double? = instrument24h.rate(48)
    }

    companion object {
        private const val ONE_MIN_SAMPLES = 4
        private const val HALF_HOUR_SAMPLES = 120
    }

}