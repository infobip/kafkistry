package com.infobip.kafkistry.kafka.recordsampling

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import com.infobip.kafkistry.utils.doWithNoLoggingOverrides
import com.infobip.kafkistry.utils.doWithOnlyErrorLogging
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.kafka.RecordVisitor
import com.infobip.kafkistry.kafka.SamplingPosition
import com.infobip.kafkistry.model.TopicName
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Record sapling works by assigning consumer to every non-empty topic partition from which sample is wanted to be taken.
 * Then pooling is done in loop until one record from every partition is received.
 * After record is received from one partition, consumer is unsubscribed from that partition so that further pool calls
 * target only remaining partitions.
 */
class RecordReadSampler(
    private val consumer: KafkaConsumer<ByteArray, ByteArray>,
    initialPoolTimeout: Long,
    poolTimeout: Long,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(RecordReadSampler::class.java)

    private val initialTimeout = Duration.ofMillis(initialPoolTimeout)
    private val poolTimeout = Duration.ofMillis(poolTimeout)

    override fun close() = consumer.close(initialTimeout)

    fun test() = Unit

    /**
     * Perform round of sampling for every given [topicPartitionOffsets].
     * Sampling will sample at-most `1` records from every topic-partition.
     * It will be either one OLDEST or one NEWEST (but already produced) record in partition,
     * depending on [samplingPosition] argument.
     * Result is given via [recordVisitor] callback to allow processing of results as soon as available to
     * avoid need to collect everything in memory at once.
     */
    fun readSampleRecords(
        connectionName: String,
        topicPartitionOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
        samplingPosition: SamplingPosition,
        recordVisitor: RecordVisitor,
    ) {
        val allNeededPartitionsOffsets = extractNeededPartitions(topicPartitionOffsets)
        log.info("[{}] Going to sample records from {} topic partitions, position: {}",
            connectionName, allNeededPartitionsOffsets.size, samplingPosition
        )
        var count = 0
        doWithOnlyErrorLogging {
            doSampleRecords(connectionName, allNeededPartitionsOffsets, samplingPosition, recordVisitor.compose { count++ })
        }
        log.info("[{}] Sampled {} records from {} topic partitions, position: {}",
            connectionName, count, allNeededPartitionsOffsets.size, samplingPosition
        )
    }

    private fun doSampleRecords(
        connectionName: String,
        allNeededPartitionsOffsets: Map<TopicPartition, PartitionOffsets>,
        samplingPosition: SamplingPosition,
        recordVisitor: RecordVisitor,
    ) {
        val remainingPartitions = allNeededPartitionsOffsets.keys.toMutableSet()
        initializeConsumer(allNeededPartitionsOffsets, samplingPosition)
        var rewindAttempt = 0
        var timeout = initialTimeout
        var poolAttemptsLeft = allNeededPartitionsOffsets.size
        while (poolAttemptsLeft > 0) {
            poolAttemptsLeft--
            val record = consumer.poll(timeout).firstOrNull()
            if (record != null) {
                timeout = poolTimeout
                record.headers().hashCode() //force eager initialization of thread unsafe RecordHeader byteBuffer
                doWithNoLoggingOverrides {
                    recordVisitor.visit(record)
                }
                val topicPartition = TopicPartition(record.topic(), record.partition())
                val gotNew = remainingPartitions.remove(topicPartition)
                if (remainingPartitions.isEmpty()) {
                    break   //we collected records from all needed partitions
                }
                if (gotNew) {
                    consumer.assign(remainingPartitions)
                }
                continue
            }
            log.info(
                "[{}] Didn't get any record and still haven't received records from all partitions, position={}, rewindAttempt={}, numRemainingPartitions={}, numAllNeededPartitions={}",
                connectionName, samplingPosition, rewindAttempt, remainingPartitions.size, allNeededPartitionsOffsets.size
            )
            when (samplingPosition) {   //didn't get any record and still haven't received records from all partitions
                SamplingPosition.OLDEST -> break
                SamplingPosition.NEWEST -> {
                    if (rewindAttempt >= 2) break
                    poolAttemptsLeft += remainingPartitions.size
                    rewindAttempt++
                    rewindConsumer(allNeededPartitionsOffsets, remainingPartitions, rewindAttempt)
                }
            }
        }
    }

    private fun initializeConsumer(
        allNeededPartitionsOffsets: Map<TopicPartition, PartitionOffsets>,
        samplingPosition: SamplingPosition
    ) {
        consumer.assign(allNeededPartitionsOffsets.keys.toSet())
        allNeededPartitionsOffsets.forEach { (topicPartition, offsets) ->
            val offset = when (samplingPosition) {
                SamplingPosition.OLDEST -> offsets.begin
                SamplingPosition.NEWEST -> offsets.end.minus(1).coerceAtLeast(offsets.begin)
            }
            consumer.seek(topicPartition, offset)
        }
    }

    /**
     * Need to rewind backwards because some topic partitions might have few newest offsets being "used" for
     * transaction marker and therefore reading from `end-1` won't yield with any record (until new one is produced).
     */
    private fun rewindConsumer(
        allNeededPartitionsOffsets: Map<TopicPartition, PartitionOffsets>,
        remainingPartitions: MutableSet<TopicPartition>,
        rewindAttempt: Int
    ) {
        allNeededPartitionsOffsets
            .filterKeys { it in remainingPartitions }
            .forEach { (topicPartition, offsets) ->
                val newOffset = offsets.end.minus(rewindAttempt * 2).coerceAtLeast(offsets.begin)
                consumer.seek(topicPartition, newOffset)
            }
    }

    private fun extractNeededPartitions(
        topicPartitionOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
    ): Map<TopicPartition, PartitionOffsets> {
        return topicPartitionOffsets
            .flatMap { (topic, partitionsOffsets) ->
                partitionsOffsets
                    .filterValues { it.end > it.begin } //only partitions which have records
                    .map { (partition, offsets) ->
                        TopicPartition(topic, partition) to offsets
                    }
            }
            .associate { it }
    }

}
