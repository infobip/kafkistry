package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import kafka.server.DynamicConfig
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.apache.kafka.server.config.QuotaConfigs
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class ClusterOps(
    private val clientCtx: ClientCtx
): BaseOps(clientCtx) {

    private val currentClusterVersionRef = clientCtx.currentClusterVersionRef

    // hold set of broker ids that are used by topic assignments, refresh it when topic's are refreshed
    // this is workaround to be aware of all nodes in cluster even if some nodes are down because
    // AdminClient.describeCluster().nodes() returns only currently online nodes
    private val topicAssignmentsUsedBrokerIdsRef = AtomicReference<Set<BrokerId>?>(null)
    private val knownNodes = ConcurrentHashMap<NodeId, ClusterNode>()

    //holds reference to best guess if kraft is enabled on cluster
    // - null: meaning unknown yet,
    // - true: meaning that last scraped config looked like kraft is enabled,
    // - false: meaning last call failed with unsupported version or config looked like kraft not enabled
    private val kraftEnabledRef = AtomicReference<Boolean>(null)

    fun acceptUsedReplicaBrokerIds(brokerIds: Set<BrokerId>) {
        topicAssignmentsUsedBrokerIdsRef.set(brokerIds)
    }

    fun clusterInfo(identifier: KafkaClusterIdentifier): CompletableFuture<ClusterInfo> {
        val clusterResult = adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
        val clusterIdFuture = clusterResult.clusterId().asCompletableFuture("describe clusterId")
        val controllerNodeFuture = clusterResult.controller().asCompletableFuture("describe cluster controller")
        val brokerNodesFuture = clusterResult.nodes().asCompletableFuture("describe cluster nodes")
        val featuresFuture = adminClient.describeFeatures(DescribeFeaturesOptions().withReadTimeout())
            .featureMetadata().asCompletableFuture("describe cluster features")
        val quorumFuture = when (kraftEnabledRef.get()) {
            null, true -> adminClient.describeMetadataQuorum(DescribeMetadataQuorumOptions().withReadTimeout())
                .quorumInfo().asCompletableFuture("describe cluster quorum info")
                .exceptionally { ex ->
                    if (ex.cause is UnsupportedVersionException) {
                        kraftEnabledRef.set(false)
                        null
                    } else {
                        throw ex
                    }
                }
            false -> CompletableFuture.completedFuture(null) //don't even attempt to fetch kraft's quorum info
        }
        val controllerNodesFuture = if (kraftEnabledRef.get() != false && clientCtx.controllerConnectionRef.get() != null) {
            controllersAdminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
                .nodes().asCompletableFuture("describe cluster controller nodes")
                .whenComplete { _, ex ->
                    if (ex is UnsupportedVersionException && "Direct-to-controller" in ex.message.orEmpty()) {
                        controllersAdminClient.close()
                    }
                }
        } else {
            CompletableFuture.completedFuture(emptyList())
        }
        return CompletableFuture
            .allOf(clusterIdFuture, controllerNodeFuture, brokerNodesFuture, featuresFuture, quorumFuture, controllerNodesFuture)
            .thenCompose {
                val clusterId = clusterIdFuture.get()
                val controllerNode = controllerNodeFuture.get()
                val brokerNodes = brokerNodesFuture.get()
                val controllerNodes = controllerNodesFuture.get()
                val brokerNodeConfigResources = brokerNodes.map { node ->
                    ConfigResource(ConfigResource.Type.BROKER, node.id().toString())
                }
                val controllerNodeConfigResources = controllerNodes.map { node ->
                    ConfigResource(ConfigResource.Type.BROKER, node.id().toString())
                }
                fun DescribeConfigsResult.asFutureOfSuccessfulOrEmpty(ofWhat: String): CompletableFuture<Map<ConfigResource, Config?>> {
                    return this.values()
                        .mapValues { (node, future) ->
                            future.asCompletableFuture("describe cluster $ofWhat configs for $node")
                                .exceptionally { null } //ignore failure
                        }
                        .let { futuresMap ->
                            CompletableFuture.allOf(*futuresMap.values.toTypedArray()).thenApply {
                                futuresMap.mapValues { it.value.get() }
                            }
                        }
                }
                val brokerConfigsFuture = adminClient
                    .describeConfigs(brokerNodeConfigResources, DescribeConfigsOptions().withReadTimeout())
                    .asFutureOfSuccessfulOrEmpty("brokers")
                val controllerConfigsFuture = if (controllerNodes.isNotEmpty()) {
                    controllersAdminClient
                        .describeConfigs(controllerNodeConfigResources, DescribeConfigsOptions().withReadTimeout())
                        .asFutureOfSuccessfulOrEmpty("controllers")
                } else {
                    CompletableFuture.completedFuture(emptyMap())
                }
                CompletableFuture
                    .allOf(brokerConfigsFuture, controllerConfigsFuture)
                    .thenApply {
                        fun ConfigResource.nodeId(): NodeId = this.name().toInt()
                        val brokerConfigs = brokerConfigsFuture.get()
                            .withoutNullValues()
                            .mapKeys { it.key.nodeId() }
                            .mapValues { (brokerId, config) -> resolveBrokerConfig(config, brokerId) }
                        val controllersConfigs = controllerConfigsFuture.get()
                            .withoutNullValues()
                            .mapKeys { it.key.nodeId() }
                            .mapValues { (brokerId, config) -> resolveBrokerConfig(config, brokerId) }
                        val nodesConfigs = controllersConfigs + brokerConfigs
                        val controllerConfig = nodesConfigs[controllerNode.id()]
                            ?: nodesConfigs.values.first()
                        val zookeeperConnection = controllerConfig["zookeeper.connect"]?.value ?: ""
                        val majorVersion = controllerConfig["inter.broker.protocol.version"]?.value
                        val clusterVersion = majorVersion?.let { Version.parse(it) }
                            ?.also(currentClusterVersionRef::set)
                        val securityEnabled = controllerConfig["authorizer.class.name"]?.value?.isNotEmpty() == true
                        val kraftEnabled = (controllerConfig["process.roles"]?.value?.isNotEmpty() == true).also {
                            kraftEnabledRef.set(it)
                        }
                        val features = featuresFuture.get()
                        val quorum = quorumFuture.get()
                        val allNodes = (brokerNodes + controllerNodes).distinctBy { it.id() }
                        val onlineNodeIds = allNodes.map { it.id() }.sorted().filter { it in nodesConfigs }
                        val allKnownNodeIds = onlineNodeIds
                            .plus(topicAssignmentsUsedBrokerIdsRef.get() ?: emptySet())
                            .plus(quorum?.voters()?.map { it.replicaId() }.orEmpty())
                            .distinct()
                            .sorted()
                        if (onlineNodeIds.toSet() == allKnownNodeIds.toSet()) {
                            knownNodes.keys.retainAll(onlineNodeIds.toSet())
                        }
                        fun nodeRoles(nodeId: NodeId): List<ClusterNodeRole> {
                            val (isBroker, isController) = if (!kraftEnabled) {
                                true to (controllerNode.id() == nodeId)
                            } else {
                                val rolesStr = nodesConfigs[nodeId]?.get("process.roles")?.value?.lowercase()
                                    ?: return knownNodes[nodeId]?.roles ?: ROLES_NONE
                                ("broker" in rolesStr) to ("controller" in rolesStr)
                            }
                            return when {
                                isBroker && isController -> ROLES_BROKER_CONTROLLER
                                isBroker && !isController -> ROLES_BROKER
                                !isBroker && isController -> ROLES_CONTROLLER
                                else -> ROLES_NONE
                            }
                        }
                        val allKnownNodes = allNodes.asSequence()
                            .map { ClusterNode(it.id(), it.host(), it.port(), nodeRoles(it.id()), it.rack()) }
                            .onEach { knownNodes[it.nodeId] = it }
                            .plus(knownNodes.values)
                            .distinctBy { it.nodeId }
                            .sortedBy { it.nodeId }
                            .toList()
                        ClusterInfo(
                            clusterId = clusterId,
                            identifier = identifier,
                            config = controllerConfig,
                            perBrokerConfig = nodesConfigs,
                            perBrokerThrottle = nodesConfigs.mapValues { it.value.extractThrottleRate() },
                            controllerId = controllerNode.id(),
                            nodeIds = allKnownNodeIds,
                            onlineNodeIds = onlineNodeIds,
                            nodes = allKnownNodes,
                            connectionString = brokerNodes.joinToString(",") { it.host() + ":" + it.port() },
                            zookeeperConnectionString = zookeeperConnection,
                            clusterVersion = clusterVersion,
                            securityEnabled = securityEnabled,
                            kraftEnabled = kraftEnabled,
                            features = ClusterFeatures(
                                finalizedFeatures = features.finalizedFeatures().mapValues { (_, versions) ->
                                    VersionsRange(versions.minVersionLevel().toInt(), versions.maxVersionLevel().toInt())
                                },
                                supportedFeatures = features.supportedFeatures().mapValues { (_, versions) ->
                                    VersionsRange(versions.minVersion().toInt(), versions.maxVersion().toInt())
                                },
                                finalizedFeaturesEpoch = features.finalizedFeaturesEpoch().orElse(null),
                            ),
                            quorumInfo = quorum?.let {
                                ClusterQuorumInfo(
                                    leaderId = quorum.leaderId(),
                                    leaderEpoch = quorum.leaderEpoch(),
                                    highWatermark = quorum.highWatermark(),
                                    voters = quorum.voters().map { it.toReplicaState() },
                                    observers = quorum.observers().map { it.toReplicaState() },
                                )
                            } ?: ClusterQuorumInfo.EMPTY,
                        )
                    }
            }
    }

    private fun QuorumInfo.ReplicaState.toReplicaState() = QuorumReplicaState(
        replicaId = replicaId(),
        logEndOffset = logEndOffset(),
        lastFetchTimestamp = lastFetchTimestamp().let { if (it.isPresent) it.asLong else null },
        lastCaughtUpTimestamp = lastCaughtUpTimestamp().let { if (it.isPresent) it.asLong else null },
    )

    private fun resolveBrokerConfig(config: Config, brokerId: Int): ExistingConfig {
        val hasFalselyNullEntries = config.entries().any {
            it.source() == ConfigEntry.ConfigSource.DYNAMIC_BROKER_CONFIG && it.value() == null
        }
        val zkBrokerConfig = if (hasFalselyNullEntries) {
            newZKAdminClient().fetchEntityConfig("brokers", brokerId.toString())
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
        return ThrottleRate(
            leaderRate = get(QuotaConfigs.LEADER_REPLICATION_THROTTLED_RATE_CONFIG)?.value?.toLongOrNull(),
            followerRate = get(QuotaConfigs.FOLLOWER_REPLICATION_THROTTLED_RATE_CONFIG)?.value?.toLongOrNull(),
            alterDirIoRate = get(QuotaConfigs.REPLICA_ALTER_LOG_DIRS_IO_MAX_BYTES_PER_SECOND_CONFIG)?.value?.toLongOrNull(),
        )
    }

    private fun <K, V> Map<K, V?>.withoutNullValues(): Map<K, V> = entries.mapNotNull { (k, v) ->
        v?.let { k to it }
    }.toMap()

    companion object {
        private val ROLES_BROKER_CONTROLLER = listOf(ClusterNodeRole.BROKER, ClusterNodeRole.CONTROLLER)
        private val ROLES_BROKER = listOf(ClusterNodeRole.BROKER)
        private val ROLES_CONTROLLER = listOf(ClusterNodeRole.CONTROLLER)
        private val ROLES_NONE = emptyList<ClusterNodeRole>()
    }

}