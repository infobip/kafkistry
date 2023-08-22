package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.model.*
import kotlin.math.absoluteValue
import kotlin.math.min

open class MergingContext(
    properties: RecordAnalyzerProperties,
    now: Long = generateTimestamp(),
) : ProcessingContext(properties, now) {

    fun timestampWrappedUnknownRecordsStructure(
        headersFields: List<TimestampWrapper<TimestampWrappedRecordField>>?,
        nullable: Boolean,
        size: RecordTimedSize,
    ) = TimestampWrappedRecordsStructure(
        PayloadType.UNKNOWN,
        timestampWrappedHeaderFields = headersFields,
        timestampWrappedJsonFields = null,
        nullable = wrapNow(nullable),
        size = size,
    )

    /**
     * Simply add the newer fields to the existent ones
     * from: @TimestampWrappedRecordsStructure
     * to: @TimestampWrappedRecordsStructure
     */
    infix fun TimestampWrappedRecordsStructure.merge(
        other: TimestampWrappedRecordsStructure
    ): TimestampWrappedRecordsStructure {
        if (payloadType != PayloadType.JSON && other.payloadType != PayloadType.JSON) {
            return timestampWrappedUnknownRecordsStructure(
                headersFields = timestampWrappedHeaderFields merge other.timestampWrappedHeaderFields,
                nullable = (nullable mergeBoolean other.nullable).field,
                size = size merge other.size,
            )
        }
        return TimestampWrappedRecordsStructure(
            PayloadType.JSON,
            timestampWrappedHeaderFields = timestampWrappedHeaderFields merge other.timestampWrappedHeaderFields,
            timestampWrappedJsonFields = timestampWrappedJsonFields merge other.timestampWrappedJsonFields,
            nullable = nullable mergeBoolean other.nullable,
            size = size merge other.size,
        )
    }

    infix fun RecordTimedSize.merge(other: RecordTimedSize): RecordTimedSize {
        return RecordTimedSize(
            keySize = keySize merge other.keySize,
            valueSize = valueSize merge other.valueSize,
            headersSize = headersSize merge other.headersSize,
        )
    }

    infix fun TimedHistory<IntNumberSummary>.merge(
        other: TimedHistory<IntNumberSummary>
    ): TimedHistory<IntNumberSummary> {
        return this.merge(other) { first, second ->
            TimestampWrapper(
                field = first.field merge second.field,
                timestamp = min(first.timestamp, second.timestamp),
            )
        }
    }

    infix fun List<TimestampWrapper<TimestampWrappedRecordField>>?.merge(
        other: List<TimestampWrapper<TimestampWrappedRecordField>>?
    ): List<TimestampWrapper<TimestampWrappedRecordField>>? {
        if (this == null || other == null || this == other) {
            return other ?: this
        }

        val thisIsTooOldDynamicField = size == 1 && first().let {
            it.field.name == null && it.timestamp.tooOld()
        }
        if (thisIsTooOldDynamicField) {
            return other
        }
        return this doMerge other
    }

    private infix fun List<TimestampWrapper<TimestampWrappedRecordField>>.doMerge(
        other: List<TimestampWrapper<TimestampWrappedRecordField>>
    ): List<TimestampWrapper<TimestampWrappedRecordField>> {
        val thisNames = this.map { it.field.name }.toSet()
        val otherNames = other.map { it.field.name }.toSet()

        if (isNotEmpty() && other.isNotEmpty()) {
            maybeCollapseAsDynamicFieldNames(other, thisNames, otherNames)?.run { return this }
        }

        val thisNulls = filter { it.field.type == RecordFieldType.NULL }.associateBy { it.field.name }
        val otherNulls = other.filter { it.field.type == RecordFieldType.NULL }.associateBy { it.field.name }
        val thisFields = associateBy { it.field.name to it.field.type }
        val otherFields = other.associateBy { it.field.name to it.field.type }

        return (thisFields.keys + otherFields.keys).distinct().mapNotNull { key ->
            val fieldName = key.first
            val thisField = thisFields[key]
            val otherField = otherFields[key]
            val thisNull = thisNulls[fieldName]
            val otherNull = otherNulls[fieldName]
            when {
                //don't output NULL type if there is matching name of non-NULL type
                thisField.isNullType() && (fieldName in otherNames) && !otherField.isNullType() -> null
                otherField.isNullType() && (fieldName in thisNames) && !thisField.isNullType() -> null
                //do actual merge
                thisField != null && otherField != null -> thisField merge otherField
                //mark as nullable
                thisField != null -> if (otherNull != null || fieldName !in otherNames) thisField.asNullable() else thisField
                otherField != null -> if (thisNull != null || fieldName !in thisNames) otherField.asNullable() else otherField
                else -> null
            }
        }
    }

    private fun List<TimestampWrapper<TimestampWrappedRecordField>>.maybeCollapseAsDynamicFieldNames(
        other: List<TimestampWrapper<TimestampWrappedRecordField>>,
        thisNames: Set<String?>,
        otherNames: Set<String?>,
    ): List<TimestampWrapper<TimestampWrappedRecordField>>? {
        infix fun Int.differentMagnitude(other: Int): Boolean {
            val thresholdDiff = properties.cardinalityDiffThreshold
            val thresholdFactor = properties.cardinalityMagnitudeFactorThreshold
            return (this - other).absoluteValue > thresholdDiff && (this > thresholdFactor * other || thresholdFactor * this < other)
        }

        fun Collection<String?>.hasDynamicFieldNames() = any { it?.isDynamicName() ?: true }
        fun commonNames() = thisNames.intersect(otherNames)
        fun namesCountDiffTooMuch() = thisNames.size.differentMagnitude(otherNames.size)
        fun isDynamic() = thisNames.hasDynamicFieldNames() || otherNames.hasDynamicFieldNames() ||
                namesCountDiffTooMuch() || commonNames().isEmpty()

        return when (isDynamic()) {
            true -> (this + other)
                .groupBy { it.field.type }
                .map { (_, values) ->
                    values.squashAsVariable()
                }
            false -> null
        }
    }

    private fun TimestampWrapper<TimestampWrappedRecordField>?.isNullType() = this?.field?.type == RecordFieldType.NULL

    private fun String.isDynamicName(): Boolean {
        if (isBlank()) return true
        if (!get(0).isJavaIdentifierStart()) return true
        return !all { it == '-' || it.isJavaIdentifierPart() }
    }

    private fun List<TimestampWrapper<TimestampWrappedRecordField>>.squashAsVariable(): TimestampWrapper<TimestampWrappedRecordField> {
        return first().withField { field ->
            field.copy(
                name = null,
                value = asSequence().map { it.field.value }.reduce { acc, wrappers ->
                    acc merge wrappers
                },
                children = asSequence().map { it.field.children }.reduce { acc, wrappers ->
                    acc merge wrappers
                }
            )
        }
    }

    private fun TimestampWrapper<TimestampWrappedRecordField>.asNullable() =
        withField { it.copy(nullable = wrapNow(true)) }

    infix fun TimestampWrapper<TimestampWrappedRecordField>.merge(
        other: TimestampWrapper<TimestampWrappedRecordField>
    ): TimestampWrapper<TimestampWrappedRecordField> {
        if (field == other.field) {
            return other
        }
        return wrapNow(
            TimestampWrappedRecordField(
                nullable = field.nullable mergeBoolean other.field.nullable,
                name = other.field.name,
                type = when (other.field.type) {
                    RecordFieldType.NULL -> field.type
                    else -> other.field.type
                },
                children = when {
                    field.children != other.field.children -> field.children merge other.field.children
                    else -> other.field.children
                },
                value = field.value merge other.field.value,
            )
        )
    }

    private infix fun TimestampWrapper<Boolean>.mergeBoolean(
        other: TimestampWrapper<Boolean>
    ): TimestampWrapper<Boolean> = when {
        field -> when (!other.field && timestamp.tooOld()) {
            true -> wrapNow(false)
            false -> this
        }
        else -> wrapNow(field || other.field)
    }

    infix fun TimestampWrappedFieldValue?.merge(
        other: TimestampWrappedFieldValue?
    ): TimestampWrappedFieldValue? {
        if (this == null || other == null || this == other) {
            return other ?: this
        }
        val mergedHighCardinality = highCardinality mergeBoolean other.highCardinality
        fun <T> List<T>.takeIfNotHighCardinality(): List<T> = when {
            isHighCardinality() -> emptyList()
            mergedHighCardinality.field -> emptyList()
            else -> this
        }

        val mergedValues = when (mergedHighCardinality.field) {
            true -> emptyList()
            false -> values mergeValueSet other.values
        }
        return TimestampWrappedFieldValue(
            highCardinality = mergedHighCardinality mergeBoolean wrapNow(mergedValues.isHighCardinality()),
            tooBig = tooBig mergeBoolean other.tooBig,
            values = mergedValues.takeIfNotHighCardinality(),
        )
    }

    private fun List<*>.isHighCardinality() = size > properties.valueSampling.maxCardinality

    private infix fun <T> List<TimestampWrapper<T>>.mergeValueSet(
        other: List<TimestampWrapper<T>>
    ): List<TimestampWrapper<T>> {
        return sequenceOf(this, other)
            .flatten()
            .filter { !it.timestamp.tooOld() }
            .groupingBy { it.field }
            .reduce { _, accumulator, element ->
                if (accumulator.timestamp > element.timestamp) accumulator else element
            }
            .values.toList()
    }


}

