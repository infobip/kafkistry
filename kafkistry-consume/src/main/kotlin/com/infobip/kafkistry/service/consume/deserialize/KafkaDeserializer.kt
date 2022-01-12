package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.DeserializedValue
import com.infobip.kafkistry.model.TopicName

typealias DeserializerType = String

interface KafkaDeserializer {

    fun typeName(): DeserializerType

    fun supportsValue(topic: TopicName): Boolean = false
    fun supportsKey(topic: TopicName): Boolean = false
    fun supportsHeader(topic: TopicName, headerName: String): Boolean = false

    fun deserializeKey(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = null

    fun deserializeValue(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = null

    fun deserializeHeader(
        rawValue: ByteArray, headerName: String, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = null

    /**
     * Return which other deserializer this deserializer suppresses, in other words,
     * it is more specific/better match candidate for type auto-detection
     */
    fun suppresses(): List<DeserializerType> = emptyList()

    fun suppressableByAnything(): Boolean = false

    /**
     * Return `false` when specific [value] is successfully deserialized but is unlikely good candidate for
     * type auto-detection
     */
    fun isSolidCandidate(value: Any): Boolean = true

}

abstract class GenericKafkaDeserializer : KafkaDeserializer {

    override fun supportsValue(topic: TopicName): Boolean = true
    override fun supportsKey(topic: TopicName): Boolean = true
    override fun supportsHeader(topic: TopicName, headerName: String): Boolean = true

    override fun deserializeKey(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = deserialize(rawValue)

    override fun deserializeValue(
        rawValue: ByteArray, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = deserialize(rawValue)

    override fun deserializeHeader(
        rawValue: ByteArray, headerName: String, record: ConsumerRecord<ByteArray, ByteArray>
    ): DeserializedValue? = deserialize(rawValue)

    abstract fun deserialize(rawValue: ByteArray): DeserializedValue?
}

