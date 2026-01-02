package com.infobip.kafkistry.webapp.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.api.RecordsStructureApi
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PayloadType
import com.infobip.kafkistry.model.RecordField
import com.infobip.kafkistry.model.RecordFieldType
import com.infobip.kafkistry.model.RecordsStructure
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class SampleDataExtractor(
    private val recordsStructureApiOpt: Optional<RecordsStructureApi>
) {

    fun extractSampleData(
        clusterIdentifier: KafkaClusterIdentifier?,
        topicName: TopicName?
    ): SampleData {
        if (clusterIdentifier == null || topicName == null) {
            return SampleData(null, null, emptyList())
        }

        return recordsStructureApiOpt.orElse(null)
            ?.let {
                it.structureOfTopicOnCluster(topicName, clusterIdentifier)
                    ?: it.structureOfTopic(topicName)  //failover to globally combined structure
            }
            ?.toSampleData()
            ?: return SampleData(null, null, emptyList())
    }

    private fun RecordsStructure.toSampleData(): SampleData {
        // Extract sample value from JSON fields
        val jsonFields = jsonFields
        val sampleValue = if (payloadType == PayloadType.JSON && jsonFields != null) {
            buildSampleJson(jsonFields)
        } else {
            null
        }

        // Extract sample headers
        val sampleHeaders = headerFields?.firstOrNull()?.children?.map { headerField ->
            val name = headerField.name ?: ""
            val value = headerField.value?.valueSet
                ?.firstOrNull()
                ?.let { value ->
                    when (value) {
                        is String -> value
                        is Number -> value.toString()
                        is ByteArray -> value.decodeToString()
                        is Boolean -> value.toString()
                        else -> value.toString()
                    }
                } ?: ""
            SampleHeader(name, value)
        } ?: emptyList()

        return SampleData(null, sampleValue, sampleHeaders)

    }

    private fun buildSampleJson(fields: List<RecordField>): String {
        val rootField = fields.firstOrNull() ?: return ""
        val sample = rootField.extractSampleValue()
        return jacksonObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(sample)
    }

    private fun RecordField.extractSampleValue(): Any? {
        return when (type) {
            RecordFieldType.NULL -> null
            RecordFieldType.OBJECT -> {
                children?.mapNotNull { child ->
                    child.name?.let { name -> name to child.extractSampleValue() }
                }?.toMap() ?: emptyMap<String, Any?>()
            }
            RecordFieldType.ARRAY -> {
                children?.map { it.extractSampleValue() } ?: emptyList<Any?>()
            }
            else -> value?.valueSet?.firstOrNull() ?: when (type) {
                RecordFieldType.STRING -> ""
                RecordFieldType.BOOLEAN -> false
                RecordFieldType.INTEGER -> 0
                RecordFieldType.DECIMAL -> 0.0
                RecordFieldType.DATE -> Instant.ofEpochMilli(0)
                RecordFieldType.BYTES -> byteArrayOf()
                else -> null
            }
        }
    }
}

data class SampleData(
    val key: String?,
    val value: String?,
    val headers: List<SampleHeader>,
)

data class SampleHeader(
    val name: String,
    val value: String,
)
