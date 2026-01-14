package com.infobip.kafkistry.autopilot.fencing

import com.infobip.kafkistry.autopilot.config.AutopilotClusterStableRequirementProperties
import com.infobip.kafkistry.kafkastate.AbstractKafkaStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ClusterStableFencingTest {

    @Test
    fun `test all stable`() {
        val fencing = newFencing {
            mockState(5_000, "up-cluster", StateType.VISIBLE)
            mockState(0, "up-cluster", StateType.VISIBLE)
        }
        val unstableReasons = fencing.recentUnstableStates("up-cluster")
        assertThat(unstableReasons).isEmpty()
    }

    @Test
    fun `test all unstable`() {
        val fencing = newFencing {
            mockState(5_000, "down-cluster", StateType.UNREACHABLE)
            mockState(0, "down-cluster", StateType.UNREACHABLE)
        }
        val unstableReasons = fencing.recentUnstableStates("down-cluster")
        assertThat(unstableReasons).isNotEmpty
    }

    @Test
    fun `test recently unstable`() {
        val fencing = newFencing {
            mockState(15_000, "recent-cluster", StateType.UNREACHABLE)
            mockState(5_000, "recent-cluster", StateType.UNREACHABLE)
            mockState(0, "recent-cluster", StateType.VISIBLE)
        }
        val unstableReasons = fencing.recentUnstableStates("recent-cluster")
        assertThat(unstableReasons).isNotEmpty
    }

    @Test
    fun `test long ago unstable`() {
        val fencing = newFencing {
            mockState(15_000, "ago-cluster", StateType.UNREACHABLE)
            mockState(5_000, "ago-cluster", StateType.VISIBLE)
            mockState(0, "ago-cluster", StateType.VISIBLE)
        }
        val unstableReasons = fencing.recentUnstableStates("ago-cluster")
        assertThat(unstableReasons).isEmpty()
    }


    @Test
    fun `test unknown cluster`() {
        val fencing = newFencing()
        val unstableReasons = fencing.recentUnstableStates("unknown-cluster")
        assertThat(unstableReasons).isNotEmpty
    }

    @Test
    fun `test ignore particular state provider`() {
        val fencing = newFencing {
            properties { usedStateProviders.excluded = setOf("ignored") }
            mockState(0, "cluster", "important" to StateType.VISIBLE, "ignored" to StateType.UNREACHABLE)
        }
        val unstableReasons = fencing.recentUnstableStates("cluster")
        assertThat(unstableReasons).isEmpty()
    }

    private fun newFencing(mocking: FencingMocker.() -> Unit = {}): ClusterStableFencing {
        val properties = AutopilotClusterStableRequirementProperties()
            .apply { stableForLastMs = 10_000L }
        val providers = mutableListOf<AbstractKafkaStateProvider<*>>()
        return ClusterStableFencing(properties, providers).apply {
            FencingMocker(this, properties, providers).mocking()
        }
    }

    private class FencingMocker(
        private val fencing: ClusterStableFencing,
        private val properties: AutopilotClusterStableRequirementProperties,
        private val providers: MutableList<AbstractKafkaStateProvider<*>>,
    ) {

        fun properties(configurer: AutopilotClusterStableRequirementProperties.() -> Unit) {
            properties.apply(configurer)
        }

        fun mockState(
            before: Long, cluster: KafkaClusterIdentifier, state: StateType,
        ) = mockState(before, cluster, "mock" to state)

        fun mockState(
            before: Long, cluster: KafkaClusterIdentifier, vararg nameStates: Pair<String, StateType>,
        ) {
            val time = System.currentTimeMillis() - before
            val tmpProviders = nameStates.map { (stateName, stateType) ->
                val stateData = StateData(
                    stateType, cluster, stateName, time, time, if (stateType == StateType.VISIBLE) Unit else null
                )
                mock<AbstractKafkaStateProvider<*>>().also {
                    whenever(it.getAllLatestStates()).thenReturn(mapOf(cluster to stateData))
                }
            }
            providers.clear()
            providers.addAll(tmpProviders)
            fencing.refresh()
        }

    }

}
