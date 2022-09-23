package com.infobip.kafkistry.service.topic.offsets

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.PartitionOffsets

data class TopicOffsets(
        val empty: Boolean,
        val size: Long,
        val messagesRate: TopicMessagesRate?,
        val partitionsOffsets: Map<Partition, PartitionOffsets>,
        val partitionMessageRate: Map<Partition, PartitionRate>,
)

data class TopicMessagesRate(
    val last15Sec: Double?,
    val lastMin: Double?,
    val last5Min: Double?,
    val last15Min: Double?,
    val last30Min: Double?,
    val lastH: Double?,
    val last2H: Double?,
    val last6H: Double?,
    val last12H: Double?,
    val last24H: Double?,
) {
    fun longestRangeRate(): Double? {
        return listOfNotNull(last24H, last12H, last6H, last2H, lastH, last30Min, last15Min, last5Min, lastMin, last15Sec)
            .firstOrNull()
    }
}

data class PartitionRate(
    val upTo15MinRate: Double?,
    val upTo24HRate: Double?,
)