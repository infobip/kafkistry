package com.infobip.kafkistry.service.consume.serialize

import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.common.serialization.*
import org.springframework.stereotype.Component
import java.util.*

@Component
class StringKeySerializer : KeySerializer {
    override val type: KeySerializerType = "STRING"
    override fun serialize(topic: TopicName, key: String): ByteArray = key.toByteArray()
}

@Component
class Base64KeySerializer : KeySerializer {
    private val decoder = Base64.getDecoder()
    override val type: KeySerializerType = "BASE64"
    override fun serialize(topic: TopicName, key: String): ByteArray = decoder.decode(key)
}

@Component
class IntegerKeySerializer : KeySerializer {
    private val serializer = IntegerSerializer()
    override val type: KeySerializerType = "INTEGER"
    override fun serialize(topic: TopicName, key: String): ByteArray = serializer.serialize(topic, key.toInt())
}

@Component
class LongKeySerializer : KeySerializer {
    private val serializer = LongSerializer()
    override val type: KeySerializerType = "LONG"
    override fun serialize(topic: TopicName, key: String): ByteArray = serializer.serialize(topic, key.toLong())
}

@Component
class FloatKeySerializer : KeySerializer {
    private val serializer = FloatSerializer()
    override val type: KeySerializerType = "FLOAT"
    override fun serialize(topic: TopicName, key: String): ByteArray = serializer.serialize(topic, key.toFloat())
}

@Component
class DoubleKeySerializer : KeySerializer {
    private val serializer = DoubleSerializer()
    override val type: KeySerializerType = "DOUBLE"
    override fun serialize(topic: TopicName, key: String): ByteArray = serializer.serialize(topic, key.toDouble())
}

@Component
class ShortKeySerializer : KeySerializer {
    private val serializer = ShortSerializer()
    override val type: KeySerializerType = "SHORT"
    override fun serialize(topic: TopicName, key: String): ByteArray = serializer.serialize(topic, key.toShort())
}
