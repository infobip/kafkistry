package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.TOPIC_CONFIG_PROPERTIES
import com.infobip.kafkistry.kafka.ThrottleRate
import com.infobip.kafkistry.model.TopicConfigMap
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.server.config.QuotaConfigs
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
    ): CompletableFuture<Unit> = updateConfigs(
        mapOf(configResource to AlterConfigsSupplier(alterConfigs, alterConfigOps))
    )

    private data class AlterConfigsSupplier(
        val alterConfigs: () -> Config,
        val alterConfigOps: () -> Collection<AlterConfigOp>,
    )

    @SuppressWarnings("kotlin:S1874")   //sonar: deprecation usage
    private fun updateConfigs(
        resourceUpdates: Map<ConfigResource, AlterConfigsSupplier>
    ): CompletableFuture<Unit> {
        val alterConfigsResult = if (clusterVersion < VERSION_2_3) {
            val brokerUpdates = resourceUpdates.filterKeys {
                it.type() == ConfigResource.Type.BROKER
            }
            val nonBrokerUpdates = resourceUpdates.filterKeys {
                it.type() != ConfigResource.Type.BROKER
            }
            val nonBrokersResult = nonBrokerUpdates.takeIf { it.isNotEmpty() }?.let { updates ->
                //suppressing since it's deprecated for version 2.3.0 but, it's the only way for older broker versions
                @Suppress("DEPRECATION")
                adminClient.alterConfigs(
                    updates.mapValues { it.value.alterConfigs() }, AlterConfigsOptions().withWriteTimeout()
                )
            }
            if (nonBrokersResult != null && brokerUpdates.isEmpty()) {
                nonBrokersResult
            } else {
                //have broker update, need to have blocking implementation
                runOperation("alter broker config") {
                    val adminZkClient = newZKAdminClient()
                    brokerUpdates.forEach { (configResource, configSupplier) ->
                        val brokerConfigs = adminZkClient.fetchEntityConfig("brokers", configResource.name())
                        configSupplier.alterConfigOps().forEach { alterOp ->
                            when (alterOp.opType()) {
                                AlterConfigOp.OpType.SET -> brokerConfigs[alterOp.configEntry().name()] = alterOp.configEntry().value()
                                AlterConfigOp.OpType.DELETE -> brokerConfigs.remove(alterOp.configEntry().name())
                                else -> throw UnsupportedOperationException("Unsupported operation type ${alterOp.opType()}")
                            }
                        }
                        adminZkClient.changeConfigs("brokers", configResource.name(), brokerConfigs, true)
                    }
                }
                return nonBrokersResult?.all()
                    ?.asCompletableFuture("alter configs")
                    ?.thenApply { }
                    ?: CompletableFuture.completedFuture(Unit)
            }
        } else {
            adminClient.incrementalAlterConfigs(
                resourceUpdates.mapValues { it.value.alterConfigOps() }, AlterConfigsOptions().withWriteTimeout()
            )
        }
        return alterConfigsResult
            .all()
            .asCompletableFuture("alter configs")
            .thenApply { }
    }

    fun updateTopicConfig(topicName: TopicName, updatingConfig: TopicConfigMap): CompletableFuture<Unit> {
        return updateTopicsConfigs(mapOf(topicName to updatingConfig))
    }

    fun updateTopicsConfigs(topicsConfigs: Map<TopicName, TopicConfigMap>): CompletableFuture<Unit> {
        return updateConfigs(
            topicsConfigs.entries.associate { (topicName, updatingConfig) ->
                ConfigResource(ConfigResource.Type.TOPIC, topicName) to AlterConfigsSupplier(
                    alterConfigs = { updatingConfig.toKafkaConfig() },
                    alterConfigOps = { updatingConfig.toToTopicAlterOps() },
                )
            }
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
        val configs = mapOf(
            QuotaConfigs.LEADER_REPLICATION_THROTTLED_RATE_CONFIG to throttleRate.leaderRate?.takeIf { it > 0 }?.toString(),
            QuotaConfigs.FOLLOWER_REPLICATION_THROTTLED_RATE_CONFIG to throttleRate.followerRate?.takeIf { it > 0 }?.toString(),
            QuotaConfigs.REPLICA_ALTER_LOG_DIRS_IO_MAX_BYTES_PER_SECOND_CONFIG to throttleRate.alterDirIoRate?.takeIf { it > 0 }?.toString(),
        )
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