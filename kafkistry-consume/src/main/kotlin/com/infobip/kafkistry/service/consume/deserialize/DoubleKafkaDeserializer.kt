package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.common.serialization.DoubleDeserializer
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component

@Component
class DoubleKafkaDeserializer : GenericKafkaDeserializer() {

    private val deserializer = DoubleDeserializer()

    override fun typeName(): DeserializerType = "DOUBLE"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return if (rawValue.size == 8) {
            val double = deserializer.deserialize("", rawValue)
            DeserializedValue(typeName(), double, double, double.toString())
        } else {
            null
        }
    }
}
