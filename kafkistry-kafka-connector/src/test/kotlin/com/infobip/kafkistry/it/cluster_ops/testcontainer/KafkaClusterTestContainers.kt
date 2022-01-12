package com.infobip.kafkistry.it.cluster_ops.testcontainer

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.io.Files
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.awaitility.Awaitility
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.SocketUtils
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.net.InetAddress
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class KafkaClusterContainer(
        private val topics: Collection<NewTopic>,
        private val brokersConfigs: BrokersConfigs
) : DockerComposeContainer<KafkaClusterContainer>(
        "kafka",
        createDockerComposeFile(brokersConfigs)
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(KafkaClusterContainer::class.java)
    }

    private val client = AtomicReference<AdminClient>()
    private val objectMapper = ObjectMapper()

    constructor(
            kafkaImage: String = "wurstmeister/kafka:latest",
            clusterSize: Int = 1,
            customBrokersConfig: Map<String, String> = emptyMap(),
            numberOfPartitions: Int = 1,
            vararg topics: String

    ) : this(
            topics.map { NewTopic(it, numberOfPartitions, 1) },
            createBrokersConfigs(kafkaImage, customBrokersConfig, clusterSize)
    )

    init {
        withExposedService("zookeeper", brokersConfigs.zkHostPort.port)
                .waitingFor("zookeeper", Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)))
        brokersConfigs.hostsPorts.forEachIndexed { index, hostPort ->
            val serviceName = "kafka_${index}_1"
            withExposedService(serviceName, hostPort.port)
                    .waitingFor(serviceName, Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)))
        }
    }

    override fun start() {
        super.start()
        // create kafka topics
        val client = AdminClient.create(Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBrokersUrl())
        })
        val topicNames = client.listTopics().names().get(1, TimeUnit.SECONDS)
        val topics = this.topics.filter { topic -> !topicNames.contains(topic.name()) }
        if (topics.isNotEmpty()) {
            val result = client.createTopics(topics)
            result.all().get(5, TimeUnit.SECONDS)
        }
        this.client.set(client)
    }

    override fun stop() {
        this.client.getAndSet(null)?.close()
        // to have some time to stop kafka related components before kafka brokers
        TimeUnit.SECONDS.sleep(5)
        super.stop()
    }

    fun getBrokersUrl(): String = brokersConfigs.hostsPorts.joinToString(",") { "${it.host}:${it.port}" }

    fun <K, V> sendMessages(
            topic: String,
            numberOfMessages: Int = 1,
            delay: Long = 0,
            producerConfigs: ProducerConfigs = ProducerConfigs(getBrokersUrl()),
            generateKey: (Int) -> K? = { null },
            generateMessage: (Int) -> V
    ) {
        if (topics.find { it.name() == topic } == null) {
            log.error("Topic $topic is not in the list")
            return
        }
        KafkaProducer<ByteArray, ByteArray>(producerConfigs.value()).use { producer ->
            sendMessages(numberOfMessages) { value, latch ->
                if (delay > 0) {
                    Thread.sleep(delay)
                }
                val record = ProducerRecord(
                        topic,
                        objectMapper.writeValueAsBytes(generateKey(value)),
                        objectMapper.writeValueAsBytes(generateMessage(value))
                )
                producer.send(record) { _, ex ->
                    if (ex != null) {
                        log.error("Error while sending ", ex)
                    }
                    latch.countDown()
                }
            }.await(10, TimeUnit.SECONDS)
        }

    }

    private fun sendMessages(numberOfMessages: Int, sendMessage: (Int, CountDownLatch) -> Unit) = CountDownLatch(numberOfMessages).also { latch ->
        repeat(numberOfMessages) {
            sendMessage(it, latch)
        }
    }

    fun <K, V> getConsumer(
            topic: String,
            consumerConfigs: ConsumerConfigs = ConsumerConfigs(getBrokersUrl()),
            handle: (Consumer<K, V>) -> Unit) {
        val description = topics.find { it.name() == topic }
        if (description == null) {
            log.error("Topic $topic is not in the list")
            return
        }
        val checker = ConsumerAssignmentChecker()
        KafkaConsumer<K, V>(consumerConfigs.value()).use { consumer ->
            consumer.subscribe(listOf(topic), checker)
            consumer.poll(Duration.ZERO)
            checker.waitForAssignment(listOf(topic), description.numPartitions())
            handle(consumer)
        }
    }

    fun <K, V> readMessages(consumer: Consumer<K, V>, timeout: Long = 5000, handle: (ConsumerRecords<K, V>) -> Boolean) {
        while (!handle(consumer.poll(Duration.ofMillis(timeout)))) {
        }
    }

}

fun createDockerComposeFile(configs: BrokersConfigs): File = File.createTempFile("kafka-docker-compose-", ".yml")
        .also { file ->
            Files.asCharSink(file, Charsets.UTF_8).write(
                    createDockerComposeFileContent(configs).also {
                        KafkaClusterContainer.log.info("Generated docker compose file content\n{}", it)
                    }
            )
            println("")
            println("Created docker compose file with name ${file.absolutePath}")
        }

private fun createDockerComposeFileContent(configs: BrokersConfigs) = """
version: '2'
services:
  zookeeper:
    image: bitnami/zookeeper:3.6.0
    ports:
      - "${configs.zkHostPort.port}:${configs.zkHostPort.port}"
    environment:
      - ZOO_PORT_NUMBER=${configs.zkHostPort.port}
      - ALLOW_ANONYMOUS_LOGIN=yes
      
""" + configs.hostsPorts.mapIndexed { brokerIndex, hostPort ->
    createBrokerFileContent(brokerIndex, hostPort, configs)
}.joinToString("\n")


private fun createBrokerFileContent(
        brokerIndex: Int,
        hostPort: HostPort,
        brokersConfigs: BrokersConfigs
) = """
  kafka_$brokerIndex:
    image: ${brokersConfigs.kafkaImage}
    ports:
      - "${hostPort.port}:${hostPort.port}"   # kafka port
    depends_on:
      - zookeeper
    environment:
      - KAFKA_BROKER_ID=$brokerIndex
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:${brokersConfigs.zkHostPort.port},${brokersConfigs.zkHostPort.host}:${brokersConfigs.zkHostPort.port}
      - KAFKA_ADVERTISED_LISTENERS=INSIDE://:9094,OUTSIDE://${hostPort.host}:${hostPort.port}
      - KAFKA_LISTENERS=INSIDE://:9094,OUTSIDE://:${hostPort.port}
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT
      - KAFKA_INTER_BROKER_LISTENER_NAME=INSIDE
      - ALLOW_PLAINTEXT_LISTENER=true
${brokersConfigs.toYamlEnvironment()}
    
"""

class ConsumerAssignmentChecker(
        private val prototype: ConsumerRebalanceListener? = null
) : ConsumerRebalanceListener {

    private val store = ConcurrentHashMap<String, AtomicInteger>()

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        partitions.forEach { tp ->
            store.computeIfAbsent(tp.topic()) { AtomicInteger() }.getAndIncrement()
        }
        prototype?.onPartitionsAssigned(partitions)
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        partitions.forEach { tp ->
            store[tp.topic()]?.getAndDecrement()
        }
        prototype?.onPartitionsRevoked(partitions)
    }

    fun waitForAssignment(topics: List<String>, expectedNumberOfPartitions: Int, timeToWait: Long = 5) {
        topics.forEach { topic ->
            Awaitility.await().atMost(timeToWait, TimeUnit.SECONDS).until {
                (store[topic]?.get() ?: 0) == expectedNumberOfPartitions
            }
        }
    }


}

private fun createBrokersConfigs(
        kafkaImage: String,
        customConfig: Map<String, String>,
        numHosts: Int
): BrokersConfigs {
    return BrokersConfigs(
            kafkaImage = kafkaImage,
            zkHostPort = HostPort.newLocalAvailable(),
            hostsPorts = (1..numHosts).map { HostPort.newLocalAvailable() },
            customConfig = customConfig
    )
}

class BrokersConfigs(
        val kafkaImage: String,
        val zkHostPort: HostPort,
        val hostsPorts: List<HostPort>,
        val customConfig: Map<String, String> = emptyMap()
) {

    private val defaultConfig = mapOf(
            "num.network.threads" to "3",
            "num.io.threads" to "8",
            "socket.send.buffer.bytes" to "102400",
            "socket.receive.buffer.bytes" to "102400",
            "socket.request.max.bytes" to "104857600",
            "log.dirs" to "/tmp/kafka-logs",
            "num.partitions" to "4",
            "num.recovery.threads.per.data.dir" to "1",
            "log.retention.hours" to "168",
            "log.segment.bytes" to "1073741824",
            "log.retention.check.interval.ms" to "300000",
            "zookeeper.connection.timeout.ms" to "6000"
    )

    fun toYamlEnvironment(): String = defaultConfig.plus(customConfig)
            .mapKeys { it.key.replace(".", "_") }
            .mapKeys { it.key.uppercase(Locale.getDefault()) }
            .mapKeys { "      - KAFKA_${it.key}" }
            .map { "${it.key}=${it.value}" }
            .joinToString("\n")

}

data class HostPort(val host: String, val port: Int) {
    companion object {
        fun newLocalAvailable() = HostPort(
                host = InetAddress.getLocalHost().hostName,
                port = SocketUtils.findAvailableTcpPort()
        )
    }
}

class ProducerConfigs(
        val brokerUrl: String,
        val customConfig: Map<String, Any> = emptyMap()
) {

    private val defaultConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to brokerUrl,
            ProducerConfig.RETRIES_CONFIG to 0,
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,
            ProducerConfig.LINGER_MS_CONFIG to 1,
            ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java
    )

    fun value(): Map<String, Any> = defaultConfig.plus(customConfig)
}

class ConsumerConfigs(
        val brokerUrl: String,
        val groupId: String = "test_group",
        val customConfig: Map<String, Any> = emptyMap()
) {

    private val defaultConfig = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokerUrl,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java
    )

    fun value() = defaultConfig.plus(customConfig)
}