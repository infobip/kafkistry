package com.infobip.kafkistry.autopilot.fencing

import com.hazelcast.config.Config
import com.hazelcast.core.Hazelcast

internal class HazelcastSyncedActionAcquireFencingTest : BaseActionAcquireFencingTest() {

    override fun withFencing(ttlMs: Long, block: ActionAcquireFencing.() -> Unit) {
        val hazelcastInstance = Hazelcast.newHazelcastInstance()
        try {
            val fencing = HazelcastSyncedActionAcquireFencing(hazelcastInstance, ttlMs)
            with(fencing, block)
        } finally {
            hazelcastInstance.shutdown()
        }
    }

    override fun withConcurrentFencing(
        ttlMs: Long,
        block: (first: ActionAcquireFencing, second: ActionAcquireFencing) -> Unit
    ) {
        val config = Config.loadDefault().apply {
            clusterName = "fence-test"
            networkConfig.join.tcpIpConfig.apply {
                isEnabled = true
                addMember("127.0.0.1")
            }
        }
        val hazelcastInstance1 = Hazelcast.newHazelcastInstance(config)
        val hazelcastInstance2 = Hazelcast.newHazelcastInstance(config)
        try {
            val fencing1 = HazelcastSyncedActionAcquireFencing(hazelcastInstance1, ttlMs)
            val fencing2 = HazelcastSyncedActionAcquireFencing(hazelcastInstance2, ttlMs)
            block(fencing1, fencing2)
        } finally {
            hazelcastInstance2.shutdown()
            hazelcastInstance1.shutdown()
        }
    }
}