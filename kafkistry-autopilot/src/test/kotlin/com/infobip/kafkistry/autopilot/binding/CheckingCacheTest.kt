package com.infobip.kafkistry.autopilot.binding

import com.nhaarman.mockitokotlin2.*
import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

internal class CheckingCacheTest {

    private val cache = CheckingCache()

    interface MockComputation : (String) -> String

    private val computation = mock<MockComputation>()

    @BeforeEach
    fun resetMock() {
        reset(computation)
    }

    @AfterEach
    fun allInteractionsVerified() {
        verifyNoMoreInteractions(computation)
    }

    private fun call(key: String) = cache.cache("test", key) { computation(key) }

    @Test
    fun `computation called once per key`() {
        whenever(computation.invoke("key1")).thenReturn("value1")
        whenever(computation.invoke("key2")).thenReturn("value2")
        val (r1, r2, r3) = cache.withThreadLocalCache {
            val result1 = call("key1")
            val result2 = call("key1")
            val result3 = call("key2")
            listOf(result1, result2, result3)
        }
        assertAll(
            { assertThat(r1).isEqualTo("value1") },
            { assertThat(r2).isEqualTo("value1") },
            { assertThat(r3).isEqualTo("value2") },
        )
        verify(computation, times(1)).invoke("key1")
        verify(computation, times(1)).invoke("key2")
    }

    @Test
    fun `protection from usage outside withThreadLocalCache block`() {
        assertThrows<IllegalStateException> {
            call("irrelevant")
        }
    }

    @Test
    fun `protection from nesting of withThreadLocalCache blocks`() {
        whenever(computation.invoke("key")).thenReturn("value")
        whenever(computation.invoke("outKey")).thenReturn("outVal")
        whenever(computation.invoke("inKey")).thenReturn("inVal")
        with(cache) {
            withThreadLocalCache {
                val keyResult1 = call("key")
                val outKeyResult1 = call("outKey")
                val (inKeyResult1, inKeyResult2) = withThreadLocalCache {
                    val inKeyResult1 = call("inKey")
                    val inKeyResult2 = call("inKey")
                    inKeyResult1 to inKeyResult2
                }
                val keyResult2 = call("key")
                val outKeyResult2 = call("outKey")
                assertAll(
                    { assertThat(keyResult1).`as`("keyResult1").isEqualTo("value") },
                    { assertThat(keyResult2).`as`("keyResult2").isEqualTo("value") },
                    { assertThat(outKeyResult1).`as`("outKeyResult1").isEqualTo("outVal") },
                    { assertThat(outKeyResult2).`as`("outKeyResult2").isEqualTo("outVal") },
                    { assertThat(inKeyResult1).`as`("inKeyResult1").isEqualTo("inVal") },
                    { assertThat(inKeyResult2).`as`("inKeyResult2").isEqualTo("inVal") },
                )
            }
        }
        verify(computation, times(1)).invoke("key")
        verify(computation, times(1)).invoke("outKey")
        verify(computation, times(1)).invoke("inKey")
    }

}