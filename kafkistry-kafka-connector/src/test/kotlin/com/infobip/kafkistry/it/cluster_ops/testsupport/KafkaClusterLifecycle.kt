package com.infobip.kafkistry.it.cluster_ops.testsupport

import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker

interface KafkaClusterLifecycle<T> {
    val kafkaCluster: T
    fun start()
    fun stop()
}

class EmbeddedKafkaClusterLifecycle(
    override val kafkaCluster: EmbeddedKafkaBroker
) : KafkaClusterLifecycle<EmbeddedKafkaBroker> {
    override fun start() = kafkaCluster.afterPropertiesSet()
    override fun stop() = kafkaCluster.destroy()
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
}

fun EmbeddedKafkaBroker.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    EmbeddedKafkaClusterLifecycle(this)
)
fun KafkaClusterContainer.asTestKafkaLifecycle() = LoggingKafkaClusterLifeCycle(
    TestContainerKafkaClusterLifecycle(this)
)

