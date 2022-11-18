package com.infobip.kafkistry.autopilot.fencing

import com.google.common.cache.CacheBuilder
import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class LocalActionAcquireFencing(ttlMs: Long) : ActionAcquireFencing {

    private val cache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMillis(ttlMs))
        .build<AutopilotActionIdentifier, Unit>()

    override fun acquireActionExecution(action: AutopilotAction): Boolean {
        val loaded = AtomicBoolean(false)
        cache.get(action.actionIdentifier) {
            loaded.set(true)
        }
        return loaded.get()
    }
}