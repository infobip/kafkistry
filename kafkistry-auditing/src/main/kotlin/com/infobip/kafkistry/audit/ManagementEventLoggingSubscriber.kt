package com.infobip.kafkistry.audit

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.SerializationFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.concurrent.ConcurrentHashMap

@Component
class ManagementEventLoggingSubscriber : ManagementEventSubscriber {

    private val loggers = ConcurrentHashMap<String, Logger>()
    private val objectMapper = jacksonMapperBuilder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .build()

    private fun String.log() = loggers.computeIfAbsent(this, LoggerFactory::getLogger)

    private fun AuditEvent.toPrettyJson() = objectMapper.writeValueAsString(this)

    override fun handleEvent(event: AuditEvent) {
        event.serviceClass.log().info("Event:\n" + event.toPrettyJson())
    }

}