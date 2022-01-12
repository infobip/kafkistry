package com.infobip.kafkistry.it.cluster_ops.testsupport

import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
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

fun EmbeddedKafkaBroker.asTestKafkaLifecycle() = EmbeddedKafkaClusterLifecycle(this)
fun KafkaClusterContainer.asTestKafkaLifecycle() = TestContainerKafkaClusterLifecycle(this)

