package com.infobip.kafkistry.service.produce.serialize

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.common.serialization.*
import org.springframework.stereotype.Component
import java.util.*

@Component
class StringValueSerializer : ValueSerializer {
    override val type: SerializerType = "STRING"
    override fun serialize(topic: TopicName, value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
}

@Component
class Base64ValueSerializer : ValueSerializer {
    private val decoder = Base64.getDecoder()
    override val type: SerializerType = "BASE64"
    override fun serialize(topic: TopicName, value: String): ByteArray = decoder.decode(value)
}

@Component
class IntegerValueSerializer : ValueSerializer {
    private val serializer = IntegerSerializer()
    override val type: SerializerType = "INTEGER"
    override fun serialize(topic: TopicName, value: String): ByteArray = serializer.serialize(topic, value.toInt())
}

@Component
class LongValueSerializer : ValueSerializer {
    private val serializer = LongSerializer()
    override val type: SerializerType = "LONG"
    override fun serialize(topic: TopicName, value: String): ByteArray = serializer.serialize(topic, value.toLong())
}

@Component
class FloatValueSerializer : ValueSerializer {
    private val serializer = FloatSerializer()
    override val type: SerializerType = "FLOAT"
    override fun serialize(topic: TopicName, value: String): ByteArray = serializer.serialize(topic, value.toFloat())
}

@Component
class DoubleValueSerializer : ValueSerializer {
    private val serializer = DoubleSerializer()
    override val type: SerializerType = "DOUBLE"
    override fun serialize(topic: TopicName, value: String): ByteArray = serializer.serialize(topic, value.toDouble())
}

@Component
class ShortValueSerializer : ValueSerializer {
    private val serializer = ShortSerializer()
    override val type: SerializerType = "SHORT"
    override fun serialize(topic: TopicName, value: String): ByteArray = serializer.serialize(topic, value.toShort())
}

@Component
class JsonValueSerializer : ValueSerializer {
    private val objectMapper = jacksonObjectMapper()
    override val type: SerializerType = "JSON"
    override fun serialize(topic: TopicName, value: String): ByteArray {
        objectMapper.readTree(value)
        return value.toByteArray(Charsets.UTF_8)
    }
}

@Component
class SerializerResolver(
    private val serializers: List<ValueSerializer>
) {
    fun resolve(type: SerializerType): ValueSerializer {
        return serializers.find { it.type == type }
            ?: throw IllegalArgumentException("Unknown serializer type: $type, available types: ${availableTypes()}")
    }

    fun availableTypes(): List<SerializerType> =
        serializers.map { it.type }
}
