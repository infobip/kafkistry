package com.infobip.kafkistry.service.consume

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndTimestamp
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException
import org.apache.kafka.common.TopicPartition
import com.infobip.kafkistry.service.consume.OffsetType.*
import com.infobip.kafkistry.service.consume.config.ConsumeProperties
import com.infobip.kafkistry.service.consume.filter.RecordFilterFactory
import com.infobip.kafkistry.kafka.ClientFactory
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.connectionDefinition
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryConsumeException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class KafkaTopicReader(
    private val consumeProperties: ConsumeProperties,
    private val recordFactory: RecordFactory,
    private val filterFactory: RecordFilterFactory,
    private val clientFactory: ClientFactory
) {

    fun readTopicRecords(
        topicName: TopicName,
        cluster: KafkaCluster,
        username: String,
        readConfig: ReadConfig
    ): KafkaRecordsResult {
        readConfig.checkLimitations()
        return createConsumer(cluster, username, readConfig)
            .use {
                it.setup(topicName, readConfig)
                try {
                    it.readMessages(topicName, readConfig)
                } catch (ex: OffsetOutOfRangeException) {
                    it.translateInvalidOffsetException(ex)
                }
            }
    }

    private fun ReadConfig.checkLimitations() {
        if (numRecords > consumeProperties.maxRecords()) {
            throw KafkistryConsumeException("Maximum allowed maxRecords is %d, got: %d".format(
                consumeProperties.maxRecords, numRecords
            ))
        }
        if (maxWaitMs > consumeProperties.maxWaitMs()) {
            throw KafkistryConsumeException("Maximum allowed maxWaitMs is %d, got: %d".format(
                consumeProperties.maxWaitMs, maxWaitMs
            ))
        }
    }



    private fun createConsumer(
        cluster: KafkaCluster, username: String, readConfig: ReadConfig
    ): KafkaConsumer<ByteArray, ByteArray> {
        return clientFactory.createConsumer(cluster.connectionDefinition()) { props ->
            props[ConsumerConfig.GROUP_ID_CONFIG] = "kafkistry-$username"
            props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "${consumeProperties.poolBatchSize()}"
            props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            props[ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG] = "${readConfig.maxWaitMs}"
            props[ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "${readConfig.maxWaitMs}"
            props[ConsumerConfig.ISOLATION_LEVEL_CONFIG] =
                if (readConfig.readOnlyCommitted) "read_committed" else "read_uncommitted"
        }
    }

    private fun KafkaConsumer<*, *>.setup(topicName: TopicName, readConfig: ReadConfig) {
        val allTopicPartitions = partitionsFor(topicName)?.map { it.partition() }
            ?: throw KafkistryConsumeException("Topic '$topicName' does not exist on cluster")
        val partitions = with(readConfig) {
            val unknownPartitions = (partitions.orEmpty() + notPartitions.orEmpty())
                .filter { it !in allTopicPartitions }
            if (unknownPartitions.isNotEmpty()) {
                throw KafkistryConsumeException(
                    "Partition(s) %s do not exist for topic '%s', existing partitions are [%d..%d]".format(
                        unknownPartitions, topicName, allTopicPartitions.minOrNull(), allTopicPartitions.maxOrNull()
                    )
                )
            }
            allTopicPartitions
                .filter { partitions == null || it in partitions }
                .filter { notPartitions == null || it !in notPartitions }
                .map { TopicPartition(topicName, it) }
        }
        assign(partitions)
        setOffsets(readConfig.fromOffset, readConfig.partitionFromOffset.orEmpty())
    }

    private fun KafkaConsumer<*, *>.setOffsets(fromOffset: Offset, partitionFromOffset: Map<Partition, Long>) {
        val partitionsToResolve = assignment().filter { it.partition() !in partitionFromOffset.keys }
        fun Collection<TopicPartition>.calcOffsets(calc: (begin: Long, end: Long) -> Long): Map<TopicPartition, Long> {
            val beginOffsets = beginningOffsets(this)
            val endOffsets = endOffsets(this)
            return (beginOffsets.keys + endOffsets.keys).distinct()
                .associateWith { topicPartition ->
                    val begin = beginOffsets[topicPartition] ?: 0L
                    val end = endOffsets[topicPartition] ?: 0L
                    calc(begin, end).coerceIn(begin, end)
                }
        }

        val resolvedOffsets = when (fromOffset.type) {
            EARLIEST -> partitionsToResolve.calcOffsets { begin, _ -> begin + fromOffset.offset }
            LATEST -> partitionsToResolve.calcOffsets { _, end -> end - fromOffset.offset }
            EXPLICIT -> partitionsToResolve.associateWith { fromOffset.offset }
            TIMESTAMP -> offsetsForTimes(partitionsToResolve.associateWith { fromOffset.offset })
                .filter { (_: TopicPartition, time: OffsetAndTimestamp?) -> time != null }
                .mapValues { (_: TopicPartition, time: OffsetAndTimestamp) -> time.offset() }
                .let { partitionOffsets ->
                    val unresolvedPartitions = partitionsToResolve.filter { it !in partitionOffsets.keys }
                    if (unresolvedPartitions.toSet() == assignment()) throw KafkistryConsumeException(
                        "There is no messages with timestamp greater than %d".format(fromOffset.offset)
                    )
                    val defaultToEndOffsets = unresolvedPartitions.calcOffsets { _, end -> end }
                    partitionOffsets + defaultToEndOffsets
                }
        }
        val newOffsets = assignment().associateWith {
            partitionFromOffset[it.partition()] ?: resolvedOffsets[it] ?: 0L
        }
        newOffsets.forEach { (topicPartition, offset) -> seek(topicPartition, offset) }
    }

    /**
     * Function reads at most `readConfig.numRecords` records starting from already assigned offsets for consumer group.
     */
    private fun KafkaConsumer<ByteArray, ByteArray>.readMessages(
        topicName: TopicName, readConfig: ReadConfig
    ): KafkaRecordsResult {
        val recordCreator = recordFactory.creatorFor(readConfig.recordDeserialization)
        val readMonitor = ReadMonitor.ofConfig(readConfig)
        val poolDuration = Duration.ofMillis(consumeProperties.poolInterval())
        val initialPositions = assignment().sortedBy { it.partition() }.associate { it.partition() to position(it) }
        val endOffsets = endOffsets(assignment())
        val recordsSequence = sequence {
            while (true) {
                if (!readMonitor.needToReadMore()) break
                poll(poolDuration).also { yieldAll(it) }
            }
        }
        val recordFilter = filterFactory.createFilter(readConfig.readFilter)
        val records = recordsSequence
            .onEach { readMonitor.receivedRecord(it) }
            .map(recordCreator::create)
            .filter(recordFilter)
            .onEach { readMonitor.gotFilteredRecord(it) }
            .take(readConfig.numRecords)
            .toList()
        val partitionsReadStatuses = readMonitor.partitionsReadStatus()
        return KafkaRecordsResult(
            totalCount = readMonitor.totalRecords(),
            timedOut = readMonitor.hasTimedOut(),
            reachedEnd = endOffsets.all { position(it.key) >= it.value },
            partitions = initialPositions.mapValues { (partition, offset) ->
                val stats = partitionsReadStatuses[partition]
                if (stats != null) {
                    PartitionReadStatus(
                        startedAt = stats.first,
                        endedAt = stats.last + 1,
                        read = stats.last - stats.first + 1,
                        matching = stats.matching,
                        reachedEnd = stats.last + 1 >= (endOffsets[TopicPartition(topicName, partition)] ?: 0L)
                    )
                } else {
                    val reachedEnd = offset >= (endOffsets[TopicPartition(topicName, partition)] ?: 0L)
                    PartitionReadStatus(
                        startedAt = offset, endedAt = offset, read = 0, matching = 0, reachedEnd = reachedEnd
                    )
                }
            },
            records = records
        )
    }

    private fun KafkaConsumer<*, *>.translateInvalidOffsetException(
        ex: OffsetOutOfRangeException
    ): Nothing {
        val beginningOffsets = beginningOffsets(assignment())
        val (tooSmallOffsetPartitions, tooBigOffsetPartitions) = ex.offsetOutOfRangePartitions()
            .map { it }
            .partition { (topicPartition, offset) -> offset < (beginningOffsets[topicPartition] ?: 0) }
            .let { (tooSmall, tooBig) ->
                tooSmall.map { it.key.partition() } to tooBig.map { it.key.partition() }
            }
        when {
            tooSmallOffsetPartitions.isNotEmpty() && tooBigOffsetPartitions.isNotEmpty() -> {
                throw KafkistryConsumeException(
                    "Requested offset for partitions $tooSmallOffsetPartitions is lower than earliest possible; " +
                            "Requested offset for partitions $tooBigOffsetPartitions is bigger than latest possible", ex
                )
            }
            tooSmallOffsetPartitions.isNotEmpty() -> throw KafkistryConsumeException(
                "Requested offset for partitions $tooSmallOffsetPartitions is lower than earliest possible", ex
            )
            tooBigOffsetPartitions.isNotEmpty() -> throw KafkistryConsumeException(
                "Requested offset for partitions $tooBigOffsetPartitions is bigger than latest possible", ex
            )
            else -> throw ex
        }
    }

}
