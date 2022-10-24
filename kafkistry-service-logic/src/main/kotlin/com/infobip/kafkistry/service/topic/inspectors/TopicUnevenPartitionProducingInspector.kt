package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.service.NamedTypeCauseDescription
import com.infobip.kafkistry.service.Placeholder
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.offsets.PartitionRate
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

val UNEVEN_TRAFFIC_RATE = TopicInspectionResultType(
    name = "UNEVEN_TRAFFIC_RATE",
    level = StatusLevel.WARNING,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has uneven produce rate across different partitions",
)

@Configuration
@ConfigurationProperties("app.topic-inspection.uneven-partition-rate")
class TopicUnevenPartitionProducingInspectorProperties {
    var ignoreInternal = true
    var acceptableMaxMinRatio = 4.0
    var thresholdMinMsgRate = 10.0
}

@Component
class TopicUnevenPartitionProducingInspector(
    private val properties: TopicUnevenPartitionProducingInspectorProperties,
    private val topicOffsetsService: TopicOffsetsService,
) : TopicExternalInspector {

    override fun inspectTopic(ctx: TopicInspectCtx, outputCallback: TopicExternalInspectCallback) {
        if (properties.ignoreInternal && ctx.existingTopic?.internal == true) {
            return
        }
        val topicOffsets = ctx.cache { topicOffsetsService.topicOffsets(clusterRef.identifier, topicName) }
            ?.takeIf { !it.empty }
            ?: return
        val rates = topicOffsets.partitionMessageRate
            .mapNotNull { (partition, rates) -> rates.rate()?.let { partition to it } }
            .toMap()
        if (rates.size < 2) {
            return
        }
        val maxRate = rates.maxByOrNull { it.value } ?: return
        val minRate = rates.minByOrNull { it.value } ?: return
        val violated = with(properties) {
            minRate.value * acceptableMaxMinRatio < maxRate.value && maxRate.value >= thresholdMinMsgRate
        }
        if (violated) {
            outputCallback.addDescribedStatusType(
                NamedTypeCauseDescription(
                    type = UNEVEN_TRAFFIC_RATE,
                    message = "Min rate partition %MIN_PARTITION% (%MIN_RATE% msg/sec) too low comparing to " +
                            "max rate partition %MAX_PARTITION% (%MAX_RATE% msg/sec)",
                    placeholders = mapOf(
                        "MIN_PARTITION" to Placeholder("partition", minRate.key),
                        "MIN_RATE" to Placeholder("min.msg.rate", minRate.value),
                        "MAX_PARTITION" to Placeholder("partition", maxRate.key),
                        "MAX_RATE" to Placeholder("max.msg.rate", maxRate.value),
                    ),
                )
            )
        }
    }
}

fun PartitionRate.rate() = upTo24HRate ?: upTo15MinRate