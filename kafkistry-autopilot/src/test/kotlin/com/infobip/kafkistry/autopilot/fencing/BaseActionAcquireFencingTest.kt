package com.infobip.kafkistry.autopilot.fencing

import com.infobip.kafkistry.autopilot.TestAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal abstract class BaseActionAcquireFencingTest {

    private val action = TestAction("fencing-action")
    private val otherAction = TestAction("other-fencing-action")

    abstract fun withFencing(ttlMs: Long, block: ActionAcquireFencing.() -> Unit)

    abstract fun withConcurrentFencing(
        ttlMs: Long, block: (first: ActionAcquireFencing, second: ActionAcquireFencing) -> Unit
    )

    @Test
    fun `test empty then acquire then no acquire then acquire after ttl`() {
        withFencing(1_000) {
            val initAcquire = acquireActionExecution(action)
            val secondAcquire = acquireActionExecution(action)
            val initAcquireOther = acquireActionExecution(otherAction)
            val secondAcquireOther = acquireActionExecution(otherAction)
            Thread.sleep(1001)
            val laterAcquire = acquireActionExecution(action)
            val laterAcquireOther = acquireActionExecution(otherAction)
            assertAll(
                { assertThat(initAcquire).`as`("initial acquire").isTrue() },
                { assertThat(secondAcquire).`as`("second acquire").isFalse() },
                { assertThat(laterAcquire).`as`("later acquire").isTrue() },
                { assertThat(initAcquireOther).`as`("initial acquire other").isTrue() },
                { assertThat(secondAcquireOther).`as`("second acquire other").isFalse() },
                { assertThat(laterAcquireOther).`as`("later acquire other").isTrue() },
            )
        }
    }

    private fun <T> Executor.execAsync(block: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(block, this)
    }

    @Test
    fun `two threads racing for acquire`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            withConcurrentFencing(1_000) { fA, fB ->
                val acquireA1 = executor.execAsync { fA.acquireActionExecution(action) }
                val acquireB1 = executor.execAsync { fB.acquireActionExecution(action) }
                assertThat(acquireA1.get()).`as`(
                    "only one wins, expect different acquire results, " +
                            "acquireA1=${acquireA1.get()}, acquireB1=${acquireB1.get()}"
                ).isNotEqualTo(acquireB1.get())
                val acquireA2 = executor.execAsync { fA.acquireActionExecution(action) }
                val acquireB2 = executor.execAsync { fB.acquireActionExecution(action) }
                assertAll(
                    { assertThat(acquireA2.get()).`as`("acquireA2").isFalse() },
                    { assertThat(acquireB2.get()).`as`("acquireB2").isFalse() },
                )
                Thread.sleep(1001)
                val acquireA3 = executor.execAsync { fA.acquireActionExecution(action) }
                val acquireB3 = executor.execAsync { fB.acquireActionExecution(action) }
                assertThat(acquireA3.get()).`as`(
                    "only one wins after expire, expect different acquire results, " +
                            "acquireA3=${acquireA3.get()}, acquireB3=${acquireB3.get()}"
                ).isNotEqualTo(acquireB3.get())
            }
        } finally {
            executor.shutdown()
        }
    }

}