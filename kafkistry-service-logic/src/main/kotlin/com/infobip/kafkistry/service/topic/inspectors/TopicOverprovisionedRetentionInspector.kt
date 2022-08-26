package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.Partition
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

val OVERPROVISIONED_RETENTION = TopicInspectionResultType(
    name = "OVERPROVISIONED_RETENTION",
    level = StatusLevel.IGNORE,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has less traffic than expected so that messages are deleted by retention.ms way before partition growing close to configured retention.bytes",
)

@Configuration
@ConfigurationProperties("app.topic-inspection.overprovisioned-retention")
class TopicOverprovisionedRetentionInspectorProperties {
    var minUsedRatioToRetentionBytes = 0.2
    var requiredRatioToRetentionMs = 0.8
    var ignoreBelowRetentionBytes = 10L * 1024 * 1024
}

@Component
class TopicOverprovisionedRetentionInspector(
    oldestRecordAgeService: OldestRecordAgeService,
    private val properties: TopicOverprovisionedRetentionInspectorProperties,
) : AbstractTopicPartitionRetentionInspector<OverprovisionedPartitionStats>(oldestRecordAgeService) {

    override fun PartitionCtx.analyzePartition(ctx: TopicInspectCtx): OverprovisionedPartitionStats? {
        if (retentionBytes == -1L || retentionMs == -1L || oldestRecordAge == null || usedRetentionBytes == null) {
            return null     //nothing to analyze for infinite retention or when not having data
        }
        if (usedRetentionBytes > retentionBytes * properties.minUsedRatioToRetentionBytes) {
            return null     //having "enough" usage, not issue
        }
        if (oldestRecordAge < retentionMs * properties.requiredRatioToRetentionMs) {
            return null     //time retention not filled up, not issue
        }
        if (retentionBytes < properties.ignoreBelowRetentionBytes) {
            return null     //don't bother with small topics
        }
        return OverprovisionedPartitionStats(
            usageBytes = usedRetentionBytes,
            percentOfRetentionBytes = 100.0 * usedRetentionBytes / retentionBytes,
        )
    }

    override fun analyzePartitions(
        ctx: TopicInspectCtx,
        retentionBytes: Long,
        retentionMs: Long,
        partitionResults: Map<Partition, OverprovisionedPartitionStats>,
        outputCallback: TopicExternalInspectCallback
    ) {
        val numPartitions = ctx.existingTopic?.partitionsAssignments?.size ?: return
        if (partitionResults.size < numPartitions) {
            return
        }
        val bestStats = partitionResults.values
            .maxByOrNull { it.percentOfRetentionBytes }
            ?: return
        outputCallback.addDescribedStatusType(
            NamedTypeCauseDescription(
                type = OVERPROVISIONED_RETENTION,
                message = "Topic uses much less disk space than anticipated. " +
                        "Biggest partition uses %USED_BYTES% which is only " +
                        "%RETENTION_PERCENT% % of maximum retention.bytes of %RETENTION_BYTES%.",
                placeholders = mapOf(
                    "USED_BYTES" to Placeholder("used.bytes", bestStats.usageBytes),
                    "RETENTION_PERCENT" to Placeholder("retention.percent", bestStats.percentOfRetentionBytes),
                    "RETENTION_BYTES" to Placeholder("retention.bytes", retentionBytes),
                ),
            )
        )
    }

}

data class OverprovisionedPartitionStats(
    val usageBytes: Long,
    val percentOfRetentionBytes: Double,
)