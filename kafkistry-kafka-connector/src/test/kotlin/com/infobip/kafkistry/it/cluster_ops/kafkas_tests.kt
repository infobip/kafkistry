@file:Suppress("ClassName")

package com.infobip.kafkistry.it.cluster_ops

import com.infobip.kafkistry.it.cluster_ops.custom.EmbeddedKafkaKraftCustomBroker
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import com.infobip.kafkistry.it.cluster_ops.testsupport.KafkaClusterLifecycle
import com.infobip.kafkistry.it.cluster_ops.testsupport.asTestKafkaLifecycle
import com.infobip.kafkistry.kafka.Version
import com.infobip.kafkistry.utils.getFieldReflective
import kafka.security.authorizer.AclAuthorizer
import kafka.testkit.KafkaClusterTestKit
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker
import org.springframework.kafka.test.EmbeddedKafkaZKBroker

class ClusterOpsKafkaZkEmbeddedTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaZKBroker(3).apply {
            brokerProperty("auto.leader.rebalance.enable", "false")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val expectedClusterVersion = Version.of("3.9")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

class ClusterOpsKafkaKraftEmbeddedCustomTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaKraftCustomBroker(0, 3, 3).apply {
            brokerProperty("auto.leader.rebalance.enable", "false")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val controllersConnection: String get() = kafka.kafkaCluster.getFieldReflective<KafkaClusterTestKit>("cluster").bootstrapControllers()
    override val expectedClusterVersion = Version.of("3.9")
    override val expectedKraftEnabled: Boolean = true
    override val expectedNumNodes: Int get() = 6
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|embedded-combined).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaKraftEmbeddedTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaKraftBroker(3, 1).apply {
            brokerProperty("auto.leader.rebalance.enable", "false")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val controllersConnection: String get() = kafka.kafkaCluster.getFieldReflective<KafkaClusterTestKit>("cluster").bootstrapControllers()
    override val expectedClusterVersion = Version.of("3.9")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

class ClusterAclOpsKafkaEmbeddedTest : ClusterAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaZKBroker(3).apply {
            brokerProperty("authorizer.class.name", AclAuthorizer::class.java.canonicalName)
            brokerProperty("super.users", "User:ANONYMOUS")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
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
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.1")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
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
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.3")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
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
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.5")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
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
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("2.8")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
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
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.1")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
        named = "enabledIntegrationTests",
        matches = "all|.*(all-kafka|kafka-3\\.3).*",
        disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_3_3_2_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.3.2",
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.3")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}


@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-3\\.4).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_3_4_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.4.0",
            kraft = false,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.4")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-3\\.6).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_3_6_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.6.0",
            logContainersOutput = false,
            kraft = true,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.6")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}



