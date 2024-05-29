package com.infobip.kafkistry.it.cluster_ops


import com.infobip.kafkistry.it.cluster_ops.testsupport.TestKafkaLifecycleExtension
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSamplerFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@ExtendWith(TestKafkaLifecycleExtension::class)
abstract class AbstractClusterOpsTestSuite {

    protected val log: Logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        val kafkaClientProvider = KafkaClientProvider(
                ClientFactory(
                        KafkaManagementClientProperties(),
                        RecordReadSamplerFactory(),
                        zookeeperConnectionResolver = Optional.of(object : ZookeeperConnectionResolver {
                            override fun resolveZkConnection(brokerZkConnection: String): String {
                                //replace zk host of docker-compose service name
                                return if (brokerZkConnection.startsWith("zookeeper:")) {
                                    brokerZkConnection.replaceFirst("zookeeper", "localhost")
                                } else {
                                    brokerZkConnection
                                }
                            }
                        })
                ),
            KafkaManagementClientProperties().apply {
                clusterConcurrency = 2
            }
        )
    }

    protected fun <R> doOnKafka(operation: (KafkaManagementClient) -> R): R {
        return kafkaClientProvider.doWithClient(
                KafkaCluster(
                    "test", "test-id", clusterConnection,
                    sslEnabled = false, saslEnabled = false, tags = emptyList()
                ),
                operation
        )
    }

    abstract val clusterConnection: String

    protected var clusterBrokerIds: List<BrokerId> = emptyList()
    protected var clusterNodeIds: List<BrokerId> = emptyList()

    @BeforeEach
    fun setup() {
        doOnKafka { client ->
            client.deleteAllOnCluster()
            val clusterInfo = client.clusterInfo("").get()
            clusterBrokerIds = clusterInfo.brokerIds
            clusterNodeIds = clusterInfo.nodeIds
        }
        assertThat(clusterNodeIds).`as`("Node IDs of cluster")
            .containsExactlyInAnyOrder(0, 1, 2)
            .hasSize(3)
    }

}

