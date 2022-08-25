package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.service.NamedTypeCauseDescription
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.topic.*
import org.apache.kafka.common.config.TopicConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

val UNDERPROVISIONED_RETENTION = TopicInspectionResultType(
    name = "UNDERPROVISIONED_RETENTION",
    level = StatusLevel.WARNING,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has more traffic than expected so that configured retention.bytes deletes messages soner than reaching configured retention.ms",
)

@Component
@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
class TopicUnderprovisionedRetentionInspector(
    private val oldestRecordAgeService: OldestRecordAgeService,
) : TopicExternalInspector {

    override fun inspectTopic(
        ctx: TopicInspectCtx,
        outputCallback: TopicExternalInspectCallback
    ) {
        val topicConfig = ctx.existingTopic?.config ?: return
        val retentionBytes = topicConfig[TopicConfig.RETENTION_BYTES_CONFIG]?.value?.toLongOrNull() ?: return
        val segmentBytes = topicConfig[TopicConfig.SEGMENT_BYTES_CONFIG]?.value?.toLongOrNull() ?: return
        val retentionMs = topicConfig[TopicConfig.RETENTION_MS_CONFIG]?.value?.toLongOrNull() ?: return
        if (retentionBytes == -1L || retentionMs == -1L) {
            return
        }
        val replicaInfos = ctx.currentTopicReplicaInfos ?: return
        val oldestRecordAges = ctx.cache {
            oldestRecordAgeService.topicOldestRecordAges(clusterRef.identifier, topicName)
        } ?: return

        fun partitionUnderprovisionedPercent(
            partition: Partition,
            replicas: Map<BrokerId, TopicPartitionReplica>,
        ): UnderprovisionedPartitionStats? {
            val usedRetentionBytes = replicas.values.maxOfOrNull { it.sizeBytes } ?: return null
            val oldestRecordAge = oldestRecordAges[partition] ?: return null
            if (usedRetentionBytes < retentionBytes - 2 * segmentBytes || usedRetentionBytes < retentionBytes * 0.8) {
                //size retention not filled up
                return null
            }
            if (oldestRecordAge > retentionMs * 0.8) {
                //effective time retention big enough
                return null
            }
            return UnderprovisionedPartitionStats(
                oldestRecordAgeMs = oldestRecordAge,
                percentOfRetentionMs = 100.0 * oldestRecordAge / retentionMs,
            )
        }

        val lowPartitionTimeRetentions = replicaInfos.partitionBrokerReplicas
            .mapNotNull { (partition, replicas) ->
                partitionUnderprovisionedPercent(partition, replicas)?.let { partition to it }
            }
            .toMap()
        val worstStats = lowPartitionTimeRetentions.values
            .minByOrNull { it.percentOfRetentionMs }
            ?: return
        val lowPartitions: List<Partition> = lowPartitionTimeRetentions.keys.sorted()
        outputCallback.addDescribedStatusType(
            NamedTypeCauseDescription(
                type = UNDERPROVISIONED_RETENTION,
                message = "Topic has partition with oldest record of age %OLDEST_AGE% which is only " +
                        "%RETENTION_PERCENT% % of expected time retention.ms of %RETENTION_MS%. " +
                        "Low retention partitions are %PARTITIONS_LIST%",
                placeholders = mapOf(
                    "OLDEST_AGE" to Placeholder("age.ms", worstStats.oldestRecordAgeMs),
                    "RETENTION_PERCENT" to Placeholder("retention.percent", worstStats.percentOfRetentionMs),
                    "RETENTION_MS" to Placeholder("retention.ms", retentionMs),
                    "PARTITIONS_LIST" to Placeholder("partitions", lowPartitions),
                ),
            )
        )
        outputCallback.setExternalInfo(lowPartitionTimeRetentions)
    }

}

data class UnderprovisionedPartitionStats(
    val oldestRecordAgeMs: Long,
    val percentOfRetentionMs: Double,
)