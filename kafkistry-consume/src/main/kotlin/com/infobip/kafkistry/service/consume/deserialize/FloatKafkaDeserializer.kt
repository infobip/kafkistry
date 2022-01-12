package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.common.serialization.FloatDeserializer
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component

@Component
class FloatKafkaDeserializer : GenericKafkaDeserializer() {

    private val deserializer = FloatDeserializer()

    override fun typeName(): DeserializerType = "FLOAT"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return if (rawValue.size == 4) {
            val float = deserializer.deserialize("", rawValue)
            DeserializedValue(typeName(), float, float, float.toString())
        } else {
            null
        }
    }
}