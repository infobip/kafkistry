package com.infobip.kafkistry.service.consume.deserialize

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class StringKafkaDeserializer : GenericKafkaDeserializer() {

    private val mapper = jacksonObjectMapper()

    override fun typeName(): DeserializerType = "STRING"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return try {
            val string = String(rawValue, charset = StandardCharsets.UTF_8)
            DeserializedValue(typeName(), string, string, mapper.writeValueAsString(string))
        } catch (ex: Exception) {
            null
        }
    }

    override fun isSolidCandidate(value: Any): Boolean {
        if (value !is String) return false
        return value.isRegularString()
    }

    private fun Char.isRegularChar(): Boolean {
        return this >= ' ' || this == '\t' || this == '\n' || this == '\r'
    }

    private fun String.isRegularString(): Boolean {
        for (char in this) {
            if (!char.isRegularChar()) return false
        }
        return true
    }


}