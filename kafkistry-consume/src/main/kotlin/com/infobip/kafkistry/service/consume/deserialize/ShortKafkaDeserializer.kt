package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.common.serialization.ShortDeserializer
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component

@Component
class ShortKafkaDeserializer : GenericKafkaDeserializer() {

    private val deserializer = ShortDeserializer()

    override fun typeName(): DeserializerType = "SHORT"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return if (rawValue.size == 2) {
            val short = deserializer.deserialize("", rawValue)
            DeserializedValue(typeName(), short, short, short.toString())
        } else {
            null
        }
    }
}