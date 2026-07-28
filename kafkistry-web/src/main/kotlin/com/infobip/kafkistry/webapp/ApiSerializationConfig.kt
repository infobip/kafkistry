package com.infobip.kafkistry.webapp

import com.fasterxml.jackson.annotation.JsonAutoDetect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@Configuration
class ApiSerializationConfig {

    @Bean
    fun jacksonJsonHttpMessageConverter(): JacksonJsonHttpMessageConverter {
        val jsonMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .changeDefaultVisibility { it.withSetterVisibility(JsonAutoDetect.Visibility.ANY).withCreatorVisibility(JsonAutoDetect.Visibility.ANY) }
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build()
        return JacksonJsonHttpMessageConverter(jsonMapper)
    }


}