package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.TOPIC_CONFIG_PROPERTIES
import com.infobip.kafkistry.kafka.ThrottleRate
import com.infobip.kafkistry.model.TopicConfigMap
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import kafka.server.DynamicConfig
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import java.util.concurrent.CompletableFuture

class ConfigOps(
    clientCtx: ClientCtx,
): BaseOps(clientCtx) {

    fun partialUpdateTopicConfig(
        topicName: TopicName,
        config: TopicConfigMap,
    ) : CompletableFuture<Unit> {
        val configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
        return updateConfig(
            configResource = configResource,
            alterConfigs = {
                val resourceConfigs = adminClient
                    .describeConfigs(listOf(configResource), DescribeConfigsOptions().withReadTimeout())
                    .all()
                    .asCompletableFuture("partial topic update config read current")
                    .get()
                val currentConfig = resourceConfigs[configResource]
                    ?: throw KafkaClusterManagementException("Did not get response for config of topic $topicName")
                val fullConfig = currentConfig.entries().associate { it.name() to it.value() }.plus(config)
                fullConfig.toKafkaConfig()
            },
            alterConfigOps = { config.toToAlterSetOps() },
        )
    }

    @SuppressWarnings("kotlin:S1874")   //sonar: deprecation usage
    private fun updateConfig(
        configResource: ConfigResource,
        alterConfigs: () -> Config,
        alterConfigOps: () -> Collection<AlterConfigOp>,
    ): CompletableFuture<Unit> {
        val alterConfigsResult = if (clusterVersion < VERSION_2_3) {
            if (configResource.type() == ConfigResource.Type.BROKER) {
                runOperation("alter broker config") {
                    val adminZkClient = newZKAdminClient()
                    val brokerConfigs = adminZkClient.fetchEntityConfig("brokers", configResource.name())
                    alterConfigOps().forEach { alterOp ->
                        when (alterOp.opType()) {
                            AlterConfigOp.OpType.SET -> brokerConfigs[alterOp.configEntry().name()] = alterOp.configEntry().value()
                            AlterConfigOp.OpType.DELETE -> brokerConfigs.remove(alterOp.configEntry().name())
                            else -> throw UnsupportedOperationException("Unsupported operation type ${alterOp.opType()}")
                        }
                    }
                    adminZkClient.changeConfigs("brokers", configResource.name(), brokerConfigs, true)
                }
                return CompletableFuture.completedFuture(Unit)
            } else {
                //suppressing since it's deprecated for version 2.3.0 but, it's the only way for older broker versions
                @Suppress("DEPRECATION")
                adminClient.alterConfigs(
                    mapOf(configResource to alterConfigs()), AlterConfigsOptions().withWriteTimeout()
                )
            }
        } else {
            adminClient.incrementalAlterConfigs(
                mapOf(configResource to alterConfigOps()), AlterConfigsOptions().withWriteTimeout()
            )
        }
        return alterConfigsResult
            .all()
            .asCompletableFuture("alter configs")
            .thenApply { }
    }

    fun updateTopicConfig(topicName: TopicName, updatingConfig: TopicConfigMap): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName),
            alterConfigs = { updatingConfig.toKafkaConfig() },
            alterConfigOps = { updatingConfig.toToTopicAlterOps() },
        )
    }

    fun setBrokerConfig(brokerId: BrokerId, config: Map<String, String>): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.BROKER, brokerId.toString()),
            alterConfigs = { config.toKafkaConfig() },
            alterConfigOps = { config.toToAlterSetOps() },
        )
    }

    fun unsetBrokerConfig(brokerId: BrokerId, configKeys: Set<String>): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.BROKER, brokerId.toString()),
            alterConfigs = { configKeys.associateWith { null }.toKafkaConfig() },
            alterConfigOps = { configKeys.toToAlterUnsetOps() },
        )
    }

    fun updateThrottleRate(brokerId: BrokerId, throttleRate: ThrottleRate): CompletableFuture<Unit> {
        val dynamicConf = DynamicConfig.`Broker$`.`MODULE$`
        val configs = with(dynamicConf) {
            mapOf(
                LeaderReplicationThrottledRateProp() to throttleRate.leaderRate?.takeIf { it > 0 }?.toString(),
                FollowerReplicationThrottledRateProp() to throttleRate.followerRate?.takeIf { it > 0 }?.toString(),
                ReplicaAlterLogDirsIoMaxBytesPerSecondProp() to throttleRate.alterDirIoRate?.takeIf { it > 0 }?.toString(),
            )
        }
        return updateConfig(ConfigResource(ConfigResource.Type.BROKER, brokerId.toString()),
            alterConfigs = { Config(configs.map { ConfigEntry(it.key, it.value) }) },
            alterConfigOps = {
                configs.map {
                    AlterConfigOp(
                        ConfigEntry(it.key, it.value),
                        if (it.value != null) AlterConfigOp.OpType.SET else AlterConfigOp.OpType.DELETE
                    )
                }
            }
        )
    }

    private fun TopicConfigMap.toKafkaConfig(): Config = this
        .mapNotNull { e -> ConfigEntry(e.key, e.value).takeIf { it.value() != null } }
        .let { Config(it) }

    private fun TopicConfigMap.toToAlterSetOps(): Collection<AlterConfigOp> = this
        .map { e -> ConfigEntry(e.key, e.value.takeIf { it != null }) }
        .map { AlterConfigOp(it, if (it.value() != null) AlterConfigOp.OpType.SET else AlterConfigOp.OpType.DELETE) }

    private fun Set<String>.toToAlterUnsetOps(): Collection<AlterConfigOp> = this
        .map { ConfigEntry(it, null) }
        .map { AlterConfigOp(it, AlterConfigOp.OpType.DELETE) }

    private fun TopicConfigMap.toToTopicAlterOps(): Collection<AlterConfigOp> = this
        .toToAlterSetOps()
        .plus(
            TOPIC_CONFIG_PROPERTIES
            .filter { it !in this.keys }
            .map { AlterConfigOp(ConfigEntry(it, null), AlterConfigOp.OpType.DELETE) }
        )

}