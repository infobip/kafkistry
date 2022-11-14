package com.infobip.kafkistry.autopilot.binding

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper for caching for one cycle/round of [com.infobip.kafkistry.autopilot.Autopilot].
 * Implementations of [AutopilotBinding] can use it while checking for blockers.
 * It's useful because one Autopilot's cycle can result with multiple [AutopilotAction]s for which same
 * computation during checking for blockers is needed, therefore caching mechanism.
 *
 * @see com.infobip.kafkistry.autopilot.Autopilot
 * @see AutopilotBinding
 * @see AutopilotAction
 */
@Component
class CheckingCache {

    private val cachedValues = ThreadLocal<ConcurrentHashMap<String, Any?>>()

    fun <T> withThreadLocalCache(block: () -> T): T {
        val alreadySet = cachedValues.get() != null
        if (!alreadySet) {
            cachedValues.set(ConcurrentHashMap())
        }
        return try {
            block()
        } finally {
            if (!alreadySet) {
                cachedValues.remove()
            }
        }
    }

    private fun cache(): ConcurrentHashMap<String, Any?> = cachedValues.get()
        ?: throw IllegalStateException("cache() can only be using within 'withThreadLocalCache { <use here> }' block")

    fun <T> cache(type: Class<T>, key: String, initializer: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return cache().computeIfAbsent(type.name + key) { initializer() } as T
    }

    final inline fun <reified T> cache(
        what: String, vararg params: Any?, noinline initializer: () -> T
    ): T = cache(T::class.java, what + ":" + params.joinToString("-"), initializer)

}