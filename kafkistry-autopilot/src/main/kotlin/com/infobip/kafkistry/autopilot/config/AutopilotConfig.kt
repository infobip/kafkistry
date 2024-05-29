package com.infobip.kafkistry.autopilot.config

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.autopilot.fencing.ActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.ClusterStableFencing
import com.infobip.kafkistry.autopilot.fencing.HazelcastSyncedActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.LocalActionAcquireFencing
import com.infobip.kafkistry.autopilot.repository.*
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.kafkastate.AbstractKafkaStateProvider
import com.infobip.kafkistry.kafkastate.NodeDiskMetricsStateProvider.Companion.NODES_DISK_METRICS
import com.infobip.kafkistry.kafkastate.KafkaRecordSamplerProvider.Companion.RECORDS_SAMPLING
import com.infobip.kafkistry.utils.FilterProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
class AutopilotConfig {

    @Bean
    fun actionExecutionFencing(
        hazelcast: ObjectProvider<HazelcastInstance>,
        properties: AutopilotRootProperties,
    ): ActionAcquireFencing {
        return when (val hazelcastInstance = hazelcast.ifAvailable) {
            null -> LocalActionAcquireFencing(properties.attemptDelayMs)
            else -> HazelcastSyncedActionAcquireFencing(hazelcastInstance, properties.attemptDelayMs)
        }
    }

    @Bean
    fun clusterStableFencing(
        stateProviders: List<AbstractKafkaStateProvider<*>>,
        properties: AutopilotClusterStableRequirementProperties,
    ) = ClusterStableFencing(properties, stateProviders)

    @Bean
    fun actionsRepository(
        properties: AutopilotRootProperties,
        repositoryProperties: ActionsRepositoryProperties,
        eventPublisher: EventPublisher,
    ): EventInitializingActionsRepository {
        return EventInitializingActionsRepositoryImpl(
            delegate = DiskPersistedActionsRepository(
                delegate = InMemoryActionsRepository(
                    repeatWindowMs = properties.attemptDelayMs,
                    properties = repositoryProperties.limits,
                ),
                storageDir = repositoryProperties.storageDir,
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
@ConfigurationProperties("app.autopilot")
class AutopilotRootProperties {
    var enabled = true
    var pendingDelayMs = 20_000L
    var attemptDelayMs = 60_000L
}

@Component
@ConfigurationProperties("app.autopilot.cluster-requirements")
class AutopilotClusterStableRequirementProperties {

    var stableForLastMs = 10 * 60 * 1000L

    @NestedConfigurationProperty
    val usedStateProviders = FilterProperties().apply {
        excluded = setOf(
            NODES_DISK_METRICS,
            RECORDS_SAMPLING,
        )
    }
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




