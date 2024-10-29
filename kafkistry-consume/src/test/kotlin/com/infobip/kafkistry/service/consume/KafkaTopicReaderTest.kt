package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.kafka.ClientFactory
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSamplerFactory
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.service.KafkistryConsumeException
import com.infobip.kafkistry.service.consume.config.ConsumeProperties
import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import com.infobip.kafkistry.service.consume.filter.MatcherFactory
import com.infobip.kafkistry.service.consume.filter.RecordFilterFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.groups.Tuple
import org.awaitility.Awaitility
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker
import org.springframework.kafka.test.EmbeddedKafkaZKBroker
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("ConstPropertyName")
class KafkaTopicReaderTest {

    companion object {

        const val topic1 = "test-consume"
        const val topic2 = "test-consume2"
        const val topic3 = "test-consume3"
        const val topic4 = "test-consume4"
        const val topic5 = "test-consume5"
        const val topic6 = "test-consume6"
        const val topic7 = "test-consume7"
        const val topic8 = "test-consume8"

        private val kafka = EmbeddedKafkaKraftBroker(
            3, 2,
            topic1, topic2, topic3, topic4, topic5, topic6, topic7, topic8,
        )

        @BeforeAll
        @JvmStatic
        fun startKafka() = kafka.afterPropertiesSet().also {
            kafka.doWithAdmin { admin ->
                await("for all topics to get created")
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted {
                        val topics = admin.listTopics().names().get()
                        assertThat(topics).contains(topic1, topic2, topic3, topic4, topic5, topic6, topic7, topic8)
                        assertThat(admin.describeTopics(topics).allTopicNames().get()).containsKeys(
                            topic1, topic2, topic3, topic4, topic5, topic6, topic7, topic8
                        )
                    }
            }
        }

        @AfterAll
        @JvmStatic
        fun stopKafka() = kafka.destroy()
    }

    private val producer: KafkaProducer<String, ByteArray> = KafkaProducer(
        Properties().also { props ->
            props["bootstrap.servers"] = kafka.brokersAsString
            props["acks"] = "all"
            props["retries"] = 0
            props["batch.size"] = 16384
            props["linger.ms"] = 1
            props["buffer.memory"] = 33554432
        },
        StringSerializer(),
        ByteArraySerializer(),
    )

    private val consumerService = KafkaTopicReader(
            consumeProperties = ConsumeProperties().apply {
                maxRecords = 100
                maxWaitMs = 10_000
                poolInterval = 1_000
                poolBatchSize = 100
            },
            recordFactory = RecordFactoryTest.factory,
            filterFactory = RecordFilterFactory(MatcherFactory(JsonPathParser())),
            clientFactory = ClientFactory(
                    properties = KafkaManagementClientProperties(),
                    RecordReadSamplerFactory(),
                    Optional.empty(),
                    Optional.empty(),
            )
    )

    private lateinit var kafkaCluster: KafkaCluster

    private fun EmbeddedKafkaBroker.clusterId(): String {
        return when (this) {
            is EmbeddedKafkaZKBroker -> this.kafkaServers[0].clusterId()
            is EmbeddedKafkaKraftBroker -> this.cluster.nodes().clusterId().toString()
            else -> throw UnsupportedOperationException("Can't extract cluster id of $this, unknown implementation")
        }
    }

    @BeforeEach
    fun init() {
        kafkaCluster = KafkaCluster(
                identifier = "test",
                connectionString = kafka.brokersAsString,
                clusterId = kafka.clusterId(),
                sslEnabled = false,
                saslEnabled = false,
                tags = emptyList(),
        )
    }

    @AfterEach
    fun tearDown() = producer.close()

    private fun sendMessage(topic: String, msg: String, key: String? = null) {
        producer.send(ProducerRecord(topic, key, msg.toByteArray())).also {
            producer.flush()
        }.get()
    }

    private fun readConfig(
        numRecords: Int = 10,
        partitions: List<Int>? = null,
        notPartitions: List<Int>? = null,
        maxWaitMs: Long = 10_000,
        fromOffset: Offset = Offset(OffsetType.EARLIEST, 0),
        partitionFromOffset: Map<Partition, Long>? = null,
        waitStrategy: WaitStrategy = WaitStrategy.AT_LEAST_ONE,
        readOnlyCommitted: Boolean = false,
        recordDeserialization: RecordDeserialization = RecordDeserialization.ANY,
        readFilter: ReadFilter = ReadFilter.EMPTY,
    ) = ReadConfig(
        numRecords, partitions, notPartitions, maxWaitMs, fromOffset, partitionFromOffset, waitStrategy,
        recordDeserialization, readOnlyCommitted, readFilter
    )

    private fun sendMessages(records: List<KafkaRecord>) {
        records
                .map { it.toProducerRecord() }
                .map { producer.send(it) }
                .also { producer.flush() }
                .forEach { it.get(2, TimeUnit.SECONDS) }
    }

    @Test
    fun `produce and consume`() {
        sendMessage(topic1, "Hello kafka")
        val recordsResult = consumerService.readTopicRecords(
                topic1,
                kafkaCluster,
                "foo",
                readConfig(),
        )
        assertThat(recordsResult.records)
                .hasSize(1)
                .extracting<Any> { it.value.deserializations["STRING"]?.value }
                .containsExactly("Hello kafka")
    }

    @Test
    fun `produce and consume 2`() {
        sendMessages(records(topic2))
        val recordsResult = consumerService.readTopicRecords(
                topic2,
                kafkaCluster,
                "foo",
                readConfig(numRecords = 6, waitStrategy = WaitStrategy.WAIT_NUM_RECORDS),
        )
        assertThat(recordsResult.records).hasSize(6)
    }

    @Test
    fun `consume too big offset`() {
        assertThatThrownBy {
            consumerService.readTopicRecords(
                topic1, kafkaCluster, "foo",
                readConfig(fromOffset = Offset(OffsetType.EXPLICIT, 1000)),
            )
        }.isInstanceOf(KafkistryConsumeException::class.java)
    }

    @Test
    fun `consume from empty topic with latest when user wants more messages than actually exists`() {
        sendMessage(topic3, "Test")
        sendMessage(topic3, "Test")
        sendMessage(topic3, "Test")

        val result = consumerService.readTopicRecords(
                topic3,
                kafkaCluster,
                "foo",
                readConfig(fromOffset = Offset(OffsetType.LATEST, 1000)),
        )
        assertThat(result.records)
                .hasSizeBetween(1, 3)
                .extracting<Pair<String, Any?>> { it.topic to it.value.deserializations["STRING"]?.value }
                .containsOnly(topic3 to "Test")
    }

    @Test
    fun `try consuming topics from future`() {
        assertThatThrownBy {

            sendMessage(topic4, "Test")
            sendMessage(topic4, "Test")
            sendMessage(topic4, "Test")
            consumerService.readTopicRecords(
                topic4, kafkaCluster, "foo",
                readConfig(fromOffset = Offset(OffsetType.TIMESTAMP, System.currentTimeMillis() + 1_000_000_000)),
            )
        }.isInstanceOf(KafkistryConsumeException::class.java)
    }

    @Test
    fun `try consuming latest minus something goes before earliest `() {
        sendMessage(topic6, "Test1", "1")
        sendMessage(topic6, "Test2", "1")
        sendMessage(topic6, "Test3", "1")
        val result = consumerService.readTopicRecords(
                topic6,
                kafkaCluster,
                "foo",
                readConfig(numRecords = 1, fromOffset = Offset(OffsetType.LATEST, 5L)),
        )
        assertThat(result.records)
            .hasSize(1)
            .extracting({ it.offset }, { it.value.deserializations["STRING"]?.value })
            .containsExactly(Tuple.tuple(0L, "Test1"))
    }

    @Test
    fun `try consuming earliest plus something goes after latest`() {
        sendMessage(topic7, "Test1", "1")
        sendMessage(topic7, "Test2", "1")
        sendMessage(topic7, "Test3", "1")
        val result = consumerService.readTopicRecords(
                topic7,
                kafkaCluster,
                "foo",
                readConfig(numRecords = 1, fromOffset = Offset(OffsetType.EARLIEST, 5L), maxWaitMs = 2000),
        )
        assertThat(result.records).isEmpty()
    }

    @Test
    fun `consuming by time, only from some partitions`() {
        sendMessage(topic5, "m1")
        val time = System.currentTimeMillis()
        sendMessage(topic5, "m2")
        val result = consumerService.readTopicRecords(
                topic5,
                kafkaCluster,
                "foo",
                readConfig(fromOffset = Offset(OffsetType.TIMESTAMP, time)),
        )
        assertThat(result.records)
                .hasSize(1)
                .extracting<Pair<String, Any?>> { it.topic to it.value.deserializations["STRING"]?.value }
                .containsOnly(topic5 to "m2")
    }

    @Test
    fun `consuming from per partition explicit offset`() {
        repeat(100) {
            sendMessage(topic8, "m-$it", "k-$it")
        }
        val result = consumerService.readTopicRecords(
                topic8,
                kafkaCluster,
                "foo",
                readConfig(
                    numRecords = 50,
                    waitStrategy = WaitStrategy.WAIT_NUM_RECORDS,
                    fromOffset = Offset(OffsetType.EXPLICIT, 0),
                    partitionFromOffset = mapOf(0 to 11, 1 to 22)
                ),
        )
        val partitionEarliest = result.records.groupBy { it.partition }.mapValues { (_, records) ->
            records.minOfOrNull { it.offset }
        }
        assertThat(partitionEarliest)
            .containsEntry(0, 11)
            .containsEntry(1, 22)
    }

}

private fun String.asValue(): KafkaValue {
    val consumerRecord = ConsumerRecord(
        "---", 0,0, ByteArray(0), this.encodeToByteArray()
    )
    val record = RecordFactoryTest.factory.creatorFor(
        "", ClusterRef(""), RecordDeserialization.ANY
    ).create(consumerRecord)
    return record.value
}

fun KafkaRecord.toProducerRecord(): ProducerRecord<String?, ByteArray> {
    return ProducerRecord(
            topic,
            null,
            null,
            key.toBytesOrNull()?.decodeToString(),
            value.toBytes(),
            headers.map { org.apache.kafka.common.header.internals.RecordHeader(it.key, it.value.toBytesOrNull()) }
    )
}

fun KafkaValue.toBytesOrNull(): ByteArray? = if (isNull || isMasked) null else Base64.getDecoder().decode(rawBase64Bytes)

fun KafkaValue.toBytes(): ByteArray {
    return when {
        rawBase64Bytes != null -> Base64.getDecoder().decode(rawBase64Bytes)
        else -> throw IllegalArgumentException("Null value: $this")
    }
}

fun records(topic: String) = listOf(
        KafkaRecord(
                topic = topic,
                partition = 3,
                offset = 235534424,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = KafkaValue.NULL,
                headers = listOf(
                        RecordHeader("FOO", "bar".asValue()),
                        RecordHeader("_SOME_LONGER_HEADER", "12".asValue()),
                        RecordHeader("PAJO", "jkhbckadsbcvkasdbvkkbkbkbkbkdsbvkbfkbfksdjbvdjbvkdbjvkdvbdkvbdksbvdkjbvd".asValue()),
                        RecordHeader("TYPE", "bar".asValue()),
                        RecordHeader("long", "jbjhb, jbjkb khbk vvkhv kvkvb kv kv k".asValue())
                ),
                value = KafkaValue(
                    rawBase64Bytes = ByteArray(100) { (it % 20).toByte() }.let { Base64.getEncoder().encodeToString(it) },
                    deserializations = emptyMap(),
                ),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
        KafkaRecord(
                topic = topic,
                partition = 3,
                offset = 235534424,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = "123_24325245".asValue(),
                headers = listOf(),
                value = """some random non json string""".asValue(),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
        KafkaRecord(
                topic = topic,
                partition = 3,
                offset = 235534424,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = "123_24325245".asValue(),
                headers = listOf(),
                value = """ "foo bar baz" """.asValue(),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
        KafkaRecord(
                topic = topic,
                partition = 3,
                offset = 235534424,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = "123_24325245".asValue(),
                headers = listOf(),
                value = """35432""".asValue(),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
        KafkaRecord(
                topic = topic,
                partition = 3,
                offset = 235534424,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = "123_24325245".asValue(),
                headers = listOf(RecordHeader("FOO", "bar".asValue())),
                value = """{"x":"y","Time":2343222,"arr":[1233,24,3243]}""".asValue(),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
        KafkaRecord(
                topic = topic,
                partition = 4,
                offset = 5633656,
                leaderEpoch = 0,
                timestamp = System.currentTimeMillis(),
                timestampType = TimestampType.CREATE_TIME,
                key = "453_56363".asValue(),
                headers = listOf(RecordHeader("TYPE", "app/json".asValue())),
                value = """{"SendDateTime":"24.55.43 44:43:3","Time":2343222,"arr":[1233,24,3243]}""".asValue(),
                keySize = 0, valueSize = 0, headersSize = 0,
        ).withSizes(),
)

private fun KafkaRecord.withSizes() = copy(
    keySize = key.rawBase64Bytes.sizeOfBase64(),
    valueSize = value.rawBase64Bytes.sizeOfBase64(),
    headersSize = headers.sumOf { it.key.length + it.value.rawBase64Bytes.sizeOfBase64() },
)

private fun String?.sizeOfBase64() = this?.let { Base64.getDecoder().decode(it).size } ?: 0



