package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.*
import java.time.Duration
import java.util.concurrent.ConcurrentMap

data class TimestampWrapper<T>(
    val field: T,
    val timestamp: Long = 0L,
) {
    fun withField(fieldSupplier: (T) -> T): TimestampWrapper<T> = copy(field = fieldSupplier(field))
}

data class TimestampWrappedRecordsStructure(
    val payloadType: PayloadType,
    val timestampWrappedHeaderFields: List<TimestampWrapper<TimestampWrappedRecordField>>?,
    val timestampWrappedJsonFields: List<TimestampWrapper<TimestampWrappedRecordField>>?,
    val nullable: TimestampWrapper<Boolean>,
    val size: RecordTimedSize,
)

data class RecordTimedSize(
    val keySize: TimedHistory<IntNumberSummary>,
    val valueSize: TimedHistory<IntNumberSummary>,
    val headersSize: TimedHistory<IntNumberSummary>,
)

data class TimestampWrappedFieldValue(
    val highCardinality: TimestampWrapper<Boolean>,
    val tooBig: TimestampWrapper<Boolean>,
    val values: List<TimestampWrapper<Any>> = emptyList(),
)

data class TimestampWrappedRecordField(
    val name: String?,
    val type: RecordFieldType,
    val nullable: TimestampWrapper<Boolean>,
    val children: List<TimestampWrapper<TimestampWrappedRecordField>>? = null,
    val value: TimestampWrappedFieldValue? = null,
)

fun TimestampWrappedRecordsStructure.toRecordsStructure(
    now: Long = generateTimestamp()
): RecordsStructure = RecordsStructure(
    payloadType = payloadType,
    headerFields = timestampWrappedHeaderFields?.map { it.field.toRecordField() },
    jsonFields = timestampWrappedJsonFields?.map { it.field.toRecordField() },
    nullable = nullable.field,
    size = RecordSize(
        key = size.keySize.toSizeOverTime(now),
        value = size.valueSize.toSizeOverTime(now),
        headers = size.headersSize.toSizeOverTime(now),
    )
)

fun TimestampWrappedRecordField.toRecordField(
    parentType: RecordFieldType = RecordFieldType.NULL,
    parentFullName: String? = null,
): RecordField {
    val fullName = fullName(name, parentFullName, parentType)
    return RecordField(
        name, fullName, type,
        children = children?.map { it.field.toRecordField(type, fullName) },
        nullable = nullable.field,
        value = value?.let { value ->
            RecordFieldValue(
                highCardinality = value.highCardinality.field,
                tooBig = value.tooBig.field,
                valueSet = value.values.takeIf { it.isNotEmpty() }?.asSequence()?.map { it.field }?.toSet(),
            )
        }
    )
}

fun TimedHistory<IntNumberSummary>.toSizeOverTime(now: Long) = SizeOverTime(
    last15Min = last15Min.toSizeStatistic(Duration.ZERO, now),
    lastHour = lastHour.toSizeStatistic(TimeConstant.offset15Min, now),
    last6Hours = last6Hours.toSizeStatistic(TimeConstant.offset1h, now),
    lastDay = lastDay.toSizeStatistic(TimeConstant.offset6h, now),
    lastWeek = lastWeek.toSizeStatistic(TimeConstant.offset1d, now),
    lastMonth = lastMonth.toSizeStatistic(TimeConstant.offset1w, now),
)

fun TimestampWrapper<IntNumberSummary>.toSizeStatistic(minOldness: Duration, now: Long): SizeStatistic? {
    if (timestamp > now - minOldness.toMillis()) {
        return null
    }
    return SizeStatistic(
        count = field.count, avg = field.avg, min = field.min, max = field.max
    )
}

fun fullName(
    name: String?,
    parentFullName: String?,
    parentType: RecordFieldType,
): String? = when (parentFullName) {
    null -> when (parentType) {
        RecordFieldType.ARRAY -> "[*]"
        RecordFieldType.OBJECT -> name ?: "*"
        else -> name
    }
    else -> {
        when (parentType) {
            RecordFieldType.OBJECT -> "$parentFullName." + (name ?: "*")
            RecordFieldType.ARRAY -> "$parentFullName[*]" + (name ?: "")
            else -> (name ?: "*")
        }
    }
}

fun generateTimestamp(): Long = System.currentTimeMillis()

typealias RecordsStructuresMap = ConcurrentMap<TopicName, TimestampWrapper<TimestampWrappedRecordsStructure>>
typealias ClusterRecordsStructuresMap = ConcurrentMap<KafkaClusterIdentifier, RecordsStructuresMap>
