package com.infobip.kafkistry.audit

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ManagementEventLoggingSubscriber : ManagementEventSubscriber {

    private val loggers = ConcurrentHashMap<String, Logger>()
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule())
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    private fun String.log() = loggers.computeIfAbsent(this, LoggerFactory::getLogger)

    private fun AuditEvent.toPrettyJson() = objectMapper.writeValueAsString(this)

    override fun handleEvent(event: AuditEvent) {
        event.serviceClass.log().info("Event:\n" + event.toPrettyJson())
    }

}