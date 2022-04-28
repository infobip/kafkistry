package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryClusterReadException
import org.apache.kafka.clients.admin.DescribeTopicsOptions
import org.apache.kafka.clients.admin.ListOffsetsOptions
import org.apache.kafka.clients.admin.ListOffsetsResult
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.CompletableFuture

class TopicOffsetsOps(
    clientCtx: ClientCtx,
): BaseOps(clientCtx) {

    fun topicsOffsets(topicNames: List<TopicName>): CompletableFuture<Map<TopicName, Map<Partition, PartitionOffsets>>> {
        fun combineTopicOffsetsResult(
            allTopicsPartitions: List<TopicPartition>,
            endOffsets: Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>,
            beginOffsets: Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>
        ): Map<TopicName, Map<Partition, PartitionOffsets>> {
            return allTopicsPartitions.groupBy { it.topic() }
                .mapValues { (_, partitions) ->
                    partitions.sortedBy { it.partition() }.associate {
                        val begin = beginOffsets[it]
                            ?: throw KafkistryClusterReadException("Could not read begin offset for $it")
                        val end = endOffsets[it]
                            ?: throw KafkistryClusterReadException("Could not read end offset for $it")
                        val partitionOffsets = PartitionOffsets(
                            begin = begin.offset(),
                            end = end.offset()
                        )
                        it.partition() to partitionOffsets
                    }
                }
        }
        return adminClient
            .describeTopics(topicNames, DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe topics for offests")
            .thenApply { topicsPartitionDescriptions ->
                topicsPartitionDescriptions.flatMap { (topicName, partitionsDescription) ->
                    val hasPartitionWithNoLeader = partitionsDescription.partitions().any { it.leader() == null }
                    if (hasPartitionWithNoLeader) {
                        emptyList()
                    } else {
                        partitionsDescription.partitions().map { TopicPartition(topicName, it.partition()) }
                    }
                }
            }
            .thenCompose { allTopicsPartitions ->
                adminClient
                    .listOffsets(
                        allTopicsPartitions.associateWith { OffsetSpec.latest() },
                        ListOffsetsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("list topics latest offsets")
                    .thenApply { endOffsets -> allTopicsPartitions to endOffsets }
            }
            .thenCompose { (allTopicsPartitions, endOffsets) ->
                adminClient
                    .listOffsets(
                        allTopicsPartitions.associateWith { OffsetSpec.earliest() },
                        ListOffsetsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("list topics earliest offsets")
                    .thenApply { beginOffsets ->
                        combineTopicOffsetsResult(allTopicsPartitions, endOffsets, beginOffsets)
                    }
            }
    }


}