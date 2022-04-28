package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.Version
import com.infobip.kafkistry.kafka.ZookeeperConnectionResolver
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSampler
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.apache.kafka.clients.admin.DescribeConfigsOptions
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.common.config.ConfigResource

class ClientOps(
    private val clientCtx: ClientCtx,
    private val zookeeperConnectionResolver: ZookeeperConnectionResolver,
    private val recordReadSampler: RecordReadSampler,
) : BaseOps(clientCtx) {

    fun bootstrapClusterVersionAndZkConnection() {
        val controllerConfig = adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
            .controller()
            .asCompletableFuture("initial describe cluster")
            .thenCompose { controllerNode ->
                adminClient
                    .describeConfigs(
                        listOf(ConfigResource(ConfigResource.Type.BROKER, controllerNode.id().toString())),
                        DescribeConfigsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("initial describe broker configs")
            }
            .thenApply { configs ->
                configs.values.first().entries().associate { it.name() to it.toTopicConfigValue() }
            }
            .whenComplete { _, ex ->
                // need to close everything before exception propagates out of KafkaManagementClientImpl-s constructor
                if (ex != null) close()
            }
            .get()
        val zookeeperConnection = controllerConfig["zookeeper.connect"]?.value ?: ""
        val majorVersion = controllerConfig["inter.broker.protocol.version"]?.value
        majorVersion?.let { Version.parse(it) }?.also {
            clientCtx.currentClusterVersionRef.set(it)
        }
        zookeeperConnectionResolver.resolveZkConnection(zookeeperConnection).also {
            clientCtx.zkConnectionRef.set(it)
        }
    }

    fun close() {
        clientCtx.adminClient.close(writeTimeoutDuration())
        recordReadSampler.close()
        if (clientCtx.zkClientLazy.isInitialized()) {
            clientCtx.zkClientLazy.value.close()
        }
    }

    fun test() {
        clientCtx.adminClient
            .listTopics(ListTopicsOptions().withReadTimeout())
            .names()
            .asCompletableFuture("test list topic names")
            .get()
        recordReadSampler.test()
    }

}