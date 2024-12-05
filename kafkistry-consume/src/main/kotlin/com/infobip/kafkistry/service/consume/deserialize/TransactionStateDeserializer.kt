package com.infobip.kafkistry.service.consume.deserialize

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.DeserializedValue
import com.infobip.kafkistry.service.consume.interntopics.InternalTopicsValueReader
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Component

@Component
class TransactionStateDeserializer(
    private val internalTopicsValueReader: InternalTopicsValueReader
) : KafkaDeserializer {

    private val topic = "__transaction_state"
    private val mapper = jacksonObjectMapper()

    override fun typeName(): DeserializerType = "TRANSACTION_STATE"

    override fun supportsValue(topic: TopicName): Boolean = topic == this.topic

    override fun supportsKey(topic: TopicName): Boolean = topic == this.topic

    override fun isResultMaskable(): Boolean = true

    override fun deserializeKey(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = with(internalTopicsValueReader) {
        record.transactionStateKey()?.let { key ->
            val asJson = mapper.writeValueAsString(key)
            val asValue = mapper.readValue(asJson, Any::class.java)
            DeserializedValue(typeName(), key, asValue, asJson, isTransformed = true)
        }
    }

    override fun deserializeValue(
        rawValue: ByteArray,
        record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = with(internalTopicsValueReader) {
        record.transactionStateValue()?.let { transactionState ->
            val asJson = mapper.writeValueAsString(transactionState)
            val asValue = mapper.readValue(asJson, Any::class.java)
            DeserializedValue(typeName(), transactionState, asValue, asJson, isTransformed = true)
        }
    }
}