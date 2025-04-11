package com.infobip.kafkistry.it.cluster_ops.testcontainer

import com.google.common.io.Files
import org.apache.kafka.common.Uuid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration
import java.util.*

class KafkaClusterContainer private constructor(
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

    constructor(
        kafkaImage: String,
        zkImage: String = "bitnami/zookeeper:3.8.3",
        clusterSize: Int = 3,
        customBrokersConfig: Map<String, String> = emptyMap(),
        startupTimeout: Duration = Duration.ofSeconds(40),
        kraft: Boolean = false,
        logContainersOutput: Boolean = false,
    ) : this(
        createBrokersConfigs(kraft, kafkaImage, zkImage, customBrokersConfig, clusterSize),
        startupTimeout,
        logContainersOutput,
    )

    init {
        val startupWait = Wait.forListeningPort().withStartupTimeout(startupTimeout)
        if (!brokersConfigs.kraft) {
            withExposedService("zookeeper", brokersConfigs.zkHostPort.port, startupWait)
            if (logContainersOutput) {
                withLogConsumer("zookeeper") {
                    log.info("ZOOKEEPER - {}: {}", it.type, it.utf8String.removeSuffix("\n"))
                }
            }
        }
        //withLocalCompose(false)
        brokersConfigs.hostsPorts.forEachIndexed { index, hostPort ->
            val serviceName = "kafka_${index}_1"
            withExposedService(serviceName, hostPort.port, startupWait)
            if (brokersConfigs.kraft) {
                withExposedService(serviceName, brokersConfigs.controllerHostsPorts[index].port, startupWait)
            }
            if (logContainersOutput) {
                withLogConsumer(serviceName) {
                    log.info("{} - {}: {}", serviceName.uppercase(), it.type, it.utf8String.removeSuffix("\n"))
                }
            }
        }
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
""" +
    (if (configs.kraft) "" else createZKFileContent(configs.zkImage, configs.zkHostPort)) +
    configs.hostsPorts.indices.joinToString("\n") { brokerIndex ->
        createBrokerFileContent(brokerIndex, configs)
    }

private fun createZKFileContent(zkImage: String, zkHostPort: HostPort): String = """
  zookeeper:
    image: $zkImage
    ports:
      - "${zkHostPort.port}:${zkHostPort.port}"
    environment:
      - ZOO_PORT_NUMBER=${zkHostPort.port}
      - ALLOW_ANONYMOUS_LOGIN=yes
    
"""


private fun createBrokerFileContent(
    brokerIndex: Int,
    brokersConfigs: BrokersConfigs,
    hostPort: HostPort = brokersConfigs.hostsPorts[brokerIndex],
    controllerHostPort: HostPort = brokersConfigs.controllerHostsPorts[brokerIndex],
) = """
  kafka_$brokerIndex:
    image: ${brokersConfigs.kafkaImage}
    hostname: kafka_$brokerIndex
    ports:
      - "${hostPort.port}:${hostPort.port}"   # kafka port
""" +
    (if (brokersConfigs.kraft) """
      - "${controllerHostPort.port}:${controllerHostPort.port}"   # controller port 
    """ else """
    depends_on:
      - zookeeper
""") +
    """
    environment:
      - KAFKA_ADVERTISED_LISTENERS=INSIDE://:9094,OUTSIDE://${hostPort.host}:${hostPort.port}
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INSIDE:PLAINTEXT,OUTSIDE:PLAINTEXT,CONTROLLER:PLAINTEXT
      - KAFKA_INTER_BROKER_LISTENER_NAME=INSIDE
      - ALLOW_PLAINTEXT_LISTENER=true
${brokersConfigs.toYamlEnvironment()}
""" + if (brokersConfigs.kraft)
    """
      - KAFKA_LISTENERS=INSIDE://:9094,OUTSIDE://:${hostPort.port},CONTROLLER://:${controllerHostPort.port}
      - KAFKA_KRAFT_CLUSTER_ID=${Uuid(123456L, 123456L)}
      - KAFKA_CFG_NODE_ID=$brokerIndex
      - KAFKA_CFG_PROCESS_ROLES=controller,broker
      - KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=${brokersConfigs.controllerHostsPorts.withIndex().joinToString(",") { (id, hp) -> "$id@kafka_$id:${hp.port}" }}
      - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
    """
else
    """
      - KAFKA_BROKER_ID=$brokerIndex
      - KAFKA_LISTENERS=INSIDE://:9094,OUTSIDE://:${hostPort.port}
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:${brokersConfigs.zkHostPort.port}
      - KAFKA_ENABLE_KRAFT=no      
    """

private fun createBrokersConfigs(
    kraft: Boolean,
    kafkaImage: String,
    zkImage: String,
    customConfig: Map<String, String>,
    numHosts: Int
): BrokersConfigs {
    return BrokersConfigs(
        kraft = kraft,
        kafkaImage = kafkaImage,
        zkImage = zkImage,
        zkHostPort = HostPort.newLocalAvailable(),
        hostsPorts = (1..numHosts).map { HostPort.newLocalAvailable() },
        controllerHostsPorts = (1..numHosts).map { HostPort.newLocalAvailable() },
        customConfig = customConfig
    )
}

class BrokersConfigs(
    val kraft: Boolean,
    val kafkaImage: String,
    val zkImage: String,
    val zkHostPort: HostPort,
    val hostsPorts: List<HostPort>,
    val controllerHostsPorts: List<HostPort>,
    val customConfig: Map<String, String> = emptyMap()
) {

    fun toYamlEnvironment(): String = customConfig
        .mapKeys { it.key.replace(".", "_") }
        .mapKeys { it.key.uppercase(Locale.getDefault()) }
        .mapKeys { "      - KAFKA_${it.key}" }
        .map { "${it.key}=${it.value}" }
        .joinToString("\n")

}

