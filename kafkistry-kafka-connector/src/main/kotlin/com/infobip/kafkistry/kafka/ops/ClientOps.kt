package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.ControllersConnectionResolver
import com.infobip.kafkistry.kafka.ZookeeperConnectionResolver
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSampler
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.apache.kafka.clients.admin.DescribeConfigsOptions
import org.apache.kafka.clients.admin.DescribeFeaturesOptions
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.common.config.ConfigResource
import java.util.concurrent.CompletableFuture

class ClientOps(
    private val clientCtx: ClientCtx,
    private val zookeeperConnectionResolver: ZookeeperConnectionResolver,
    private val recordReadSampler: RecordReadSampler,
    private val controllersConnectionResolver: ControllersConnectionResolver,
) : BaseOps(clientCtx) {

    fun bootstrapClusterVersionAndZkConnection() {
        fun <T> CompletableFuture<T>.closeWhenExceptionallyCompleted(): CompletableFuture<T> {
            return this.whenComplete { _, ex ->
                // need to close everything before exception propagates out of KafkaManagementClientImpl-s constructor
                if (ex != null) close()
            }
        }
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
            .closeWhenExceptionallyCompleted()
            .get()
        val zookeeperConnection = controllerConfig["zookeeper.connect"]?.value ?: ""
        val interBrokerVersion = controllerConfig["inter.broker.protocol.version"]?.value
        interBrokerVersion
            ?.let { ClusterOps.resolveClusterVersion(it, null) }
            ?.also { clientCtx.currentClusterVersionRef.set(it) }
            ?: run {
                adminClient.describeFeatures(DescribeFeaturesOptions().withReadTimeout())
                    .featureMetadata()
                    .asCompletableFuture("bootstrap describe features")
                    .closeWhenExceptionallyCompleted()
                    .get()
                    ?.let { ClusterOps.resolveClusterVersion(null, it) }
                    ?.also { clientCtx.currentClusterVersionRef.set(it) }
            }
        zookeeperConnectionResolver.resolveZkConnection(zookeeperConnection).also {
            clientCtx.zkConnectionRef.set(it)
        }
        controllerConfig["controller.quorum.voters"]?.value?.also { voters ->
            val resolvedControllersConnection = controllersConnectionResolver
                .resolveQuorumControllersConnection(voters)
                .takeIf { it.isNotEmpty() }
            clientCtx.controllerConnectionRef.set(resolvedControllersConnection)
        }
    }

    fun close() {
        clientCtx.adminClient.close(writeTimeoutDuration())
        recordReadSampler.close()
        if (clientCtx.zkClientLazy.isInitialized()) {
            clientCtx.zkClientLazy.value.close()
        }
        if (clientCtx.controllerClientLazy.isInitialized()) {
            clientCtx.controllerClientLazy.value.close()
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