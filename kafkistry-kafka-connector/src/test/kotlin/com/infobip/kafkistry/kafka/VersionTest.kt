package com.infobip.kafkistry.kafka

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

class VersionTest {

    @Test
    fun `parse invalid - no numbers`() = assertThat(Version.parse("x.s.d")).isNull()

    @Test
    fun `parse invalid - empty`() = assertThat(Version.parse("")).isNull()

    @Test
    fun `parse major`() {
        assertThat(Version.parse("2")).isEqualTo(Version(2))
    }

    @Test
    fun `parse minor`() {
        assertThat(Version.parse("1.3")).isEqualTo(Version(1, 3))
    }

    @Test
    fun `parse patch`() {
        assertThat(Version.parse("5.6.7")).isEqualTo(Version(5, 6, 7))
    }

    @Test
    fun `parse with extra text on minor`() {
        assertThat(Version.parse("2.1-IV")).isEqualTo(Version(2, 1))
    }

    @Test
    fun `parse with extra text on patch`() {
        assertThat(Version.parse("3.4.5-IV")).isEqualTo(Version(3, 4, 5))
    }

    @Test
    fun `compare 1`() = "2" checkLessThan "3"

    @Test
    fun `compare 2`() = "2" checkEqual "2"

    @Test
    fun `compare 3`() = "1.3" checkLessThan "2.1"

    @Test
    fun `compare 4`() = "2.2" checkEqual "2.2"

    @Test
    fun `compare 5`() = "2.2" checkLessThan "2.2.1"

    @Test
    fun `compare 6`() = "2.1" checkLessThan "2.1.3"

    @Test
    fun `compare 7`() = "1.3" checkLessThan "2.1-IV"

    @Test
    fun `compare 8`() = "2.5.9" checkEqual  "2a.5-gh.9-rc"

    @Test
    fun `compare shorter`() = "2.2" checkLessThan "2.2.0"

    private infix fun String.checkLessThan(other: String) {
        val v1 = Version.of(this)
        val v2 = Version.of(other)
        if (v1 >= v2) {
            fail<Unit>("Expecting $v1 to be less than $v2")
        }
    }

    private infix fun String.checkEqual(other: String) {
        val v1 = Version.of(this)
        val v2 = Version.of(other)
        if ((v1 compareTo v2) != 0) {
            fail<Unit>("Expecting $v1 to be equal to $v2")
        }
    }
}