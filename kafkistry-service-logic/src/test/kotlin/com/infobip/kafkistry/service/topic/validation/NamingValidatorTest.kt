package com.infobip.kafkistry.service.topic.validation

import io.kotlintest.matchers.fail
import com.infobip.kafkistry.service.KafkistryValidationException
import org.junit.jupiter.api.Test

class NamingValidatorTest {

    private val validator = NamingValidator()

    @Test
    fun `empty not valid`() = "".isNotValid()

    @Test
    fun `having minus valid`() = "message-events".isValid()

    @Test
    fun `having dot valid`() = "KF.R.message-prices".isValid()

    @Test
    fun `having underscore valid`() = "__consumer-offsets".isValid()

    @Test
    fun `having digit valid`() = "dg2-elastic.replay".isValid()

    @Test
    fun `having whitespace invalid`() = "foo bar".isNotValid()

    @Test
    fun `having slavic chars invalid`() = "čćžšđ".isNotValid()

    @Test
    fun `starting with dot invalid`() = ".topic".isNotValid()

    @Test
    fun `ending with dot invalid`() = "topic.".isNotValid()

    @Test
    fun `having comma invalid`() = "foo,bar".isNotValid()

    private fun String.isNotValid() {
        try {
            validator.validateTopicName(this)
            fail("Expected to fail validation for '$this'")
        } catch (_: KafkistryValidationException) {
        }
    }

    private fun String.isValid() {
        validator.validateTopicName(this)
    }
}