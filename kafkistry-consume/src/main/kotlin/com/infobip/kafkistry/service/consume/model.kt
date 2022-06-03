package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.kafka.Partition

data class RecordHeader(
    val key: String,
    val value: KafkaValue,
)

data class ReadConfig(
    val numRecords: Int,
    val partitions: List<Int>?,
    val notPartitions: List<Int>?,
    val maxWaitMs: Long,
    val fromOffset: Offset,
    val partitionFromOffset: Map<Partition, Long>?,
    val waitStrategy: WaitStrategy,
    val recordDeserialization: RecordDeserialization,
    val readOnlyCommitted: Boolean,
    val readFilter: ReadFilter,
)

data class ReadFilter(
    val headerValueRule: ValueRule? = null,
    val jsonValueRule: ValueRule? = null,
    val keyValueRule: ValueRule? = null,
    val all: List<ReadFilter>? = null,
    val any: List<ReadFilter>? = null,
    val none: List<ReadFilter>? = null
) {
    companion object {
        val EMPTY = ReadFilter()
    }
}

data class RecordDeserialization(
    val keyType: DeserializerType?,
    val headersType: DeserializerType?,
    val valueType: DeserializerType?,
) {
    companion object {
        val ANY = RecordDeserialization(null, null, null)
    }
}

typealias JsonPathDef = String

data class ValueRule(
    val name: JsonPathDef,
    val type: FieldRuleType,
    val value: String
)

enum class FieldRuleType {
    EXIST, NOT_EXIST,
    IS_NULL, NOT_NULL,
    EQUAL_TO, NOT_EQUAL_TO,
    LESS_THAN, GREATER_THAN,
    CONTAINS, NOT_CONTAINS,
    REGEX, NOT_REGEX
}

data class Offset(
    val type: OffsetType,
    val offset: Long
)

enum class OffsetType {
    EARLIEST, LATEST, EXPLICIT, TIMESTAMP
}

enum class WaitStrategy {
    AT_LEAST_ONE,
    WAIT_NUM_RECORDS
}

data class KafkaValue(
    val isNull: Boolean = false,
    val isEmpty: Boolean = false,
    val rawBase64Bytes: String?,
    val deserializations: Map<DeserializerType, DeserializedValue>,
) {
    companion object {
        val NULL = KafkaValue(isNull = true, isEmpty = false, rawBase64Bytes = null, deserializations = emptyMap())
        val EMPTY = KafkaValue(isNull = false, isEmpty = true, rawBase64Bytes = "", deserializations = emptyMap())
    }
}

data class DeserializedValue(
    val typeTag: DeserializerType,
    val value: Any,
    val asFilterable: Any,
    val asJson: String,
)

data class KafkaRecord(
    val topic: String,
    val partition: Partition,
    val offset: Long,
    val timestamp: Long,
    val key: KafkaValue,
    val headers: List<RecordHeader>,
    val value: KafkaValue,
    val keySize: Int,
    val valueSize: Int,
    val headersSize: Int,
)

data class KafkaRecordsResult(
    val totalCount: Int,
    val timedOut: Boolean,
    val reachedEnd: Boolean,
    val partitions: Map<Partition, PartitionReadStatus>,
    val records: List<KafkaRecord>,
)

data class PartitionReadStatus(
    val startedAt: Long,
    val endedAt: Long,
    val read: Long,
    val matching: Int,
    val reachedEnd: Boolean,
)
