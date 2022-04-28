package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import kafka.server.DynamicConfig
import kafka.zk.AdminZkClient
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.apache.kafka.clients.admin.DescribeConfigsOptions
import org.apache.kafka.common.config.ConfigResource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ClusterOps(
    clientCtx: ClientCtx
): BaseOps(clientCtx) {

    private val currentClusterVersionRef = clientCtx.currentClusterVersionRef

    // hold set of broker ids that are used by topic assignments, refresh it when topic's are refreshed
    // this is workaround to be aware of all nodes in cluster even if some nodes are down because
    // AdminClient.describeCluster().nodes() returns only currently online nodes
    private val topicAssignmentsUsedBrokerIdsRef = AtomicReference<Set<BrokerId>?>(null)
    private val knownBrokers = ConcurrentHashMap<BrokerId, ClusterBroker>()

    fun acceptUsedReplicaBrokerIds(brokerIds: Set<BrokerId>) {
        topicAssignmentsUsedBrokerIdsRef.set(brokerIds)
    }

    fun clusterInfo(identifier: KafkaClusterIdentifier): CompletableFuture<ClusterInfo> {
        val clusterResult = adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
        val clusterIdFuture = clusterResult.clusterId().asCompletableFuture("describe clusterId")
        val controllerNodeFuture = clusterResult.controller().asCompletableFuture("describe cluster controller")
        val nodesFuture = clusterResult.nodes().asCompletableFuture("describe cluster nodes")
        return CompletableFuture.allOf(clusterIdFuture, controllerNodeFuture, nodesFuture)
            .thenCompose {
                val clusterId = clusterIdFuture.get()
                val controllerNode = controllerNodeFuture.get()
                val nodes = nodesFuture.get()
                val nodeConfigResources = nodes.map { node ->
                    ConfigResource(ConfigResource.Type.BROKER, node.id().toString())
                }
                adminClient
                    .describeConfigs(nodeConfigResources, DescribeConfigsOptions().withReadTimeout())
                    .all()
                    .asCompletableFuture("describe cluster all brokers configs")
                    .thenApply { configsResponse ->
                        val brokerConfigs = configsResponse
                            .mapKeys { it.key.name().toInt() }
                            .mapValues { (brokerId, config) -> resolveBrokerConfig(config, brokerId) }
                        val controllerConfig = brokerConfigs[controllerNode.id()]
                            ?: brokerConfigs.values.first()
                        val zookeeperConnection = controllerConfig["zookeeper.connect"]?.value ?: ""
                        val majorVersion = controllerConfig["inter.broker.protocol.version"]?.value
                        val clusterVersion = majorVersion?.let { Version.parse(it) }
                            ?.also(currentClusterVersionRef::set)
                        val securityEnabled = controllerConfig["authorizer.class.name"]?.value?.isNotEmpty() == true
                        val onlineNodeIds = nodes.map { it.id() }.sorted()
                        val allKnownNodeIds = onlineNodeIds
                            .plus(topicAssignmentsUsedBrokerIdsRef.get() ?: emptySet())
                            .distinct()
                            .sorted()
                        if (onlineNodeIds.toSet() == allKnownNodeIds.toSet()) {
                            knownBrokers.keys.retainAll(onlineNodeIds)
                        }
                        val allKnownBrokers = nodes.asSequence()
                            .map { ClusterBroker(it.id(), it.host(), it.port(), it.rack()) }
                            .onEach { knownBrokers[it.brokerId] = it }
                            .plus(knownBrokers.values)
                            .distinctBy { it.brokerId }
                            .sortedBy { it.brokerId }
                            .toList()
                        ClusterInfo(
                            clusterId = clusterId,
                            identifier = identifier,
                            config = controllerConfig,
                            perBrokerConfig = brokerConfigs,
                            perBrokerThrottle = brokerConfigs.mapValues { it.value.extractThrottleRate() },
                            controllerId = controllerNode.id(),
                            nodeIds = allKnownNodeIds,
                            onlineNodeIds = onlineNodeIds,
                            brokers = allKnownBrokers,
                            connectionString = nodes.joinToString(",") { it.host() + ":" + it.port() },
                            zookeeperConnectionString = zookeeperConnection,
                            clusterVersion = clusterVersion,
                            securityEnabled = securityEnabled,
                        )
                    }
            }
    }

    private fun resolveBrokerConfig(config: Config, brokerId: Int): Map<String, ConfigValue> {
        val hasFalselyNullEntries = config.entries().any {
            it.source() == ConfigEntry.ConfigSource.DYNAMIC_BROKER_CONFIG && it.value() == null
        }
        val zkBrokerConfig = if (hasFalselyNullEntries) {
            AdminZkClient(zkClient).fetchEntityConfig("brokers", brokerId.toString())
        } else {
            null
        }
        return config.entries()
            .map {
                if (it.source() == ConfigEntry.ConfigSource.DYNAMIC_BROKER_CONFIG && it.value() == null) {
                    val zkValue = zkBrokerConfig?.getProperty(it.name())?.toString()
                    ConfigEntry(it.name(), zkValue)
                } else {
                    it
                }
            }
            .associate { it.name() to it.toTopicConfigValue() }
            .let { existingConfigs ->
                val dynamicConfigs = DynamicConfig.`Broker$`.`MODULE$`.names().associateWith {
                    ConfigValue(
                        null,
                        default = true,
                        readOnly = false,
                        sensitive = false,
                        ConfigEntry.ConfigSource.DYNAMIC_BROKER_CONFIG
                    )
                }
                dynamicConfigs + existingConfigs
            }
            .map { it }.sortedBy { it.key }.associate { it.toPair() }
    }

    private fun ExistingConfig.extractThrottleRate(): ThrottleRate {
        val dynamicConf = DynamicConfig.`Broker$`.`MODULE$`
        return ThrottleRate(
            leaderRate = get(dynamicConf.LeaderReplicationThrottledRateProp())?.value?.toLongOrNull(),
            followerRate = get(dynamicConf.FollowerReplicationThrottledRateProp())?.value?.toLongOrNull(),
            alterDirIoRate = get(dynamicConf.ReplicaAlterLogDirsIoMaxBytesPerSecondProp())?.value?.toLongOrNull(),
        )
    }

}