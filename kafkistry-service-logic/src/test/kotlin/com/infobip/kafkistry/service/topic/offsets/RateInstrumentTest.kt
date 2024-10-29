package com.infobip.kafkistry.service.topic.offsets

import io.kotlintest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RateInstrumentTest {

    @Test
    fun `test empty`() {
        val instrument = RateInstrument(10)
        instrument.rate(1) shouldBe null
        instrument.rate(4) shouldBe null
        instrument.rate(10) shouldBe null
    }

    @Test
    fun `test out of range too big`() {
        assertThrows<IllegalArgumentException> {
            val instrument = RateInstrument(10)
            instrument.rate(15)
        }
    }

    @Test
    fun `test out of range too small`() {
        assertThrows<IllegalArgumentException> {
            val instrument = RateInstrument(10)
            instrument.rate(0)
        }
    }


    @Test
    fun `test one sample`() {
        val instrument = RateInstrument(10)
        instrument.observe(123, 1)
        instrument.rate(1) shouldBe null
        instrument.rate(4) shouldBe null
        instrument.rate(10) shouldBe null
        instrument.rate() shouldBe null
    }

    @Test
    fun `test two samples`() {
        val instrument = RateInstrument(10)
        instrument.observe(1, 1)
        instrument.observe(43, 2)
        instrument.rate(1) shouldBe 42.0
        instrument.rate(4) shouldBe null
        instrument.rate(10) shouldBe null
        instrument.rate() shouldBe 42.0
    }

    @Test
    fun `test all samples`() {
        val instrument = RateInstrument(5)
        instrument.observe(0, 1)
        instrument.observe(1, 2)
        instrument.observe(2, 3)
        instrument.observe(4, 4)
        instrument.observe(8, 5)
        instrument.observe(16, 6)
        instrument.rate(1) shouldBe 8.0/1
        instrument.rate(2) shouldBe 12.0/2
        instrument.rate(3) shouldBe 14.0/3
        instrument.rate(4) shouldBe 15.0/4
        instrument.rate(5) shouldBe 16.0/5
        instrument.rate() shouldBe 16.0/5
    }

}