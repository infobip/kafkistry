package com.infobip.kafkistry.it.cluster_ops.testsupport

import com.infobip.kafkistry.it.cluster_ops.custom.EmbeddedKafkaKraftCustomBroker
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import com.infobip.kafkistry.kafka.NodeId
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker
import org.springframework.kafka.test.EmbeddedKafkaZKBroker

interface KafkaClusterLifecycle<T> {
    val kafkaCluster: T
    fun start()
    fun stop()

    fun supportsNodeStartStop(): Boolean = false

    fun startNode(id: NodeId) {
        throw UnsupportedOperationException()
    }
    fun stopNode(id: NodeId) {
        throw UnsupportedOperationException()
    }
}

class EmbeddedKafkaClusterLifecycle<T : EmbeddedKafkaBroker>(
    override val kafkaCluster: T
) : KafkaClusterLifecycle<T> {
    override fun start() = kafkaCluster.afterPropertiesSet()
    override fun stop() = kafkaCluster.destroy()

    override fun supportsNodeStartStop(): Boolean {
        return kafkaCluster is EmbeddedKafkaKraftCustomBroker ||
            kafkaCluster is EmbeddedKafkaZKBroker ||
            kafkaCluster is EmbeddedKafkaKraftBroker
    }

    override fun startNode(id: NodeId) {
        when (kafkaCluster) {
            is EmbeddedKafkaKraftCustomBroker -> {
                if (id in kafkaCluster.brokerIds()) kafkaCluster.startBroker(id)
                if (id in kafkaCluster.controllerIds()) kafkaCluster.startController(id)
            }

            is EmbeddedKafkaKraftBroker -> {
                kafkaCluster.cluster.brokers()[id]?.startup()
                kafkaCluster.cluster.controllers()[id]?.startup()
            }

            is EmbeddedKafkaZKBroker -> {
                kafkaCluster.getKafkaServer(id).startup()
            }

            else -> {
                super.startNode(id)
            }
        }
    }

    override fun stopNode(id: NodeId) {
        when (kafkaCluster) {
            is EmbeddedKafkaKraftCustomBroker -> {
                if (id in kafkaCluster.brokerIds()) kafkaCluster.shutdownBroker(id)
                if (id in kafkaCluster.controllerIds()) kafkaCluster.shutdownController(id)
            }

            is EmbeddedKafkaKraftBroker -> {
                kafkaCluster.cluster.brokers()[id]?.run {
                    shutdown()
                    awaitShutdown()
                }
                kafkaCluster.cluster.controllers()[id]?.run {
                    shutdown()
                    awaitShutdown()
                }
            }

            is EmbeddedKafkaZKBroker -> {
                kafkaCluster.getKafkaServer(id).run {
                    shutdown()
                    awaitShutdown()
                }
            }

            else -> {
                super.stopNode(id)
            }
        }
    }
}

class TestContainerKafkaClusterLifecycle(
    override val kafkaCluster: KafkaClusterContainer
) : KafkaClusterLifecycle<KafkaClusterContainer> {
    override fun start() = kafkaCluster.start()
    override fun stop() = kafkaCluster.stop()
}

class LoggingKafkaClusterLifeCycle<T>(
    private val delegate: KafkaClusterLifecycle<T>
) : KafkaClusterLifecycle<T> by delegate {

    private val log = LoggerFactory.getLogger(delegate.javaClass)

    override fun start() {
        log.info("Starting cluster $delegate...")
        delegate.start()
        log.info("Started cluster $delegate")
    }

    override fun stop() {
        log.info("Stopping cluster $delegate...")
        delegate.stop()
        log.info("Stopped cluster $delegate")
    }

    override fun startNode(id: NodeId) {
        log.info("Starting cluster's node $id on $delegate...")
        delegate.startNode(id)
        log.info("Started cluster's node $id on $delegate")
    }

    override fun stopNode(id: NodeId) {
        log.info("Stopping cluster's node $id on $delegate...")
        delegate.stopNode(id)
        log.info("Stopped cluster's node $id on $delegate")
    }
}

fun <T : EmbeddedKafkaBroker> T.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    EmbeddedKafkaClusterLifecycle(this)
)
fun KafkaClusterContainer.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    TestContainerKafkaClusterLifecycle(this)
)

