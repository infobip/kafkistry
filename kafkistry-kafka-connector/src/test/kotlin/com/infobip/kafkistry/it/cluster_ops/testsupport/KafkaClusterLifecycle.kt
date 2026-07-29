package com.infobip.kafkistry.it.cluster_ops.testsupport

import com.infobip.kafkistry.it.cluster_ops.custom.EmbeddedKafkaKraftCustomBroker
import com.infobip.kafkistry.it.cluster_ops.custom.KafkaKRaftEmbeddedCluster
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import com.infobip.kafkistry.kafka.NodeId
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker
import java.lang.Exception
import com.infobip.kafkistry.shaded.org.springframework.kafka.test.EmbeddedKafkaZKBroker as LegacyEmbeddedKafkaZKBroker

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
            //kafkaCluster is EmbeddedKafkaZKBroker ||
            kafkaCluster is EmbeddedKafkaKraftBroker
    }

    override fun startNode(id: NodeId) {
        when (kafkaCluster) {
            is EmbeddedKafkaKraftCustomBroker -> {
                if (id in kafkaCluster.brokerIds()) kafkaCluster.startBroker(id)
                if (id in kafkaCluster.controllerIds()) kafkaCluster.startController(id)
            }

            is EmbeddedKafkaKraftBroker -> {
                kafkaCluster.cluster?.brokers()[id]?.startup()
                kafkaCluster.cluster?.controllers()[id]?.startup()
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
                kafkaCluster.cluster?.brokers()[id]?.run {
                    shutdown()
                    awaitShutdown()
                }
                kafkaCluster.cluster?.controllers()[id]?.run {
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

class EmbeddedCombinedKraftKafkaClusterLifecycle(
    override val kafkaCluster: KafkaKRaftEmbeddedCluster,
): KafkaClusterLifecycle<KafkaKRaftEmbeddedCluster> {
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
fun KafkaKRaftEmbeddedCluster.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    EmbeddedCombinedKraftKafkaClusterLifecycle(this)
)
fun LegacyEmbeddedKafkaZKBroker.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    this.asEmbeddedKafkaBroker().asTestKafkaLifecycle()
)

fun LegacyEmbeddedKafkaZKBroker.asEmbeddedKafkaBroker(): EmbeddedKafkaBroker = object : EmbeddedKafkaBroker {
    override fun afterPropertiesSet() = this@asEmbeddedKafkaBroker.afterPropertiesSet()

    override fun destroy() = this@asEmbeddedKafkaBroker.destroy()

    override fun kafkaPorts(vararg ports: Int): EmbeddedKafkaBroker = apply {
        this@asEmbeddedKafkaBroker.kafkaPorts(*ports)
    }

    override fun getTopics(): Set<String> = this@asEmbeddedKafkaBroker.topics

    override fun brokerProperties(properties: Map<String, String>): EmbeddedKafkaBroker = apply {
        this@asEmbeddedKafkaBroker.brokerProperties(properties)
    }

    override fun brokerListProperty(brokerListProperty: String): EmbeddedKafkaBroker = apply {
        this@asEmbeddedKafkaBroker.brokerListProperty(brokerListProperty)
    }

    override fun adminTimeout(adminTimeout: Int): EmbeddedKafkaBroker = apply {
        this@asEmbeddedKafkaBroker.adminTimeout(adminTimeout)
    }

    override fun getBrokersAsString(): String = this@asEmbeddedKafkaBroker.brokersAsString

    override fun addTopics(vararg topicsToAdd: String) = error("Unsupported for legacy ZK cluster")
    override fun addTopics(vararg topicsToAdd: NewTopic) = error("Unsupported for legacy ZK cluster")
    override fun addTopicsWithResults(vararg topicsToAdd: NewTopic): Map<String, Exception> = error("Unsupported for legacy ZK cluster")
    override fun addTopicsWithResults(vararg topicsToAdd: String): Map<String, Exception> = error("Unsupported for legacy ZK cluster")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean, vararg topicsToConsume: String) = error("Unsupported for legacy ZK cluster")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, vararg topicsToConsume: String) = error("Unsupported for legacy ZK cluster")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, seekToEnd: Boolean, topic: String) = error("Unsupported for legacy ZK cluster")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, topic: String) = error("Unsupported for legacy ZK cluster")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean) = error("Unsupported for legacy ZK cluster")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>) = error("Unsupported for legacy ZK cluster")
    override fun getPartitionsPerTopic(): Int = this@asEmbeddedKafkaBroker.partitionsPerTopic

}

