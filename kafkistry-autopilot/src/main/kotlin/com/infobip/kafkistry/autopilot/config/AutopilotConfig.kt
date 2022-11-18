package com.infobip.kafkistry.autopilot.config

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.autopilot.fencing.ActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.HazelcastSyncedActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.LocalActionAcquireFencing
import com.infobip.kafkistry.autopilot.repository.*
import com.infobip.kafkistry.events.EventPublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Configuration
class AutopilotConfig {

    @Bean
    fun actionExecutionFencing(
        hazelcast: ObjectProvider<HazelcastInstance>,
        fencingProperties: ActionsFencingProperties,
    ): ActionAcquireFencing {
        return when (val hazelcastInstance = hazelcast.ifAvailable) {
            null -> LocalActionAcquireFencing(fencingProperties.attemptDelayMs)
            else -> HazelcastSyncedActionAcquireFencing(hazelcastInstance, fencingProperties.attemptDelayMs)
        }
    }

    @Bean
    fun actionsRepository(
        properties: ActionsRepositoryProperties,
        eventPublisher: EventPublisher,
    ): EventInitializingActionsRepository {
        return EventInitializingActionsRepositoryImpl(
            delegate = DiskPersistedActionsRepository(
                delegate = InMemoryActionsRepository(properties.limits),
                storageDir = properties.storageDir,
            ),
            eventPublisher = eventPublisher,
        )
    }

    @Bean
    fun actionsSyncEventListener(
        @Lazy eventSyncedRepository: EventInitializingActionsRepository,
    ) = ActionsRepositorySyncEventListener(eventSyncedRepository)

    @Bean
    @ConfigurationProperties("app.autopilot.cycle")
    fun autopilotCycle() = ActionsCycleProperties()

}

@Component
@ConfigurationProperties("app.autopilot.execution")
class ActionsFencingProperties {
    var attemptDelayMs = 60_000L
}

@Component
@ConfigurationProperties("app.autopilot.repository")
class ActionsRepositoryProperties {
    var storageDir = ""

    @NestedConfigurationProperty
    var limits = LimitsProperties()

    class LimitsProperties {
        var maxCount = 1000
        var maxPerAction = 50
        var retentionMs = TimeUnit.DAYS.toMillis(7)
    }
}

class ActionsCycleProperties {

    var repeatDelayMs = 10_000L
    var afterStartupDelayMs = 60_000L

    //field-like methods to allow invocation/usage in @Scheduled SpEL expressions
    fun repeatDelayMs() = repeatDelayMs
    fun afterStartupDelayMs() = afterStartupDelayMs
}




