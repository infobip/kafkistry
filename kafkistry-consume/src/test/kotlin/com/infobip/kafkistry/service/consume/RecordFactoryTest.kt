package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.model.ClusterRef
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.consume.deserialize.*
import com.infobip.kafkistry.service.consume.interntopics.InternalTopicsValueReader
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryPermissionException
import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import com.infobip.kafkistry.service.consume.masking.RecordMaskerFactory
import com.infobip.kafkistry.service.consume.masking.RecordMaskingRuleProvider
import com.infobip.kafkistry.service.consume.masking.TopicMaskingSpec
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.groups.Tuple
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import org.apache.kafka.common.header.internals.RecordHeader as KafkaRecordHeader

class RecordFactoryTest {

    companion object {
        val maskingProvider = mock<RecordMaskingRuleProvider>().also {
            whenever(it.maskingSpecFor(any(), any())).thenReturn(emptyList())
        }
        val factory = RecordFactory(
            DeserializeResolver(
                availableDeserializers = listOf(
                    IntKafkaDeserializer(),
                    LongKafkaDeserializer(),
                    FloatKafkaDeserializer(),
                    DoubleKafkaDeserializer(),
                    ShortKafkaDeserializer(),
                    StringKafkaDeserializer(),
                    JsonKafkaDeserializer(),
                    ConsumerOffsetDeserializer(InternalTopicsValueReader()),
                    TransactionStateDeserializer(InternalTopicsValueReader()),
                    CsvTestDeserializer(),
                ),
                selectors = listOf(),
            ),
            recordMaskerFactory = RecordMaskerFactory(listOf(maskingProvider), JsonPathParser()),
        )
        val creator = factory.creatorFor("", ClusterRef(""), RecordDeserialization.ANY)
    }

    @Test
    fun `test null key`() {
        val record = creator.create(record(key = null))
        assertThat(record.key).isEqualTo(KafkaValue.NULL)
    }

    @Test
    fun `test null value`() {
        val record = creator.create(record(value = null))
        assertThat(record.value).isEqualTo(KafkaValue.NULL)
    }

    @Test
    fun `test empty key`() {
        val record = creator.create(record(key = ""))
        assertThat(record.key).isEqualTo(KafkaValue.EMPTY)
    }

    @Test
    fun `test binary value`() {
        val bytes = byteArrayOf(0x02, 0x02, 0x05, 0x07, 0x0b)
        val record = creator.create(record(binValue = bytes))
        assertThat(record.value).isEqualTo(
            KafkaValue(
                rawBase64Bytes = Base64.getEncoder().encodeToString(bytes), deserializations = emptyMap()
            )
        )
    }

    @Test
    fun `valid json parse`() {
        val record = creator.create(record(value = """{"key":123}"""))
        assertThat(record.value.deserializations["JSON"]?.asJson).isEqualTo("""{"key":123}""")
        assertThat(record.value.deserializations["JSON"]?.value).isEqualTo(mapOf("key" to 123))
    }

    @Test
    fun `invalid json parse`() {
        val record = creator.create(record(value = "3 - 123"))
        assertThat(record.value.deserializations["STRING"]?.asJson).isEqualTo("\"3 - 123\"")
        assertThat(record.value.deserializations["STRING"]?.value).isEqualTo("3 - 123")
        assertThat(record.value.deserializations["JSON"]?.asJson).isNull()
        assertThat(record.value.deserializations["JSON"]?.value).isNull()
    }

    @Test
    fun `test suppress more generic deserization JSON vs STRING`() {
        val record = creator.create(record(value = """{"k":"v"}"""))
        assertThat(record.value.deserializations.values)
            .extracting({ it.typeTag }, { it.isSuppressed })
            .containsExactlyInAnyOrder(
                tuple("JSON", false),
                tuple("STRING", true),
            )
    }

    @Test
    fun `test detect string uniquely`() {
        val record = creator.create(record(value = """just a string"""))
        assertThat(record.value.deserializations).containsOnlyKeys("STRING")
    }

    @Test
    fun `test detect int or float`() {
        val record = creator.create(record(binValue = byteArrayOf(0x00, 0x00, 0x00, 0x1C)))
        assertThat(record.value.deserializations).containsOnlyKeys("INT", "FLOAT")
    }

    @Nested
    inner class MaskingValueTest {

        private val stringDsr = RecordDeserialization(keyType = null, valueType = "STRING", headersType = null)

        private fun maskingCreator(
            deserialization: RecordDeserialization = RecordDeserialization.ANY
        ): RecordFactory.Creator {
            whenever(maskingProvider.maskingSpecFor("t", ClusterRef("c"))).thenReturn(
                listOf(
                    TopicMaskingSpec(
                        valuePathDefs = setOf("secret"), keyPathDefs = emptySet(), headerPathDefs = emptyMap(),
                    )
                )
            )
            return factory.creatorFor("t", ClusterRef("c"), deserialization)
        }

        @Test
        fun `masking json value field`() {
            val record = maskingCreator().create(record(value = """{"id":1122,"secret":"plaintext"}"""))
            assertThat(record.value.isMasked).isTrue
            assertThat(record.value.deserializations["JSON"]?.asJson).isEqualTo("""{"id":1122,"secret":"***MASKED***"}""")
            assertThat(record.value.deserializations["JSON"]?.asFilterable).isEqualTo(
                mapOf("id" to 1122, "secret" to "***MASKED***")
            )
            assertThat(record.value.deserializations["STRING"]).isNull()
            assertThat(record.value.rawBase64Bytes).isNull()
        }

        @Test
        fun `masking json value field - string deserialization`() {
            assertThrows<KafkistryPermissionException> {
                maskingCreator(stringDsr).create(record(value = """{"id":1122,"secret":"plaintext"}"""))
            }
        }

        @Test
        fun `masking non-json value field`() {
            val record = maskingCreator().create(record(value = "this is not a json"))
            assertThat(record.value.isMasked).isFalse
            assertThat(record.value.deserializations["JSON"]).isNull()
            assertThat(record.value.deserializations["STRING"]?.asFilterable).isEqualTo("this is not a json")
            assertThat(record.value.rawBase64Bytes).isNotNull
        }

        @Test
        fun `masking non-json value field - string deserialization`() {
            assertThrows<KafkistryPermissionException> {
                maskingCreator(stringDsr).create(record(value = "this is not a json"))
            }
        }

    }

    @Nested
    inner class TransformedValueTests {

        @Test
        fun `transforming csv value`() {
            val record = creator.create(record(value = "foo,bar,baz", topic = CsvTestDeserializer.TOPIC_NAME))
            assertThat(record.value.deserializations["CSV"]?.asJson).isEqualTo("""["foo","bar","baz"]""")
            assertThat(record.value.deserializations["CSV"]?.asFilterable).isEqualTo(
                listOf("foo", "bar", "baz")
            )
            assertThat(record.value.deserializations["STRING"]?.isSuppressed).isTrue()
        }

    }

    private fun record(
        topic: TopicName = "test-topic",
        key: String? = null,
        binKey: ByteArray? = key?.encodeToByteArray(),
        value: String? = null,
        binValue: ByteArray? = value?.encodeToByteArray(),
        headers: Map<String, String?> = emptyMap(),
        binHeaders: Map<String, ByteArray?> = headers.mapValues { it.value?.encodeToByteArray() },
    ): ConsumerRecord<ByteArray, ByteArray> {
        return ConsumerRecord(
            topic,
            0,
            0,
            -1,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            binKey,
            binValue,
            RecordHeaders(binHeaders.map { KafkaRecordHeader(it.key, it.value) }),
            Optional.empty(),
        )
    }
}