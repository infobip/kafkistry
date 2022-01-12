package com.infobip.kafkistry.service.consume

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.consume.deserialize.*
import com.infobip.kafkistry.service.consume.interntopics.InternalTopicsValueReader
import com.infobip.kafkistry.model.TopicName
import org.junit.jupiter.api.Test
import java.util.*
import org.apache.kafka.common.header.internals.RecordHeader as KafkaRecordHeader

class RecordFactoryTest {

    companion object {
        val factory = RecordFactory(
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
            )
        )
        val creator = factory.creatorFor(RecordDeserialization.ANY)
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
        assertThat(record.value.deserializations)
            .containsOnlyKeys("JSON")
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
            0,
            binKey,
            binValue,
            RecordHeaders(binHeaders.map { KafkaRecordHeader(it.key, it.value) }),
        )
    }
}