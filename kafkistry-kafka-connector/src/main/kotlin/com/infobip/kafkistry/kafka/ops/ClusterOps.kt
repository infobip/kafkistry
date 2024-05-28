package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import kafka.server.DynamicConfig
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.errors.UnsupportedVersionException
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
        val nodesFuture = clusterResult.nodes().asCompletableFuture("describe cluster nodes")
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
        return CompletableFuture
            .allOf(clusterIdFuture, controllerNodeFuture, nodesFuture, featuresFuture, quorumFuture)
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
                        val kraftEnabled = (controllerConfig["process.roles"]?.value?.isNotEmpty() == true).also {
                            kraftEnabledRef.set(it)
                        }
                        val onlineNodeIds = nodes.map { it.id() }.sorted()
                        val allKnownNodeIds = onlineNodeIds
                            .plus(topicAssignmentsUsedBrokerIdsRef.get() ?: emptySet())
                            .distinct()
                            .sorted()
                        if (onlineNodeIds.toSet() == allKnownNodeIds.toSet()) {
                            knownBrokers.keys.retainAll(onlineNodeIds.toSet())
                        }
                        val allKnownBrokers = nodes.asSequence()
                            .map { ClusterBroker(it.id(), it.host(), it.port(), it.rack()) }
                            .onEach { knownBrokers[it.brokerId] = it }
                            .plus(knownBrokers.values)
                            .distinctBy { it.brokerId }
                            .sortedBy { it.brokerId }
                            .toList()
                        val features = featuresFuture.get()
                        val quorum = quorumFuture.get()
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

    private fun resolveBrokerConfig(config: Config, brokerId: Int): Map<String, ConfigValue> {
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
        val dynamicConf = DynamicConfig.`Broker$`.`MODULE$`
        return ThrottleRate(
            leaderRate = get(dynamicConf.LeaderReplicationThrottledRateProp())?.value?.toLongOrNull(),
            followerRate = get(dynamicConf.FollowerReplicationThrottledRateProp())?.value?.toLongOrNull(),
            alterDirIoRate = get(dynamicConf.ReplicaAlterLogDirsIoMaxBytesPerSecondProp())?.value?.toLongOrNull(),
        )
    }

}