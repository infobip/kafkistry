package com.infobip.kafkistry.recordstructure

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Utf8
import com.infobip.kafkistry.model.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import java.io.CharConversionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

open class AnalyzeContext(
    properties: RecordAnalyzerProperties,
    private val analyzeFilter: AnalyzeFilter,
    private val objectMapper: ObjectMapper,
    now: Long = generateTimestamp(),
    private val topic: TopicName,
    private val cluster: ClusterRef,
) : MergingContext(properties, now) {

    private val shouldSampleValues = analyzeFilter.shouldSampleValues(cluster, topic)

    fun analyzeRecord(
        consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>,
        clusterRecordsStructures: ClusterRecordsStructuresMap,
    ) {
        val recordsStructure = analyzeRecordStructure(
            consumerRecord.headers(), consumerRecord.value()
        )
        val recordsStructures = clusterRecordsStructures.computeIfAbsent(cluster.identifier) { ConcurrentHashMap() }
        recordsStructures.merge(consumerRecord.topic(), wrapNow(recordsStructure)) { old, new ->
            wrapNow(old.field merge new.field)
        }
    }

    private fun analyzeRecordStructure(
        headers: Headers, recordPayload: ByteArray?
    ): TimestampWrappedRecordsStructure {
        val headersMap = headers.associate { it.key() to it.value() }
        val headersValue = analyzeValue(headersMap, null)
        if (recordPayload == null) {
            return TimestampWrappedRecordsStructure(
                PayloadType.NULL,
                timestampWrappedHeaderFields = listOf(headersValue),
                timestampWrappedJsonFields = null,
                nullable = wrapNow(true),
            )
        }
        return try {
            val input = objectMapper.readValue(recordPayload, Any::class.java)
            val value = analyzeValue(input, null)
            TimestampWrappedRecordsStructure(
                PayloadType.JSON,
                timestampWrappedHeaderFields = listOf(headersValue),
                timestampWrappedJsonFields = listOf(value),
                nullable = wrapNow(false),
            )
        } catch (e: Exception) {
            when (e) {
                is JsonParseException, is JsonProcessingException, is CharConversionException -> {
                    timestampWrappedUnknownRecordsStructure(listOf(headersValue), false)
                }
                else -> throw e
            }
        }
    }

    private fun analyzeValue(
        input: Any?,
        name: String?,
        parentPath: List<Pair<RecordFieldType, String?>> = emptyList()
    ): TimestampWrapper<TimestampWrappedRecordField> {
        return when (input) {
            null -> {
                TimestampWrappedRecordField(
                    name = name,
                    type = RecordFieldType.NULL,
                    nullable = wrapNow(true)
                )
            }
            is Map<*, *> -> {
                val objectFields = input.map { (key, value) ->
                    analyzeValue(value, key.toString(), parentPath + (RecordFieldType.OBJECT to name))
                }
                TimestampWrappedRecordField(
                    name = name,
                    type = RecordFieldType.OBJECT,
                    nullable = wrapNow(false),
                    children = objectFields
                )
            }
            is List<*> -> {
                val listElementStruct = input
                    .map { analyzeValue(it, null, parentPath + (RecordFieldType.ARRAY to name)) }
                    .takeIf { it.isNotEmpty() }
                    ?.groupBy { it.field.type }
                    ?.mapValues { (_, valuesOfType) ->
                        valuesOfType.reduce { acc, timestampWrapper ->
                            acc merge timestampWrapper
                        }
                    }
                    ?.values?.toList().orEmpty()
                TimestampWrappedRecordField(
                    name = name,
                    type = RecordFieldType.ARRAY,
                    nullable = wrapNow(false),
                    children = listElementStruct,
                )
            }
            else -> {
                val aInput = if (input is ByteArray) {
                    @Suppress("UnstableApiUsage")
                    val stringInput = input.takeIf { Utf8.isWellFormed(input) }?.decodeToString()
                    stringInput?.toLongOrNull()?.let {
                        when (it in (Int.MIN_VALUE..Int.MAX_VALUE)) {
                            true -> it.toInt()
                            false -> it
                        }
                    } ?: stringInput ?: input
                } else {
                    input
                }
                val type = TypeMappings.forClass(aInput.javaClass)
                TimestampWrappedRecordField(
                    name = name,
                    type = type,
                    nullable = wrapNow(false),
                    value = when (shouldSampleValues(parentPath + (type to name))) {
                        true -> sampleValue(aInput)
                        false -> null
                    }
                )
            }
        }.let { wrapNow(it) }

    }

    private fun shouldSampleValues(jsonPath: List<Pair<RecordFieldType, String?>>): Boolean {
        return shouldSampleValues && analyzeFilter.shouldSampleValuesForPath(jsonPath)
    }

    private fun sampleValue(value: Any): TimestampWrappedFieldValue {
        val nowFalse = wrapNow(false)
        if (value is String && value.length > properties.valueSampling.maxStringLength) {
            return TimestampWrappedFieldValue(nowFalse, tooBig = wrapNow(true))
        }
        if (value is Number && value.toLong().absoluteValue > properties.valueSampling.maxNumberAbs) {
            return TimestampWrappedFieldValue(nowFalse, tooBig = wrapNow(true))
        }
        return TimestampWrappedFieldValue(
            highCardinality = nowFalse,
            tooBig = nowFalse,
            values = listOf(wrapNow(value)),
        )
    }

}

