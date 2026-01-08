package com.infobip.kafkistry.kafkastate.coordination

import com.infobip.kafkistry.kafka.SamplingPosition
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import java.io.Serializable

/**
 * Event signaling that sampling has started for a cluster.
 * Other instances should prepare samplers when receiving this event.
 */
data class SamplingStartedEvent(
    val clusterIdentifier: KafkaClusterIdentifier,
    val samplingPosition: SamplingPosition,
    val topics: Set<TopicName>,
) : Serializable

/**
 * Event signaling that sampling has completed (successfully or failed) for a cluster.
 * Other instances should finalize/cleanup samplers when receiving this event.
 */
data class SamplingCompletedEvent(
    val clusterIdentifier: KafkaClusterIdentifier,
    val samplingPosition: SamplingPosition,
    val success: Boolean,
    val cause: String? = null
) : Serializable

/**
 * Event representing a sampled Kafka record.
 * Used for sharing sampled records across Kafkistry instances via Hazelcast.
 */
data class SampledConsumerRecord(
    val clusterIdentifier: KafkaClusterIdentifier,
    val samplingPosition: SamplingPosition,
    val topic: TopicName,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val timestampType: TimestampType,
    val key: Bytes?,
    val value: Bytes?,
    val headers: List<SampledRecordHeader>,
) : Serializable {

    data class Bytes(val array: ByteArray): Serializable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Bytes
            return array.contentEquals(other.array)
        }

        override fun hashCode(): Int = array.contentHashCode()
    }

    data class SampledRecordHeader(
        val key: String,
        val value: Bytes?,
    ): Serializable

    companion object {

        fun from(
            clusterIdentifier: KafkaClusterIdentifier,
            samplingPosition: SamplingPosition,
            record: ConsumerRecord<ByteArray?, ByteArray?>
        ): SampledConsumerRecord = SampledConsumerRecord(
            clusterIdentifier = clusterIdentifier,
            samplingPosition = samplingPosition,
            topic = record.topic(),
            partition = record.partition(),
            offset = record.offset(),
            timestamp = record.timestamp(),
            timestampType = record.timestampType(),
            key = record.key()?.toBytes(),
            value = record.value()?.toBytes(),
            headers = record.headers().map { SampledRecordHeader(it.key(), it.value()?.toBytes()) },
        )

        private fun ByteArray.toBytes(): Bytes = Bytes(this)
    }

    /**
     * Convert back to a ConsumerRecord for local listeners.
     */
    fun toConsumerRecord(): ConsumerRecord<ByteArray?, ByteArray?> {
        val kafkaHeaders = RecordHeaders().apply {
            headers.forEach { (key, value) -> add(key, value?.array) }
        }
        return ConsumerRecord(
            topic,
            partition,
            offset,
            timestamp,
            timestampType,
            key?.array?.size ?: 0,
            value?.array?.size ?: 0,
            key?.array,
            value?.array,
            kafkaHeaders,
            java.util.Optional.empty(),
        )
    }
}

