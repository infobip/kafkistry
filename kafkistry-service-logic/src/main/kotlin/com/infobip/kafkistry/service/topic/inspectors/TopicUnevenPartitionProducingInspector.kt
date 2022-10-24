package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafka.Partition
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
    level = StatusLevel.IGNORE,
    category = IssueCategory.RUNTIME_ISSUE,
    doc = "Topic has uneven produce rate across different partitions",
)

@Configuration
@ConfigurationProperties("app.topic-inspection.uneven-partition-rate")
class TopicUnevenPartitionProducingInspectorProperties {
    var ignoreInternal = true
    var only15MinWindow = false
    var acceptableMaxMinRatio = 5.0
    var thresholdMinMsgRate = 50.0
}

data class UnevenPartitionRates(
    val lowRate: Map<Partition, Double>,
    val highRate: Map<Partition, Double>,
)

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
                    message = "Min rate partition %MIN_PARTITION% (rate: %MIN_RATE%) is too low comparing to " +
                            "max rate partition %MAX_PARTITION% (rate: %MAX_RATE%)",
                    placeholders = mapOf(
                        "MIN_PARTITION" to Placeholder("partition", minRate.key),
                        "MIN_RATE" to Placeholder("min.msg.rate", minRate.value),
                        "MAX_PARTITION" to Placeholder("partition", maxRate.key),
                        "MAX_RATE" to Placeholder("max.msg.rate", maxRate.value),
                    ),
                )
            )
            val lowRates = mutableMapOf<Partition, Double>()
            val highRates = mutableMapOf<Partition, Double>()
            rates.forEach { (partition, rate) ->
                val lowRate = rate < maxRate.value / properties.acceptableMaxMinRatio
                val highRate = rate > minRate.value * properties.acceptableMaxMinRatio
                if (lowRate && !highRate) lowRates[partition] = rate
                if (highRate && !lowRate) highRates[partition] = rate
            }
            outputCallback.setExternalInfo(UnevenPartitionRates(lowRates, highRates))
        }
    }

    fun PartitionRate.rate() = if (properties.only15MinWindow) {
        upTo15MinRate
    } else {
        upTo24HRate ?: upTo15MinRate
    }
}
