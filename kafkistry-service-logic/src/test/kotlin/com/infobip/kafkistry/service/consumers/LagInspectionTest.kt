package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.service.consumers.config.LagInspectProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LagInspectionTest {

    private val inspector = LagInspection(
        LagInspectProperties()
    )

    private fun inspect(
        offsets: IntRange = 0..0,
        offset: Long? = 0,
        rate: Double? = 0.0,
        assigned: Boolean = true,
    ): Lag = inspector.inspectLag(
        PartitionOffsets(begin = offsets.first.toLong(), end = offsets.last.toLong()),
        offset, rate, assigned
    )

    @Test
    fun `test - zero lag - empty`() {
        val lag = inspect(1000..1000, 1000)
        assertThat(lag).isEqualTo(Lag(0, 0.0, LagStatus.NO_LAG))
    }

    @Test
    fun `test - zero lag - non empty`() {
        val lag = inspect(0..1000, 1000)
        assertThat(lag).isEqualTo(Lag(0, 0.0, LagStatus.NO_LAG))
    }

    @Test
    fun `test - non zero no lag`() {
        val lag = inspect(0..1000, 990)
        assertThat(lag).isEqualTo(Lag(10, 1.0, LagStatus.NO_LAG))
    }

    @Test
    fun `test - has lag - low speed`() {
        val lag = inspect(0..1_000_000, 980_000, 0.5)
        assertThat(lag).isEqualTo(Lag(20_000, 2.0, LagStatus.HAS_LAG))
    }

    @Test
    fun `test - small lag - has lag - low speed`() {
        val lag = inspect(0..50_000, 40_000, 2.0)
        assertThat(lag).isEqualTo(Lag(10_000, 20.0, LagStatus.HAS_LAG))
    }

    @Test
    fun `test - small lag - minor lag - high speed`() {
        val lag = inspect(0..50_000, 40_000, 2000.0)
        assertThat(lag).isEqualTo(Lag(10_000, 20.0, LagStatus.MINOR_LAG))
    }

    @Test
    fun `test - minor lag`() {
        val lag = inspect(0..1000, 900)
        assertThat(lag).isEqualTo(Lag(100, 10.0, LagStatus.MINOR_LAG))
    }

    @Test
    fun `test - has lag`() {
        val lag = inspect(0..100_000, 50_000)
        assertThat(lag).isEqualTo(Lag(50_000, 50.0, LagStatus.HAS_LAG))
    }

    @Test
    fun `test - has lag - maximum valid`() {
        val lag = inspect(0..100_000, 0)
        assertThat(lag).isEqualTo(Lag(100_000, 100.0, LagStatus.HAS_LAG))
    }

    @Test
    fun `test - overflow lag`() {
        val lag = inspect(20_000..100_000, 0)
        assertThat(lag).isEqualTo(Lag(100_000, 125.0, LagStatus.OVERFLOW))
    }

    @Test
    fun `test - unknown lag`() {
        val lag = inspect(20_000..100_000, null)
        assertThat(lag).isEqualTo(Lag(null, null, LagStatus.UNKNOWN))
    }
}