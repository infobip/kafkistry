package com.infobip.kafkistry.repository

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.repository.storage.FileNameEncoding
import org.junit.Test


class FileNameEncodingTest {

    private val encoding = FileNameEncoding()
    private val illegalChars = """/\?%*:|"<> """.toCharArray().map { "$it" }.toTypedArray()

    private fun testEncodeAndDecode(string: String) {
        val encoded = encoding.encode(string)
        val decoded = encoding.decode(encoded)
        println("$string -> $encoded")
        assertThat(decoded).`as`("decoded from encoded from input string").isEqualTo(string)
        assertThat(encoded).`as`("does not contain illegal chars").doesNotContain(*illegalChars)
    }

    @Test
    fun `test empty`() = testEncodeAndDecode("")

    @Test
    fun `test word`() = testEncodeAndDecode("foo")

    @Test
    fun `test words`() = testEncodeAndDecode("foo-bar")

    @Test
    fun `test many words different legal separators`() = testEncodeAndDecode("KF.R-simple_topic.name")

    @Test
    fun `test words spaced`() = testEncodeAndDecode("foo bar")

    @Test
    fun `test many illegal`() = testEncodeAndDecode("""/\?%*:|"<> """)
}