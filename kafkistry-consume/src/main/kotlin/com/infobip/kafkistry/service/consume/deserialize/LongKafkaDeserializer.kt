package com.infobip.kafkistry.service.consume.deserialize

import org.apache.kafka.common.serialization.LongDeserializer
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component

@Component
class LongKafkaDeserializer : GenericKafkaDeserializer() {

    private val deserializer = LongDeserializer()

    override fun typeName(): DeserializerType = "LONG"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return if (rawValue.size == 8) {
            val long = deserializer.deserialize("", rawValue)
            DeserializedValue(typeName(), long, long, long.toString())
        } else {
            null
        }
    }
}