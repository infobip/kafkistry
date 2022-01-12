package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.common.serialization.IntegerDeserializer
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component

@Component
class IntKafkaDeserializer : GenericKafkaDeserializer() {

    private val deserializer = IntegerDeserializer()

    override fun typeName(): DeserializerType = "INT"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return if (rawValue.size == 4) {
            val integer = deserializer.deserialize("", rawValue)
            DeserializedValue(typeName(), integer, integer, integer.toString())
        } else {
            null
        }
    }
}