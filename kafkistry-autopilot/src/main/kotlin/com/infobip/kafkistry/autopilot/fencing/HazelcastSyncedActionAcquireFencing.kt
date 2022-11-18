package com.infobip.kafkistry.autopilot.fencing

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import java.util.concurrent.TimeUnit.MILLISECONDS

class HazelcastSyncedActionAcquireFencing(
    hazelcastInstance: HazelcastInstance,
    private val ttlMs: Long,
) : ActionAcquireFencing {

    private val value = ""  //dummy unused value

    private val cache =
            hazelcastInstance.getMap<AutopilotActionIdentifier, String>("autopilot-fencing-cache")

    override fun acquireActionExecution(action: AutopilotAction): Boolean {
        val oldAcquirer = cache.put(action.actionIdentifier, value, ttlMs, MILLISECONDS)
        return oldAcquirer == null
    }
}