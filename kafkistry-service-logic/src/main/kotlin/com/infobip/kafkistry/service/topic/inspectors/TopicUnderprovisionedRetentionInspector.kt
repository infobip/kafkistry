package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.service.NamedType
import com.infobip.kafkistry.service.NamedTypeCauseDescription
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.topic.IssueCategory
import com.infobip.kafkistry.service.topic.TopicExternalInspectCallback
import com.infobip.kafkistry.service.topic.TopicInspectCtx
import com.infobip.kafkistry.service.topic.TopicInspectionResultType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

val UNDERPROVISIONED_RETENTION = TopicInspectionResultType(
    name = "UNDERPROVISIONED_RETENTION",
    level = StatusLevel.WARNING,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has more traffic than expected so that configured retention.bytes deletes messages soner than reaching configured retention.ms",
)

val DRASTICALLY_UNDERPROVISIONED_RETENTION = TopicInspectionResultType(
    name = "DRASTICALLY_UNDERPROVISIONED_RETENTION",
    level = StatusLevel.ERROR,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has way more traffic than expected so that configured retention.bytes deletes messages much soner than reaching configured retention.ms",
)

@Configuration
@ConfigurationProperties("app.topic-inspection.underprovisioned-retention")
class TopicUnderprovisionedRetentionInspectorProperties {
    var minOldnessRatioToRetentionMs = 0.8
    var requiredRatioToRetentionBytes = 0.8
    var drasticOldnessRatioToRetentionMs = 0.1
}

@Component
class TopicUnderprovisionedRetentionInspector(
    oldestRecordAgeService: OldestRecordAgeService,
    private val properties: TopicUnderprovisionedRetentionInspectorProperties,
) : AbstractTopicPartitionRetentionInspector<UnderprovisionedPartitionStats>(oldestRecordAgeService) {

    override fun PartitionCtx.analyzePartition(ctx: TopicInspectCtx): UnderprovisionedPartitionStats? {
        if (retentionBytes == -1L || retentionMs == -1L || oldestRecordAge == null || usedRetentionBytes == null) {
            return null     //nothing to analyze for infinite retention or when not having data
        }
        if (usedRetentionBytes < retentionBytes - 2 * segmentBytes ||
            usedRetentionBytes < retentionBytes * properties.requiredRatioToRetentionBytes) {
            //size retention not filled up
            return null
        }
        if (oldestRecordAge > retentionMs * properties.minOldnessRatioToRetentionMs) {
            //effective time retention big enough
            return null
        }
        return UnderprovisionedPartitionStats(
            oldestRecordAgeMs = oldestRecordAge,
            percentOfRetentionMs = 100.0 * oldestRecordAge / retentionMs,
        )
    }

    override fun analyzePartitions(
        ctx: TopicInspectCtx,
        retentionBytes: Long,
        retentionMs: Long,
        partitionResults: Map<Partition, UnderprovisionedPartitionStats>,
        outputCallback: TopicExternalInspectCallback
    ) {
        val worstStats = partitionResults.values
            .minByOrNull { it.percentOfRetentionMs }
            ?: return   //not a single partition is under-provisioned, nothing more to do

        val drasticPartitionStats = partitionResults.filter {
            it.value.percentOfRetentionMs < 100 * properties.drasticOldnessRatioToRetentionMs
        }
        val drasticStats = drasticPartitionStats.values
            .minByOrNull { it.percentOfRetentionMs }
        if (drasticStats != null) {
            outputCallback.addDescribedStatusType(
                newUnderProvisionedDescription(
                    DRASTICALLY_UNDERPROVISIONED_RETENTION, drasticStats, retentionMs, drasticPartitionStats.keys.sorted()
                )
            )
        } else {
            outputCallback.addDescribedStatusType(
                newUnderProvisionedDescription(
                    UNDERPROVISIONED_RETENTION, worstStats, retentionMs, partitionResults.keys.sorted()
                )
            )
        }
        outputCallback.setExternalInfo(partitionResults)
    }

    private fun <T : NamedType> newUnderProvisionedDescription(
        type: T, worstStats: UnderprovisionedPartitionStats, retentionMs: Long, lowPartitions: List<Partition>,
    ): NamedTypeCauseDescription<T> {
        return NamedTypeCauseDescription(
            type = type,
            message = "Topic has partition with oldest record of age %OLDEST_AGE% which is only " +
                "%RETENTION_PERCENT% of expected time retention.ms of %RETENTION_MS%. " +
                "Low retention partitions are %PARTITIONS_LIST%",
            placeholders = mapOf(
                "OLDEST_AGE" to Placeholder("age.ms", worstStats.oldestRecordAgeMs),
                "RETENTION_PERCENT" to Placeholder("retention.percent", worstStats.percentOfRetentionMs),
                "RETENTION_MS" to Placeholder("retention.ms", retentionMs),
                "PARTITIONS_LIST" to Placeholder("partitions", lowPartitions),
            ),
        )
    }

}

data class UnderprovisionedPartitionStats(
    val oldestRecordAgeMs: Long,
    val percentOfRetentionMs: Double,
)