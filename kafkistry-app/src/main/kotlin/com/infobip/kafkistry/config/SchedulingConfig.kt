package com.infobip.kafkistry.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Role
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Configure scheduling so that thread pool size is same as number of `@Scheduled` tasks
 */
@Configuration
@ConditionalOnProperty(value = ["app.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
@ConfigurationProperties("app.scheduling")
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SchedulingConfig {

    var enabled = true

    private val log = LoggerFactory.getLogger(com.infobip.kafkistry.config.SchedulingConfig::class.java)

    @Bean
    fun scheduler() = ThreadPoolTaskScheduler()

    @Bean
    fun taskRegistrar(
        scheduler: ThreadPoolTaskScheduler
    ) = ScheduledTaskRegistrar().apply { setScheduler(scheduler) }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    fun scheduledAnnotationProcessor(
        @Lazy scheduler: ThreadPoolTaskScheduler,
        @Lazy taskRegistrar: ScheduledTaskRegistrar,
    ) =
        object : ScheduledAnnotationBeanPostProcessor(taskRegistrar) {
            override fun onApplicationEvent(event: ContextRefreshedEvent) {
                super.onApplicationEvent(event)
                val registeredTasks = scheduledTasks
                val poolSize = registeredTasks.size.coerceAtLeast(1)
                log.info("Re-configuring thread pool size to: {}", poolSize)
                scheduler.poolSize = poolSize
                registeredTasks.forEach {
                    log.info("Having registered @Scheduled task: {}", it)
                }
            }
        }

}