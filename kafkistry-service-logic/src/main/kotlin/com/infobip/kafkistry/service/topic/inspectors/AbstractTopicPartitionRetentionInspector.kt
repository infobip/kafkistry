package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.topic.*
import org.apache.kafka.common.config.TopicConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
abstract class AbstractTopicPartitionRetentionInspector<T>(
    private val oldestRecordAgeService: OldestRecordAgeService,
) : TopicExternalInspector {

    data class PartitionCtx(
        val partition: Partition,
        val retentionBytes: Long,
        val segmentBytes: Long,
        val retentionMs: Long,
        val usedRetentionBytes: Long?,
        val oldestRecordAge: Long?,
    )

    abstract fun PartitionCtx.analyzePartition(ctx: TopicInspectCtx): T?

    abstract fun analyzePartitions(
        ctx: TopicInspectCtx,
        retentionBytes: Long,
        retentionMs: Long,
        partitionResults: Map<Partition, T>,
        outputCallback: TopicExternalInspectCallback,
    )

    override fun inspectTopic(
        ctx: TopicInspectCtx,
        outputCallback: TopicExternalInspectCallback
    ) {
        val topicConfig = ctx.existingTopic?.config ?: return
        val retentionBytes = topicConfig[TopicConfig.RETENTION_BYTES_CONFIG]?.value?.toLongOrNull() ?: return
        val segmentBytes = topicConfig[TopicConfig.SEGMENT_BYTES_CONFIG]?.value?.toLongOrNull() ?: return
        val retentionMs = topicConfig[TopicConfig.RETENTION_MS_CONFIG]?.value?.toLongOrNull() ?: return
        val replicaInfos = ctx.currentTopicReplicaInfos ?: return
        val oldestRecordAges = ctx.cache {
            oldestRecordAgeService.topicOldestRecordAges(clusterRef.identifier, topicName)
        } ?: return

        val partitionResults = replicaInfos.partitionBrokerReplicas
            .mapNotNull { (partition, replicas) ->
                PartitionCtx(
                    partition, retentionBytes, segmentBytes, retentionMs,
                    usedRetentionBytes = replicas.values.maxOfOrNull { it.sizeBytes },
                    oldestRecordAge = oldestRecordAges[partition]
                ).analyzePartition(ctx)?.let { partition to it }
            }
            .toMap()
        analyzePartitions(ctx, retentionBytes, retentionMs, partitionResults, outputCallback)
    }

}
