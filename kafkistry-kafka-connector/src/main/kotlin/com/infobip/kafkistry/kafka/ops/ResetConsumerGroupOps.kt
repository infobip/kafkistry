package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ResetConsumerGroupOps(
    clientCtx: ClientCtx,
    private val consumerGroupOps: ConsumerGroupOps,
    private val topicOffsetsOps: TopicOffsetsOps,
    private val consumerSupplier: ClientFactory.ConsumerSupplier,
) : BaseOps(clientCtx) {

    fun resetConsumerGroup(
        groupId: ConsumerGroupId,
        reset: GroupOffsetsReset
    ): CompletableFuture<GroupOffsetResetChange> {
        val hasPartitionsToReset = reset.topics.any { it.partitions == null || it.partitions.isNotEmpty() }
        if (!hasPartitionsToReset) {
            throw KafkaClusterManagementException("Can't perform reset, no topic/partitions selected")
        }
        val topicsOffsets = topicOffsetsOps.topicsOffsets(reset.topics.map { it.topic })
        val consumerGroupFuture = consumerGroupOps.consumerGroup(groupId)
        return with(ResetConsumerGroupCtx(groupId, reset)) {
            val currentGroupOffsets: Map<TopicPartition, Long> by lazy { currentGroupOffsets() }
            CompletableFuture.allOf(topicsOffsets, consumerGroupFuture)
                .thenApply { checkGroupState(consumerGroupFuture.get()) }
                .thenApply { resolveTopicPartitionSeeks(topicsOffsets.get()) }
                .thenCompose { resolveTargetOffsets(it, currentGroupOffsets) }
                .thenApply { ensureTargetOffsetsWithinBounds(it, topicsOffsets.get()) }
                .thenCompose { targetOffsets -> doResetConsumerGroup(targetOffsets).thenApply { targetOffsets } }
                .thenApply { targetOffsets ->
                    constructResult(currentGroupOffsets, targetOffsets, topicsOffsets.get(), consumerGroupFuture.get())
                }
        }
    }

    private inner class ResetConsumerGroupCtx(
        val groupId: ConsumerGroupId,
        val reset: GroupOffsetsReset,
    ) {

        fun checkGroupState(consumerGroup: ConsumerGroup) {
            when (consumerGroup.status) {
                ConsumerGroupStatus.EMPTY, ConsumerGroupStatus.DEAD, ConsumerGroupStatus.UNKNOWN -> Unit
                else -> throw KafkaClusterManagementException(
                    "Aborting reset to consumer group's '$groupId' offset(s) because it need to be inactive, " +
                            "current state: " + consumerGroup.status
                )
            }
        }

        fun currentGroupOffsets(): Map<TopicPartition, Long> {
            return consumerSupplier.createNewConsumer { props ->
                props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
                props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            }.use { consumer ->
                val subscribeLatch = CountDownLatch(1)
                consumer.subscribe(reset.topics.map { it.topic }, object : ConsumerRebalanceListener {
                    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) =
                        subscribeLatch.countDown()

                    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) = Unit
                })
                //do some polling which is needed for KafkaConsumer to assign offsets
                val consumedRecordsCounts = sequence {
                    var remainingAttempts = 10
                    while (remainingAttempts > 0) {
                        consumer.poll(Duration.ofSeconds(1)).also { yieldAll(it) }
                        val subscribed = subscribeLatch.await(1, TimeUnit.SECONDS)
                        if (subscribed) {
                            break
                        }
                        remainingAttempts--
                    }
                }.groupingBy { TopicPartition(it.topic(), it.partition()) }.eachCount()

                consumer.assignment().associateWith { topicPartition ->
                    val currentOffset = consumer.position(topicPartition, readTimeoutDuration())
                    val correction = consumedRecordsCounts[topicPartition] ?: 0
                    currentOffset - correction
                }
            }
        }

        fun resolveTopicPartitionSeeks(
            topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
        ): Map<TopicPartition, OffsetSeek> {
            return reset.topics
                .associateBy { it.topic }
                .mapValues { (topic, topicSeek) ->
                    val topicPartitions = topicsOffsets[topic]?.keys
                        ?: throw KafkaClusterManagementException("Did not get response offsets for topic '$topic'")
                    when (topicSeek.partitions) {
                        null -> topicPartitions.associate { TopicPartition(topic, it) to reset.seek }
                        else -> topicSeek.partitions.associate {
                            val topicPartition = TopicPartition(topic, it.partition)
                            if (it.partition !in topicPartitions) {
                                throw KafkaClusterManagementException("$topicPartition does not exist, can't perform offset reset")
                            }
                            topicPartition to (it.seek ?: reset.seek)
                        }
                    }
                }
                .flatMap { (_, partitionSeeks) ->
                    partitionSeeks.map { it.toPair() }
                }
                .associate { it }
        }

        fun resolveTargetOffsets(
            topicPartitionSeeks: Map<TopicPartition, OffsetSeek>,
            currentGroupOffsets: Map<TopicPartition, Long>
        ): CompletableFuture<Map<TopicPartition, Long>> {
            val lookupSeeks = mutableMapOf<TopicPartition, OffsetSpec>()
            val explicitOffsets = mutableMapOf<TopicPartition, Long>()
            val relativeSeeks = mutableMapOf<TopicPartition, Long>()
            val lookupClones = mutableMapOf<TopicPartition, ConsumerGroupId>()
            topicPartitionSeeks.forEach { (topicPartition, seek) ->
                when (seek.type) {
                    OffsetSeekType.EARLIEST -> lookupSeeks[topicPartition] = OffsetSpec.earliest()
                    OffsetSeekType.LATEST -> lookupSeeks[topicPartition] = OffsetSpec.latest()
                    OffsetSeekType.TIMESTAMP -> lookupSeeks[topicPartition] = OffsetSpec.forTimestamp(seek.timestamp())
                    OffsetSeekType.EXPLICIT -> explicitOffsets[topicPartition] = seek.offset()
                    OffsetSeekType.RELATIVE -> relativeSeeks[topicPartition] = seek.offset()
                    OffsetSeekType.CLONE -> lookupClones[topicPartition] = seek.cloneFromConsumerGroup()
                }
            }

            val lookupOffsetsFuture = if (lookupSeeks.isNotEmpty()) {
                adminClient
                    .listOffsets(lookupSeeks, ListOffsetsOptions().withReadTimeout())
                    .all()
                    .asCompletableFuture("reset offsets - list topic offsets")
                    .thenApply { topicOffsets -> topicOffsets.mapValues { it.value.offset() } }
                    .thenApply { topicOffsets ->
                        topicOffsets.mapValues { (topicPartition, offset) ->
                            topicPartitionSeeks[topicPartition]?.let {
                                when (it.type) {
                                    OffsetSeekType.EARLIEST -> offset + it.offset()
                                    OffsetSeekType.LATEST -> offset - it.offset()
                                    else -> null
                                }
                            } ?: offset
                        }
                    }
            } else {
                CompletableFuture.completedFuture(emptyMap())
            }

            val cloneOffsetsFutures = lookupClones.map { it }
                .groupBy({ it.value }, { it.key })
                .map { (clonedGroup, neededTopicPartitions) ->
                    adminClient
                        .listConsumerGroupOffsets(clonedGroup, ListConsumerGroupOffsetsOptions().withReadTimeout())
                        .partitionsToOffsetAndMetadata()
                        .asCompletableFuture("reset offsets - list group offsets")
                        .thenApply { groupOffsets ->
                            neededTopicPartitions.associateWith {
                                groupOffsets[it]?.offset() ?: throw KafkaClusterManagementException(
                                    "Tried to clone offset from $it of group '$clonedGroup', " +
                                            "but that group have no committed offset for that topic partition"
                                )
                            }
                        }
                }

            val relativeOffsets = if (relativeSeeks.isNotEmpty()) {
                relativeSeeks.mapValues { (topicPartition, seek) ->
                    val currentOffset = currentGroupOffsets[topicPartition]
                        ?: throw KafkaClusterManagementException(
                            "Can't perform relative seek for topic partition not assigned to consumer group: $topicPartition, " +
                                    "there might be other active consumer in group"
                        )
                    currentOffset + seek
                }
            } else {
                emptyMap()
            }

            return CompletableFuture.allOf(lookupOffsetsFuture, *cloneOffsetsFutures.toTypedArray()).thenApply {
                val clonedOffsets = cloneOffsetsFutures.map { it.get() }
                    .takeIf { it.isNotEmpty() }
                    ?.reduce { acc, partitionOffsets -> acc + partitionOffsets }
                    .orEmpty()
                explicitOffsets + lookupOffsetsFuture.get() + relativeOffsets + clonedOffsets
            }
        }

        fun ensureTargetOffsetsWithinBounds(
            targetOffsets: Map<TopicPartition, Long>,
            topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
        ): Map<TopicPartition, Long> {
            return targetOffsets.mapValues { (topicPartition, offset) ->
                val partitionOffsets = topicsOffsets[topicPartition.topic()]
                    ?.get(topicPartition.partition())
                    ?: return@mapValues offset
                if (offset == -1L) {
                    //timestamp lookup returns -1 if no newer message than provided timestamp, set it to end
                    partitionOffsets.end
                } else {
                    //ensure final target offset is (>= begin) and (<= end)
                    offset.coerceIn(partitionOffsets.begin..partitionOffsets.end)
                }
            }
        }

        fun doResetConsumerGroup(
            topicPartitionTargetOffsets: Map<TopicPartition, Long>
        ): CompletableFuture<Void> {
            val offsets = topicPartitionTargetOffsets.mapValues { OffsetAndMetadata(it.value) }
            return adminClient
                .alterConsumerGroupOffsets(groupId, offsets, AlterConsumerGroupOffsetsOptions().withWriteTimeout())
                .all()
                .asCompletableFuture("reset offsets - alter offsets")
        }

        fun constructResult(
            currentOffsets: Map<TopicPartition, Long>,
            targetOffsets: Map<TopicPartition, Long>,
            topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>,
            currentConsumerGroup: ConsumerGroup,
        ): GroupOffsetResetChange {
            val newlyInitialized = currentConsumerGroup.status in setOf(
                ConsumerGroupStatus.DEAD, ConsumerGroupStatus.UNKNOWN
            )
            val changes = targetOffsets.map { (topicPartition, targetOffset) ->
                val partitionEndOffset = topicsOffsets[topicPartition.topic()]
                    ?.get(topicPartition.partition())
                    ?.end
                    ?: targetOffset
                TopicPartitionOffsetChange(
                    topic = topicPartition.topic(),
                    partition = topicPartition.partition(),
                    offset = targetOffset,
                    delta = currentOffsets[topicPartition]
                        ?.takeUnless { newlyInitialized }
                        ?.let { targetOffset - it },
                    lag = (partitionEndOffset - targetOffset).coerceAtLeast(0L),
                )
            }.sortedBy { it.topic + it.partition }
            return GroupOffsetResetChange(
                groupId = groupId,
                changes = changes,
                totalSkip = changes.mapNotNull { it.delta }.filter { it > 0 }.sum(),
                totalRewind = -changes.mapNotNull { it.delta }.filter { it < 0 }.sum(),
                totalLag = changes.sumOf { it.lag },
            )
        }
    }

}
