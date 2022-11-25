package com.infobip.kafkistry.autopilot.fencing

import com.infobip.kafkistry.autopilot.binding.ClusterUnstable
import com.infobip.kafkistry.autopilot.config.AutopilotClusterStableRequirementProperties
import com.infobip.kafkistry.kafkastate.AbstractKafkaStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.utils.Filter
import java.util.concurrent.ConcurrentHashMap

class ClusterStableFencing(
    private val properties: AutopilotClusterStableRequirementProperties,
    private val stateProviders: List<AbstractKafkaStateProvider<*>>,
) {

    private val clusterLastNonOkStates = ConcurrentHashMap<KafkaClusterIdentifier, ClusterLastNonOkStates>()
    private val stateProviderFilter = Filter(properties.usedStateProviders)

    fun refresh() {
        stateProviders.forEach { provider ->
            provider.getAllLatestStates().forEach { (cluster, state) ->
                clusterLastNonOkStates
                    .computeIfAbsent(cluster) { ClusterLastNonOkStates() }
                    .observe(state)
            }
        }
        clusterLastNonOkStates.entries.removeIf { (_, states) ->
            !states.hasRecentUpdates()
        }
    }

    fun recentUnstableStates(identifier: KafkaClusterIdentifier): List<ClusterUnstable> {
        return clusterLastNonOkStates[identifier]
            ?.recentNonOkStates()
            ?: listOf(ClusterUnstable(StateType.UNKNOWN, "*", System.currentTimeMillis()))
    }

    private inner class ClusterLastNonOkStates {

        private val typeLastNonOkState = ConcurrentHashMap<String, StateData<*>>()
        private val typeLastUpdateTime = ConcurrentHashMap<String, Long>()

        fun observe(state: StateData<*>) {
            if (state.stateType != StateType.VISIBLE) {
                typeLastNonOkState[state.stateTypeName] = state
            }
            typeLastUpdateTime[state.stateTypeName] = System.currentTimeMillis()
        }

        fun recentNonOkStates(): List<ClusterUnstable> {
            val maxTime = System.currentTimeMillis() - properties.stableForLastMs
            return typeLastNonOkState.filter { (providerName, stateData) ->
                stateData.lastRefreshTime >= maxTime && stateProviderFilter(providerName)
            }.values.map { ClusterUnstable(it.stateType, it.stateTypeName, it.lastRefreshTime) }
        }

        fun hasRecentUpdates(): Boolean {
            val minTime = System.currentTimeMillis() - properties.stableForLastMs
            return typeLastUpdateTime.values.any { it >= minTime }
        }

    }

}