package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryConsumeException
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consume.serialize.KeySerializerType
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

@Service
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class KafkaConsumerService(
    private val topicReader: KafkaTopicReader,
    private val clustersRepository: ClustersRegistryService,
    private val topicPartitionResolver: TopicPartitionResolver,
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val userResolver: CurrentRequestUserResolver,
    private val clusterEnabledFilter: ClusterEnabledFilter
) {

    fun readRecords(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        readConfig: ReadConfig
    ): KafkaRecordsResult {
        val cluster = clustersRepository.getCluster(clusterIdentifier)
        cluster.ref().checkClusterEnabled()
        val user = userResolver.resolveUserOrUnknown()
        return topicReader.readTopicRecords(topicName, cluster, user.username, readConfig)
    }

    fun readRecordsContinued(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        continuedReadConfig: ContinuedReadConfig,
    ): ContinuedKafkaRecordsResult {
        val nextResult = readRecords(clusterIdentifier, topicName, continuedReadConfig.readConfig)
        val overallPartitions = continuedReadConfig.previousPartitions mergeWithNext nextResult.partitions
        return ContinuedKafkaRecordsResult(
            recordsResult = nextResult,
            overallSkipCount = overallPartitions.values.sumOf { (it.startedAtOffset - it.beginOffset).coerceAtLeast(0) },
            overallReadCount = overallPartitions.values.sumOf { it.read },
            overallPartitions = overallPartitions,
        )
    }

    private infix fun Map<Partition, PartitionReadStatus>.mergeWithNext(
        next: Map<Partition, PartitionReadStatus>
    ): Map<Partition, PartitionReadStatus> {
        return (keys + next.keys)
            .mapNotNull { partition -> (this[partition] mergeWithNext next[partition])?.let { partition to it } }
            .toMap()
    }

    private infix fun PartitionReadStatus?.mergeWithNext(next: PartitionReadStatus?): PartitionReadStatus? {
        if (this == null || next == null) {
            return this ?: next
        }
        return PartitionReadStatus(
            startedAtOffset = min(startedAtOffset, next.startedAtOffset),
            endedAtOffset = max(endedAtOffset, next.endedAtOffset),
            startedAtTimestamp = startedAtTimestamp minNullable next.startedAtTimestamp,
            endedAtTimestamp = endedAtTimestamp maxNullable next.endedAtTimestamp,
            read = read + next.read,
            matching = matching + next.matching,
            reachedEnd = next.reachedEnd,
            remaining = next.remaining,
            beginOffset = next.beginOffset,
            endOffset = next.endOffset,
        )
    }

    fun resolvePartitionForKey(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        key: String,
        keySerializerType: KeySerializerType,
    ): Partition {
        clustersRepository.getCluster(clusterIdentifier).ref().checkClusterEnabled()
        val topic = clustersStateProvider.getLatestClusterStateValue(clusterIdentifier)
            .topics
            .find { it.name == topicName }
            ?: throw KafkistryIllegalStateException("No topic '$topicName' found on cluster '$clusterIdentifier'")
        return topicPartitionResolver.resolvePartition(topicName, key, keySerializerType, topic.partitionsAssignments.size)
    }

    private fun ClusterRef.checkClusterEnabled() {
        if (!clusterEnabledFilter.enabled(this)) {
            throw KafkistryConsumeException("Cluster '$this' is disabled")
        }
    }

}

infix fun Long?.minNullable(other: Long?): Long? {
    return if (this == null || other == null) {
        this ?: other
    } else {
        min(this, other)
    }
}
infix fun Long?.maxNullable(other: Long?): Long? {
    return if (this == null || other == null) {
        this ?: other
    } else {
        max(this, other)
    }
}
