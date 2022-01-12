package com.infobip.kafkistry.service.consume.deserialize

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.infobip.kafkistry.service.consume.DeserializedValue
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class JsonKafkaDeserializer : GenericKafkaDeserializer() {

    private val mapper = jacksonMapperBuilder()
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
        .build()

    override fun typeName(): DeserializerType = "JSON"

    override fun deserialize(rawValue: ByteArray): DeserializedValue? {
        return try {
            val json = String(rawValue, charset = StandardCharsets.UTF_8)
            val value = mapper.readValue(json, Any::class.java)
            DeserializedValue(typeName(), value, value, json)
        } catch (ex: Exception) {
            null
        }
    }

    override fun suppresses(): List<DeserializerType> = listOf("STRING")

    override fun isSolidCandidate(value: Any): Boolean {
        return when (value) {
            is Int, is Long, is Boolean, is Float, is Double -> false
            else -> true
        }
    }
}