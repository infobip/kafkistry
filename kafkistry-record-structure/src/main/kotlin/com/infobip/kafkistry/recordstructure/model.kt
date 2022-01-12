package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.*
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

fun TimestampWrappedRecordsStructure.toRecordsStructure(): RecordsStructure = RecordsStructure(
    payloadType = payloadType,
    headerFields = timestampWrappedHeaderFields?.map { it.field.toRecordField() },
    jsonFields = timestampWrappedJsonFields?.map { it.field.toRecordField() },
    nullable = nullable.field,
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
