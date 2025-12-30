package com.infobip.kafkistry.service.produce

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.service.produce.serialize.SerializerType
import org.springframework.http.HttpStatus

/**
 * Request to produce a single record to Kafka
 */
data class ProduceRequest(
    val key: ProduceValue?,              // null for null key
    val value: ProduceValue?,            // null for null value (tombstone)
    val headers: List<ProduceHeader>,    // empty list if no headers
    val partition: Partition?,           // null for automatic partitioning
    val timestamp: Long?,                // null for broker-assigned timestamp
)

/**
 * Value to be produced (key, value, or header value)
 */
data class ProduceValue(
    val content: String,                 // String representation of the value
    val serializerType: SerializerType,  // How to serialize this value
)

/**
 * Header to attach to produced record
 */
data class ProduceHeader(
    val key: String,
    val value: ProduceValue?,            // null for null header value
)

/**
 * Result of produce operation
 */
data class ProduceResult(
    val success: Boolean,
    val topic: String,
    val partition: Partition,
    val offset: Long,
    val timestamp: Long,
    val errorMessage: String? = null,
)

/**
 * Exception thrown during produce operations
 */
open class KafkistryProduceException(
    message: String,
    cause: Throwable? = null
) : KafkistryException(message, cause)


class KafkistryProduceDeniedException(
    message: String,
): KafkistryProduceException(message) {
    override val httpStatus: Int = HttpStatus.FORBIDDEN.value()
}
