package com.infobip.kafkistry.service.consume.deserialize

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.DeserializedValue
import com.infobip.kafkistry.service.consume.interntopics.InternalTopicsValueReader
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Component

@Component
class ConsumerOffsetDeserializer(
    private val internalTopicsValueReader: InternalTopicsValueReader
) : KafkaDeserializer {

    private val topic = "__consumer_offsets"
    private val mapper = jacksonObjectMapper()

    override fun typeName(): DeserializerType = "CONSUMER_OFFSET"

    override fun supportsValue(topic: TopicName): Boolean = topic == this.topic

    override fun supportsKey(topic: TopicName): Boolean = topic == this.topic

    override fun isResultMaskable(): Boolean = true

    override fun deserializeKey(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = with(internalTopicsValueReader) {
        record.consumerOffsetKey()?.let { key ->
            val asJson = mapper.writeValueAsString(key)
            val asValue = mapper.readValue(asJson, Any::class.java)
            DeserializedValue(typeName(), key, asValue, asJson)
        }
    }

    override fun deserializeValue(
        rawValue: ByteArray,
        record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = with(internalTopicsValueReader) {
        record.consumerOffsetValue()?.let { consumerOffsetMeta ->
            val asJson = mapper.writeValueAsString(consumerOffsetMeta)
            val asValue = mapper.readValue(asJson, Any::class.java)
            DeserializedValue(typeName(), consumerOffsetMeta, asValue, asJson)
        }
    }
}