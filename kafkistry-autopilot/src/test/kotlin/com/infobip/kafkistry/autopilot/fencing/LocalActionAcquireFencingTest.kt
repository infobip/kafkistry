package com.infobip.kafkistry.autopilot.fencing

internal class LocalActionAcquireFencingTest : BaseActionAcquireFencingTest() {

    override fun withFencing(ttlMs: Long, block: ActionAcquireFencing.() -> Unit) {
        with(LocalActionAcquireFencing(ttlMs), block)
    }

    override fun withConcurrentFencing(
        ttlMs: Long,
        block: (first: ActionAcquireFencing, second: ActionAcquireFencing) -> Unit
    ) {
        val fencing = LocalActionAcquireFencing(ttlMs)
        block(fencing, fencing)
    }
}