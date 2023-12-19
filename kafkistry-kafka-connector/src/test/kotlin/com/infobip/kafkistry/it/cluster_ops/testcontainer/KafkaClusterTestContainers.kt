package com.infobip.kafkistry.it.cluster_ops.testcontainer

import com.google.common.io.Files
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class KafkaClusterContainer(
        private val topics: Collection<NewTopic>,
        private val brokersConfigs: BrokersConfigs,
        startupTimeout: Duration,
        logContainersOutput: Boolean,
) : DockerComposeContainer<KafkaClusterContainer>(
        "kafka",
        createDockerComposeFile(brokersConfigs)
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(KafkaClusterContainer::class.java)
    }

    private val client = AtomicReference<AdminClient>()

    constructor(
        kafkaImage: String,
        clusterSize: Int = 1,
        customBrokersConfig: Map<String, String> = emptyMap(),
        numberOfPartitions: Int = 1,
        startupTimeout: Duration = Duration.ofSeconds(20),
        logContainersOutput: Boolean = true,
        vararg topics: String,
    ) : this(
            topics.map { NewTopic(it, numberOfPartitions, 1) },
            createBrokersConfigs(kafkaImage, customBrokersConfig, clusterSize),
            startupTimeout,
            logContainersOutput,
    )

    init {
        val startupWait = Wait.forListeningPort().withStartupTimeout(startupTimeout)
        withExposedService("zookeeper", brokersConfigs.zkHostPort.port, startupWait)
        if (logContainersOutput) {
            withLogConsumer("zookeeper") {
                log.info("ZOOKEEPER - {}: {}", it.type, it.utf8String.removeSuffix("\n"))
            }
        }
        //withLocalCompose(false)
        brokersConfigs.hostsPorts.forEachIndexed { index, hostPort ->
            val serviceName = "kafka_${index}_1"
            withExposedService(serviceName, hostPort.port, startupWait)
            if (logContainersOutput) {
                withLogConsumer(serviceName) {
                    log.info("{} - {}: {}", serviceName.uppercase(), it.type, it.utf8String.removeSuffix("\n"))
                }
            }
        }
    }

    override fun start() {
        super.start()
        // create kafka topics
        val client = AdminClient.create(Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBrokersUrl())
        }).also { client.set(it) }
        val topicNames = client.listTopics().names().get(15, TimeUnit.SECONDS)
        val topics = this.topics.filter { topic -> !topicNames.contains(topic.name()) }
        if (topics.isNotEmpty()) {
            val result = client.createTopics(topics)
            result.all().get(5, TimeUnit.SECONDS)
        }
    }

    override fun stop() {
        this.client.getAndSet(null)?.close()
        // to have some time to stop kafka related components before kafka brokers
        TimeUnit.SECONDS.sleep(5)
        super.stop()
    }

    fun getBrokersUrl(): String = brokersConfigs.hostsPorts.joinToString(",") { "${it.host}:${it.port}" }

}

fun createDockerComposeFile(configs: BrokersConfigs): File {
    val dir = File("tmp", "kafka-docker-compose").also {
        it.mkdirs()
    }
    return File.createTempFile("compose-", ".yml", dir)
        .also { file ->
            Files.asCharSink(file, Charsets.UTF_8).write(
                createDockerComposeFileContent(configs).also {
                    KafkaClusterContainer.log.info("Generated docker compose file content\n{}", it)
                }
            )
            KafkaClusterContainer.log.info("Created docker compose file with name ${file.absolutePath}")
        }
}

private fun createDockerComposeFileContent(configs: BrokersConfigs) = """
version: '2'
services:
  zookeeper:
    image: bitnami/zookeeper:3.8.3
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
      - KAFKA_ZOOKEEPER_CONNECT=${brokersConfigs.zkHostPort.host}:${brokersConfigs.zkHostPort.port}
      - KAFKA_ADVERTISED_LISTENERS=INSIDE://:9094,OUTSIDE://${hostPort.host}:${hostPort.port}
      - KAFKA_LISTENERS=INSIDE://:9094,OUTSIDE://:${hostPort.port}
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT
      - KAFKA_INTER_BROKER_LISTENER_NAME=INSIDE
      - ALLOW_PLAINTEXT_LISTENER=true
${brokersConfigs.toYamlEnvironment()}
    
"""

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

    fun toYamlEnvironment(): String = customConfig
        .mapKeys { it.key.replace(".", "_") }
        .mapKeys { it.key.uppercase(Locale.getDefault()) }
        .mapKeys { "      - KAFKA_${it.key}" }
        .map { "${it.key}=${it.value}" }
        .joinToString("\n")

}

