package com.infobip.kafkistry.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Component

@Component
class YamlMapper {

    private val mapper: ObjectMapper = YAMLFactory()
        .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
        .let { ObjectMapper(it) }
        .registerKotlinModule()

    fun serialize(any: Any?): String = mapper.writeValueAsString(any)

    fun <T> deserialize(yaml: String, clazz: Class<T>): T = mapper.readValue(yaml, clazz)

}