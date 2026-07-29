@file:Suppress("ClassName")

package com.infobip.kafkistry.it.cluster_ops

import com.infobip.kafkistry.it.cluster_ops.custom.KafkaKRaftEmbeddedCluster
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer.ConsensusType.KRAFT
import com.infobip.kafkistry.it.cluster_ops.testcontainer.KafkaClusterContainer.ConsensusType.ZOOKEEPER
import com.infobip.kafkistry.it.cluster_ops.testsupport.KafkaClusterLifecycle
import com.infobip.kafkistry.it.cluster_ops.testsupport.asEmbeddedKafkaBroker
import com.infobip.kafkistry.it.cluster_ops.testsupport.asTestKafkaLifecycle
import com.infobip.kafkistry.kafka.Version
import com.infobip.kafkistry.utils.getFieldReflective
import org.apache.kafka.common.test.KafkaClusterTestKit
import org.apache.kafka.metadata.authorizer.StandardAuthorizer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker
import com.infobip.kafkistry.shaded.org.springframework.kafka.test.EmbeddedKafkaZKBroker

class ClusterOpsKafkaZkEmbeddedTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = EmbeddedKafkaZKBroker(3).apply {
            brokerProperty("auto.leader.rebalance.enable", "false")
        }.asEmbeddedKafkaBroker().asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.brokersAsString
    override val expectedClusterVersion = Version.of("3.9")
    override val expectedKraftEnabled: Boolean = false
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

class ClusterOpsKafkaKraftEmbeddedCustomTest : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaKRaftEmbeddedCluster(count = 3).apply {
            allBrokersProperty("auto.leader.rebalance.enable", "false")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.embeddedKafka.brokersAsString
    override val controllersConnection: String get() = kafka.kafkaCluster.embeddedKafka.getFieldReflective<KafkaClusterTestKit>("cluster").bootstrapControllers()
    override val expectedClusterVersion = Version.of("4.4")
    override val expectedKraftEnabled: Boolean = true
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
        val kafka = object : EmbeddedKafkaKraftBroker(3, 1){
            //overriding because of spring-kafka-test 4.1.0 depends on kafka test kit that uses cluster.getClientProperties() which was deleted
            override fun getBrokersAsString(): String = this.cluster?.bootstrapServers() ?: error("no bootstrap servers")
        }.apply {
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
        val kafka = KafkaKRaftEmbeddedCluster(count = 3).apply {
            allBrokersProperty("authorizer.class.name", StandardAuthorizer::class.java.canonicalName)
            allBrokersProperty("super.users", "User:ANONYMOUS")
        }.asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.embeddedKafka.brokersAsString
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
            consensus = ZOOKEEPER,
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
            kafkaImage = "wurstmeister/kafka:2.12-2.3.1",
            consensus = ZOOKEEPER,
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
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_2_5_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:2.5.0",
            consensus = ZOOKEEPER,
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
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_2_8_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:2.8.0",
            consensus = ZOOKEEPER,
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
            kafkaImage = "itzg/kafka:3.1.0",
            consensus = ZOOKEEPER,
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
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_3_3_2_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.3.2",
            consensus = ZOOKEEPER,
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
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_3_4_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.4.0",
            consensus = ZOOKEEPER,
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
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_3_6_0_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "bitnami/kafka:3.6.0",
            consensus = KRAFT,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.6")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-3\\.7).*",
    disabledReason = "These tests are too slow to run each time",
)
@Disabled("No available image for amd64 and arm64 platforms")
class ClusterOpsKafkaDockerCompose_V_3_7_1_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "apache/kafka:3.7.1",
            consensus = KRAFT,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.7")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-3\\.9).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_3_9_1_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "apache/kafka:3.9.1",
            consensus = KRAFT,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("3.9")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}

@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(all-kafka|kafka-4\\.0).*",
    disabledReason = "These tests are too slow to run each time",
)
class ClusterOpsKafkaDockerCompose_V_4_0_1_Test : ClusterNoAclOperationsTestSuite() {

    companion object {
        @JvmField
        val kafka = KafkaClusterContainer(
            kafkaImage = "apache/kafka:4.0.1",
            consensus = KRAFT,
        ).asTestKafkaLifecycle()
    }

    override val clusterConnection: String get() = kafka.kafkaCluster.getBrokersUrl()
    override val expectedClusterVersion = Version.of("4.0")
    override val expectedKraftEnabled: Boolean = true
    override val testKafkaLifecycle: KafkaClusterLifecycle<*> get() = kafka
}



