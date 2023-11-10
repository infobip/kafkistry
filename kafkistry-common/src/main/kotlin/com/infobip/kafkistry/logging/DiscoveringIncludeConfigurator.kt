package com.infobip.kafkistry.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.core.spi.ContextAwareBase
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.annotation.Order
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

@Component
@Order(100)
class DiscoveringIncludeConfigurator  : ContextAwareBase(), Configurator, ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    override fun configure(loggerContext: LoggerContext): Configurator.ExecutionStatus {
        addInfo("Starting configuring")
        val jc = JoranConfigurator().apply { context = loggerContext }
        val resources = PathMatchingResourcePatternResolver().getResources("classpath*:logback-kafkistry-*.xml")
        resources.forEach {
            addInfo("Configuring discovered config: $it")
            jc.doConfigure(it.inputStream)
        }
        addInfo("Completed configuring")
        return Configurator.ExecutionStatus.NEUTRAL
    }

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        configure(LoggerFactory.getILoggerFactory() as LoggerContext)
    }
}