package com.infobip.kafkistry.service.consume

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UnmaskedRecordReadRequestTest {

    private fun req(reason: String) = UnmaskedRecordReadRequest(
        partition = 0,
        offset = 0,
        reason = reason,
        recordDeserialization = RecordDeserialization.ANY,
    )

    @Test
    fun `accepts reason at minimum length`() {
        val request = req("12345")
        assertThat(request.reason).isEqualTo("12345")
    }

    @Test
    fun `rejects empty reason`() {
        assertThrows<IllegalArgumentException> { req("") }
    }

    @Test
    fun `rejects reason shorter than minimum after trimming`() {
        assertThrows<IllegalArgumentException> { req("  abc  ") }
    }

    @Test
    fun `rejects reason longer than maximum`() {
        assertThrows<IllegalArgumentException> {
            req("x".repeat(UnmaskedRecordReadRequest.REASON_MAX_LENGTH + 1))
        }
    }

    @Test
    fun `accepts reason at maximum length`() {
        val maxLength = UnmaskedRecordReadRequest.REASON_MAX_LENGTH
        val request = req("y".repeat(maxLength))
        assertThat(request.reason.length).isEqualTo(maxLength)
    }
}
