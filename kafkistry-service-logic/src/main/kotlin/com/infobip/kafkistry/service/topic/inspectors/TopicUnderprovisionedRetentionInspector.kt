package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.topic.*
import org.apache.kafka.common.config.TopicConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

val UNDERPROVISIONED = TopicInspectionResultType(
    name = "UNDERPROVISIONED",
    level = StatusLevel.WARNING,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has more traffic than expected so that configured retention.bytes deletes messages soner than reaching configured retention.ms",
)

@Component
@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
class TopicUnderprovisionedRetentionInspector(
    private val oldestRecordAgeService: OldestRecordAgeService,
    private val replicaDirsService: ReplicaDirsService,
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
        val oldestRecordAges = ctx.cache {
            oldestRecordAgeService.topicOldestRecordAges(clusterRef.identifier, topicName)
        } ?: return
        val replicaInfos = ctx.cache {
            replicaDirsService.topicReplicaInfos(clusterRef.identifier, topicName)
        } ?: return

        fun partitionUnderprovisionedPercent(
            partition: Partition,
            replicas: Map<BrokerId, TopicPartitionReplica>,
        ): Double? {
            val usedRetentionBytes = replicas.values.maxOfOrNull { it.sizeBytes } ?: return null
            val oldestRecordAge = oldestRecordAges[partition] ?: return null
            if (usedRetentionBytes < retentionBytes - 2 * segmentBytes) {
                return null
            }
            if (oldestRecordAge > retentionMs * 0.8) {
                return null
            }
            return 100.0 * oldestRecordAge / retentionMs
        }

        val lowPartitionTimeRetentions = replicaInfos.partitionBrokerReplicas
            .mapNotNull { (partition, replicas) ->
                partitionUnderprovisionedPercent(partition, replicas)?.let { partition to it }
            }
            .toMap()
        if (lowPartitionTimeRetentions.isNotEmpty()) {
            outputCallback.addStatusType(UNDERPROVISIONED)
            outputCallback.setExternalInfo(lowPartitionTimeRetentions)
        }
    }

}