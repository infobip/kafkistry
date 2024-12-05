package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.service.consume.deserialize.KafkaDeserializer
import org.apache.kafka.clients.consumer.ConsumerRecord

class CsvTestDeserializer : KafkaDeserializer {

    companion object {
        const val TOPIC_NAME = "csv-topic"
    }

    override fun typeName(): DeserializerType = "CSV"

    override fun suppresses(): List<DeserializerType> = listOf("STRING")

    override fun supportsValue(topic: TopicName): Boolean = topic == TOPIC_NAME

    override fun deserializeValue(
        rawValue: ByteArray,
        record: ConsumerRecord<ByteArray, ByteArray>,
    ): DeserializedValue? {
        val csv = String(rawValue)
        val elements = csv.split(',')
        return DeserializedValue(
            "CSV", elements, elements, elements.joinToString(",", "[", "]") { "\"$it\"" },
            isTransformed = true,
        )
    }
}