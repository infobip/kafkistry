package com.infobip.kafkistry.service.consume.deserialize

import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component
import java.util.*

@Component
class ByteArrayKafkaDeserializer : GenericKafkaDeserializer() {

    override fun typeName(): DeserializerType = "BYTES"

    override fun isResultMaskable(): Boolean = false

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return DeserializedValue(typeName(), rawValue, rawValue, Base64.getEncoder().encodeToString(rawValue))
    }

    override fun suppressibleByAnything(): Boolean = true
}
