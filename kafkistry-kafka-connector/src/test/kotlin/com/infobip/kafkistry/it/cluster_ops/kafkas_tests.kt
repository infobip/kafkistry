@file:Suppress("ClassName")

package com.infobip.kafkistry.it.cluster_ops

import kafka.security.authorizer.AclAuthorizer
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import com.infobip.kafkistry.it.cluster_ops.testsupport.asTestKafkaLifecycle
import com.infobip.kafkistry.kafka.Version
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.kafka.test.EmbeddedKafkaBroker

class ClusterOpsKafkaEmbeddedTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaBroker(3).apply {
            brokerProperty("auto.leader.rebalance.enable", "false")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val expectedClusterVersion = Version.of("3.3")
}

class ClusterAclOpsKafkaEmbeddedTest : ClusterAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaBroker(3).apply {
            brokerProperty("authorizer.class.name", AclAuthorizer::class.java.canonicalName)
            brokerProperty("super.users", "User:ANONYMOUS")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-2\\.1).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_2_1_1_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "wurstmeister/kafka:2.12-2.1.1",
            clusterSize = 3
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.1")
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-2\\.3).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_2_3_1_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:2.3.1",
            clusterSize = 3
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.3")
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-2\\.5).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_2_5_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:2.5.0",
            clusterSize = 3
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.5")
}


@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-2\\.8).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_2_8_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:2.8.0",
            clusterSize = 3
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.8")
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-3\\.1).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_3_1_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.1.0",
            clusterSize = 3
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.1")
}



