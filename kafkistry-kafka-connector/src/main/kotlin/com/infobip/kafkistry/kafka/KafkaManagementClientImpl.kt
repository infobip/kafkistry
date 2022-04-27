package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.kafka.OffsetSeekType.*
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSampler
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.KafkaClusterManagementException
import com.infobip.kafkistry.service.KafkistryClusterReadException
import kafka.log.LogConfig
import kafka.server.ConfigType
import kafka.server.DynamicConfig
import kafka.zk.AdminZkClient
import kafka.zk.KafkaZkClient
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.ElectionType
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.TopicPartitionInfo
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclBindingFilter
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.internals.QuotaConfigs
import org.apache.kafka.common.quota.ClientQuotaAlteration
import org.apache.kafka.common.quota.ClientQuotaEntity
import org.apache.kafka.common.quota.ClientQuotaFilter
import org.apache.kafka.common.resource.ResourcePattern
import org.apache.kafka.common.utils.Sanitizer
import org.apache.kafka.common.utils.Time
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

//support clusters 2.0.0 or greater

private val VERSION_0 = Version.of("0")
private val VERSION_2_2 = Version.of("2.2")
private val VERSION_2_3 = Version.of("2.3")
private val VERSION_2_4 = Version.of("2.4")
private val VERSION_2_6 = Version.of("2.6")

class KafkaManagementClientImpl(
    private val adminClient: AdminClient,
    private val readRequestTimeoutMs: Long,
    private val writeRequestTimeoutMs: Long,
    private val consumerSupplier: ClientFactory.ConsumerSupplier,
    private val recordReadSampler: RecordReadSampler,
    private val zookeeperConnectionResolver: ZookeeperConnectionResolver,
    eagerlyConnectToZookeeper: Boolean = false,
) : KafkaManagementClient {

    private val currentClusterVersionRef = AtomicReference<Version?>(null)

    // hold set of broker ids that are used by topic assignments, refresh it when topic's are refreshed
    // this is workaround to be aware of all nodes in cluster even if some nodes are down because
    // AdminClient.describeCluster().nodes() returns only currently online nodes
    private val topicAssignmentsUsedBrokerIdsRef = AtomicReference<Set<BrokerId>?>(null)
    private val knownBrokers = ConcurrentHashMap<BrokerId, ClusterBroker>()

    private val zkConnection = bootstrapClusterVersionAndZkConnection()
    private val zkClientLazy = lazy {
        KafkaZkClient.apply(
            zkConnection, false,
            readRequestTimeoutMs.toInt(), writeRequestTimeoutMs.toInt(),
            Int.MAX_VALUE, Time.SYSTEM,
            "",
            org.apache.zookeeper.client.ZKClientConfig(),
            "kr.zk.kafka.server",
            "SessionExpireListener",
            false,
        )
    }
    private val zkClient: KafkaZkClient by zkClientLazy

    init {
        if (eagerlyConnectToZookeeper) {
            zkClient.clusterId
        }
    }

    private fun bootstrapClusterVersionAndZkConnection(): String {
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
                if (ex != null) adminClient.close(Duration.ofMillis(writeRequestTimeoutMs))
            }
            .get()
        val zookeeperConnection = controllerConfig["zookeeper.connect"]?.value ?: ""
        val majorVersion = controllerConfig["inter.broker.protocol.version"]?.value
        majorVersion?.let { Version.parse(it) }?.also(currentClusterVersionRef::set)
        return zookeeperConnectionResolver.resolveZkConnection(zookeeperConnection)
    }

    private fun clusterVersion(): Version {
        return currentClusterVersionRef.get() ?: VERSION_0
    }

    override fun close() {
        adminClient.close(Duration.ofMillis(writeRequestTimeoutMs))
        recordReadSampler.close()
        if (zkClientLazy.isInitialized()) {
            zkClient.close()
        }
    }

    override fun test() {
        adminClient
            .listTopics(ListTopicsOptions().withReadTimeout())
            .names()
            .asCompletableFuture("test list topic names")
            .get()
        recordReadSampler.test()
    }

    override fun clusterInfo(identifier: KafkaClusterIdentifier): CompletableFuture<ClusterInfo> {
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

    override fun describeReplicas(): CompletableFuture<List<TopicPartitionReplica>> {
        return adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
            .nodes()
            .asCompletableFuture("describe replicas all nodes")
            .thenApply { nodes -> nodes.map { it.id() } }
            .thenCompose { brokerIds ->
                adminClient
                    .describeLogDirs(brokerIds, DescribeLogDirsOptions().withReadTimeout())
                    .allDescriptions()
                    .asCompletableFuture("describe replicas log dirs")
            }
            .thenApply { brokersReplicas ->
                brokersReplicas.flatMap { (broker, dirReplicas) ->
                    dirReplicas.flatMap { (rootDir, replicas) ->
                        replicas.replicaInfos().map { (topicPartition, replica) ->
                            TopicPartitionReplica(
                                rootDir = rootDir,
                                brokerId = broker,
                                topic = topicPartition.topic(),
                                partition = topicPartition.partition(),
                                sizeBytes = replica.size(),
                                offsetLag = replica.offsetLag(),
                                isFuture = replica.isFuture
                            )
                        }
                    }
                }
            }
    }

    override fun listReAssignments(): CompletableFuture<List<TopicPartitionReAssignment>> {
        if (clusterVersion() < VERSION_2_4) {
            val topicNames = listAllTopicNames().get()
            val result = runOperation("get current re-assignments in ZK") {
                val targetAssignments = zkClient.partitionReassignment
                    .toJavaMap()
                    .mapValues { it.value.toJavaList().cast<List<BrokerId>>() }
                val assignmentForTopics = zkClient.getFullReplicaAssignmentForTopics(
                    topicNames.toScalaList().toSet()
                )
                val partitionStates = zkClient.getTopicPartitionStates(assignmentForTopics.keys().toSeq())
                assignmentForTopics.toJavaMap()
                    .mapNotNull { (topicPartition, reAssignment) ->
                        val allReplicas = reAssignment.replicas().toJavaList().cast<List<BrokerId>>()
                        val isr = partitionStates.get(topicPartition)
                            .map { it.leaderAndIsr().isr().toJavaList().cast<List<BrokerId>>() }
                            .getOrElse { emptyList<BrokerId>() }
                        if (allReplicas.toSet() == isr.toSet()) {
                            return@mapNotNull null
                        }
                        val targets = targetAssignments[topicPartition] ?: emptyList()
                        TopicPartitionReAssignment(
                            topic = topicPartition.topic(),
                            partition = topicPartition.partition(),
                            addingReplicas = targets - isr,
                            removingReplicas = allReplicas - targets,
                            allReplicas = allReplicas
                        )
                    }
            }
            return CompletableFuture.completedFuture(result)
        }
        return adminClient.listPartitionReassignments(ListPartitionReassignmentsOptions().withReadTimeout())
            .reassignments()
            .asCompletableFuture("list partition reassignments")
            .thenApply { partitionReAssignments ->
                partitionReAssignments.map { (topicPartition, reAssignment) ->
                    TopicPartitionReAssignment(
                        topic = topicPartition.topic(),
                        partition = topicPartition.partition(),
                        addingReplicas = reAssignment.addingReplicas(),
                        removingReplicas = reAssignment.removingReplicas(),
                        allReplicas = reAssignment.replicas()
                    )
                }
            }
    }

    override fun listAllTopicNames(): CompletableFuture<List<TopicName>> {
        return adminClient
            .listTopics(ListTopicsOptions().listInternal(true).withReadTimeout())
            .names()
            .asCompletableFuture("list all topic names")
            .thenApply { it.sorted() }
    }

    override fun listAllTopics(): CompletableFuture<List<KafkaExistingTopic>> {
        return listAllTopicNames()
            .thenCompose { topicNames ->
                val topicsPartitionDescriptionsFuture = adminClient
                    .describeTopics(topicNames, DescribeTopicsOptions().withReadTimeout())
                    .allTopicNames()
                    .asCompletableFuture("describe all topics partitions")
                val topicsConfigPropertiesFuture = adminClient
                    .describeConfigs(
                        topicNames.map { ConfigResource(ConfigResource.Type.TOPIC, it) },
                        DescribeConfigsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("describe all topics configs")
                    .thenApply { resourceConfigs -> resourceConfigs.mapKeys { it.key.name() } }
                topicsPartitionDescriptionsFuture.thenCombine(topicsConfigPropertiesFuture) { topicsPartitionDescriptions, topicsConfigProperties ->
                    topicNames.map { topicName ->
                        val topicDescription = topicsPartitionDescriptions[topicName]
                            ?: throw KafkaClusterManagementException("Invalid response, missing topic '$topicName' in partition descriptions")
                        val topicConfig = topicsConfigProperties[topicName]
                            ?: throw KafkaClusterManagementException("Invalid response, missing topic '$topicName' in config descriptions")
                        KafkaExistingTopic(
                            name = topicName,
                            internal = topicDescription.isInternal,
                            config = topicConfig.entries()
                                .sortedBy { it.name() }
                                .associate { it.name() to it.toTopicConfigValue() },
                            partitionsAssignments = topicDescription.partitions().map { it.toPartitionAssignments() }
                        )
                    }
                }
            }
            .whenComplete { topics, _ ->
                if (topics != null) {
                    val usedReplicaBrokerIds = topics.asSequence()
                        .flatMap { it.partitionsAssignments.asSequence() }
                        .flatMap { it.replicasAssignments }
                        .map { it.brokerId }
                        .toSet()
                    topicAssignmentsUsedBrokerIdsRef.set(usedReplicaBrokerIds)
                }
            }
    }

    override fun createTopic(topic: KafkaTopicConfiguration): CompletableFuture<Unit> {
        return adminClient
            .createTopics(mutableListOf(topic.toNewTopic()), CreateTopicsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("create topic")
            .thenApply { }
    }

    override fun topicFullyExists(topicName: TopicName): CompletableFuture<Boolean> {
        val describeTopicOk = adminClient
            .describeTopics(mutableListOf(topicName), DescribeTopicsOptions().withReadTimeout())
            .allTopicNames().asCompletableFuture("try describe topic")
            .thenApply { true }.exceptionally { false }
        val describeTopicConfigOk = adminClient
            .describeConfigs(mutableListOf(ConfigResource(ConfigResource.Type.TOPIC, topicName)), DescribeConfigsOptions().withReadTimeout())
            .all().asCompletableFuture("try describe topic configs")
            .thenApply { true }.exceptionally { false }
        return CompletableFuture.allOf(describeTopicOk, describeTopicConfigOk)
            .thenApply { describeTopicOk.get() && describeTopicConfigOk.get() }
    }

    private fun partialUpdateTopicConfig(
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
        val alterConfigsResult = if (clusterVersion() < VERSION_2_3) {
            if (configResource.type() == ConfigResource.Type.BROKER) {
                runOperation("alter broker config") {
                    val adminZkClient = AdminZkClient(zkClient)
                    val brokerConfigs = adminZkClient.fetchEntityConfig("brokers", configResource.name())
                    alterConfigOps().forEach { alterOp ->
                        when (alterOp.opType()) {
                            AlterConfigOp.OpType.SET -> brokerConfigs[alterOp.configEntry().name()] = alterOp.configEntry().value()
                            AlterConfigOp.OpType.DELETE -> brokerConfigs.remove(alterOp.configEntry().name())
                            else -> throw UnsupportedOperationException("Unsupported operation type ${alterOp.opType()}")
                        }
                    }
                    adminZkClient.changeConfigs("brokers", configResource.name(), brokerConfigs)
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

    override fun updateTopicConfig(topicName: TopicName, updatingConfig: TopicConfigMap): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName),
            alterConfigs = { updatingConfig.toKafkaConfig() },
            alterConfigOps = { updatingConfig.toToTopicAlterOps() },
        )
    }

    override fun setBrokerConfig(brokerId: BrokerId, config: Map<String, String>): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.BROKER, brokerId.toString()),
            alterConfigs = { config.toKafkaConfig() },
            alterConfigOps = { config.toToAlterSetOps() },
        )
    }

    override fun unsetBrokerConfig(brokerId: BrokerId, configKeys: Set<String>): CompletableFuture<Unit> {
        return updateConfig(
            configResource = ConfigResource(ConfigResource.Type.BROKER, brokerId.toString()),
            alterConfigs = { configKeys.associateWith { null }.toKafkaConfig() },
            alterConfigOps = { configKeys.toToAlterUnsetOps() },
        )
    }

    override fun deleteTopic(topicName: TopicName): CompletableFuture<Unit> {
        return adminClient
            .deleteTopics(mutableListOf(topicName), DeleteTopicsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete topic")
            .thenApply { }
    }

    override fun addTopicPartitions(
        topicName: TopicName, totalPartitionsCount: Int, newPartitionsAssignments: Map<Partition, List<BrokerId>>
    ): CompletableFuture<Unit> {
        val newAssignments = newPartitionsAssignments.asSequence()
            .sortedBy { it.key }
            .map { it.value }
            .toList()
        val newPartitions = NewPartitions.increaseTo(totalPartitionsCount, newAssignments)
        return adminClient
            .createPartitions(
                mapOf(topicName to newPartitions),
                CreatePartitionsOptions().withWriteTimeout()
            )
            .all()
            .asCompletableFuture("add topic partitions")
            .thenApply { }
    }

    override fun reAssignPartitions(
        topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>, throttleBytesPerSec: Int
    ): CompletableFuture<Unit> = reAssignPartitions(mapOf(topicName to partitionsAssignments), throttleBytesPerSec)

    override fun reAssignPartitions(
        topicPartitionsAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>, throttleBytesPerSec: Int
    ): CompletableFuture<Unit> {
        val partitionsAssignments = topicPartitionsAssignments
            .flatMap { (topic, partitionAssignments) ->
                partitionAssignments.map { (partition, replicas) ->
                    TopicPartition(topic, partition) to replicas
                }
            }
            .toMap()

        runOperation("verify reassignments used brokers") {
            val brokerIds = partitionsAssignments.values.flatten().toSet()
            val allNodeIds = adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
                .nodes()
                .asCompletableFuture("describe cluster for re-assignments")
                .get().map { it.id() }.toSet()
            brokerIds.filter { it !in allNodeIds }.takeIf { it.isNotEmpty() }?.run {
                throw KafkaClusterManagementException("Unknown broker(s) used in assignments: $this")
            }
        }
        val currentPartitionAssignments = adminClient
            .describeTopics(topicPartitionsAssignments.keys, DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe re-assigning topics").get()
            .flatMap { (topic, description) ->
                description.partitions()
                    .map { it.toPartitionAssignments() }
                    .map { partitionsAssignments ->
                        val partitionReplicas = partitionsAssignments.replicasAssignments.map { it.brokerId }
                        TopicPartition(topic, partitionsAssignments.partition) to partitionReplicas
                    }
            }
            .toMap()
        runOperation("verify reassignments partitions") {
            val nonExistentPartitions = partitionsAssignments.keys.filter { it !in currentPartitionAssignments.keys }
            if (nonExistentPartitions.isNotEmpty()) {
                throw KafkaClusterManagementException("Trying to reassign non-existent topic partitions: $nonExistentPartitions")
            }
        }
        fun setupThrottle(currentReassignments: Map<TopicPartition, PartitionReassignment>) = runOperation("setup re-assignment throttle") {
            val throttleRate = ThrottleRate(throttleBytesPerSec.toLong(), throttleBytesPerSec.toLong())
            val topicsMoveMap = ReAssignmentSupport.calculateProposedMoveMap(
                currentReassignments, partitionsAssignments, currentPartitionAssignments
            )
            val leaderThrottlesMap = ReAssignmentSupport.calculateLeaderThrottles(topicsMoveMap)
            val followerThrottlesMap = ReAssignmentSupport.calculateFollowerThrottles(topicsMoveMap)
            val reassigningBrokersSet = ReAssignmentSupport.calculateReassigningBrokers(topicsMoveMap)

            val topicNames = leaderThrottlesMap.keys + followerThrottlesMap.keys
            topicNames.map { topic ->
                val topicThrottleConfig = mapOf(
                    LogConfig.LeaderReplicationThrottledReplicasProp() to leaderThrottlesMap[topic],
                    LogConfig.FollowerReplicationThrottledReplicasProp() to followerThrottlesMap[topic],
                ).filterValues { it != null }
                partialUpdateTopicConfig(topic, topicThrottleConfig)
            }.forEach { it.get() }
            reassigningBrokersSet.map { updateThrottleRate(it, throttleRate) }.forEach { it.get() }
        }
        return if (clusterVersion() < VERSION_2_4) {
            runOperation("execute re-assignment via ZK") {
                if (throttleBytesPerSec >= 0) {
                    setupThrottle(currentReassignments = emptyMap())
                }
                val newAssignmentsScala = partitionsAssignments
                    .mapValues { it.value.toScalaList().toSeq() }
                    .toScalaMap()
                zkClient.createPartitionReassignment(newAssignmentsScala.cast())
            }
            CompletableFuture.completedFuture(Unit)
        } else {
            val reassignments = partitionsAssignments.mapValues {
                Optional.of(NewPartitionReassignment(it.value))
            }
            if (throttleBytesPerSec >= 0) {
                val currentReassignments = adminClient.listPartitionReassignments().reassignments().get()
                setupThrottle(currentReassignments)
            }
            adminClient
                .alterPartitionReassignments(reassignments, AlterPartitionReassignmentsOptions().withWriteTimeout())
                .all()
                .asCompletableFuture("alter partition reassignments")
                .thenApply { }
        }
    }

    override fun updateThrottleRate(brokerId: BrokerId, throttleRate: ThrottleRate): CompletableFuture<Unit> {
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

    override fun verifyReAssignPartitions(topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>): String {
        val currentReAssignments = listReAssignments().get()
            .groupBy { it.topic }
            .mapValues { (_, partitionReAssignments) ->
                partitionReAssignments.associateBy { it.partition }
            }
        val currentTopicDescription = adminClient
            .describeTopics(listOf(topicName), DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe topics for reassignment verification")
            .get().getOrElse(topicName) {
                throw KafkaClusterManagementException("Failed to get current topic description for topic: '$topicName'")
            }
        val currentAssignments = currentTopicDescription.partitions()
            .map { it.toPartitionAssignments() }
            .toPartitionReplicasMap()
        val partitionStatuses = partitionsAssignments.mapValues { (partition, replicas) ->
            val currentReplicas = currentAssignments[partition] ?: emptyList()
            val reAssignment = currentReAssignments[topicName]?.get(partition)
            when {
                reAssignment != null || currentReplicas.size > replicas.size -> ReAssignmentStatus.IN_PROGRESS
                currentReplicas == replicas -> ReAssignmentStatus.COMPLETED
                else -> ReAssignmentStatus.FAILED
            }
        }
        val resultMsg = StringBuilder()
        partitionStatuses.forEach { (partition, status) ->
            val topicPartition = TopicPartition(topicName, partition)
            when (status) {
                ReAssignmentStatus.COMPLETED -> resultMsg.append("Reassignment of partition $topicPartition completed successfully\n")
                ReAssignmentStatus.FAILED -> resultMsg.append("Reassignment of partition $topicPartition failed\n")
                ReAssignmentStatus.IN_PROGRESS -> resultMsg.append("Reassignment of partition $topicPartition is still in progress\n")
            }
        }
        if (partitionStatuses.values.all { it == ReAssignmentStatus.COMPLETED }) {
            val topicConfig = adminClient.describeConfigs(
                listOf(ConfigResource(ConfigResource.Type.TOPIC, topicName)),
                DescribeConfigsOptions().withWriteTimeout()
            )
                .all().asCompletableFuture("describe topic configs for reassignment verification")
                .get().getOrElse(ConfigResource(ConfigResource.Type.TOPIC, topicName)) {
                    throw KafkaClusterManagementException("Failed to get current topic description for topic: '$topicName'")
                }
                .entries().associate { it.name() to it.toTopicConfigValue() }
                .filterValues { !it.default }
                .mapValues { it.value.value }
                .plus(
                    mapOf(
                        LogConfig.FollowerReplicationThrottledReplicasProp() to null,
                        LogConfig.LeaderReplicationThrottledReplicasProp() to null,
                    )
                )
            updateTopicConfig(topicName, topicConfig).get()
            resultMsg.append("Topic: Throttle was removed.\n")
        }
        if (currentReAssignments.isEmpty()) {
            clusterInfo("").get().nodeIds
                .map { updateThrottleRate(it, ThrottleRate.NO_THROTTLE) }
                .forEach { it.get() }
            resultMsg.append("Brokers: Throttle was removed.\n")
        } else {
            resultMsg.append("Brokers: Keeping throttle because of topics re-assigning ${currentReAssignments.keys}.\n")
        }
        return resultMsg.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.cast(): T = this as T

    override fun cancelReAssignments(topicName: TopicName, partitions: List<Partition>): CompletableFuture<Unit> {
        if (clusterVersion() < VERSION_2_4) {
            throw KafkaClusterManagementException("Unsupported operation for cluster version < $VERSION_2_4, current: ${clusterVersion()}")
        }
        val topicPartitions = partitions
            .associate { TopicPartition(topicName, it) to Optional.empty<NewPartitionReassignment>() }
        return adminClient
            .alterPartitionReassignments(topicPartitions, AlterPartitionReassignmentsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("cancel partition reassignments")
            .thenApply { }
    }

    override fun runPreferredReplicaElection(topicName: TopicName, partitions: List<Partition>) {
        val clusterVersion = clusterVersion()
        val topicPartitions = partitions.map { TopicPartition(topicName, it) }.toSet()
        when {
            clusterVersion < VERSION_2_2 -> runOperation("preferred replica election") {
                zkClient.createPreferredReplicaElection(topicPartitions.toScalaList().toSet())
            }
            else -> {
                adminClient
                    .electLeaders(
                        ElectionType.PREFERRED, topicPartitions, ElectLeadersOptions().withWriteTimeout()
                    )
                    .all().asCompletableFuture("run preferred leader election")
                    .get()
            }
        }
    }

    override fun topicsOffsets(topicNames: List<TopicName>): CompletableFuture<Map<TopicName, Map<Partition, PartitionOffsets>>> {
        fun combineTopicOffsetsResult(
            allTopicsPartitions: List<TopicPartition>,
            endOffsets: Map<TopicPartition, ListOffsetsResultInfo>,
            beginOffsets: Map<TopicPartition, ListOffsetsResultInfo>
        ): Map<TopicName, Map<Partition, PartitionOffsets>> {
            return allTopicsPartitions.groupBy { it.topic() }
                .mapValues { (_, partitions) ->
                    partitions.sortedBy { it.partition() }.associate {
                        val begin = beginOffsets[it]
                            ?: throw KafkistryClusterReadException("Could not read begin offset for $it")
                        val end = endOffsets[it]
                            ?: throw KafkistryClusterReadException("Could not read end offset for $it")
                        val partitionOffsets = PartitionOffsets(
                            begin = begin.offset(),
                            end = end.offset()
                        )
                        it.partition() to partitionOffsets
                    }
                }
        }
        return adminClient
            .describeTopics(topicNames, DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe topics for offests")
            .thenApply { topicsPartitionDescriptions ->
                topicsPartitionDescriptions.flatMap { (topicName, partitionsDescription) ->
                    val hasPartitionWithNoLeader = partitionsDescription.partitions().any { it.leader() == null }
                    if (hasPartitionWithNoLeader) {
                        emptyList()
                    } else {
                        partitionsDescription.partitions().map { TopicPartition(topicName, it.partition()) }
                    }
                }
            }
            .thenCompose { allTopicsPartitions ->
                adminClient
                    .listOffsets(
                        allTopicsPartitions.associateWith { OffsetSpec.latest() },
                        ListOffsetsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("list topics latest offsets")
                    .thenApply { endOffsets -> allTopicsPartitions to endOffsets }
            }
            .thenCompose { (allTopicsPartitions, endOffsets) ->
                adminClient
                    .listOffsets(
                        allTopicsPartitions.associateWith { OffsetSpec.earliest() },
                        ListOffsetsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("list topics earliest offsets")
                    .thenApply { beginOffsets ->
                        combineTopicOffsetsResult(allTopicsPartitions, endOffsets, beginOffsets)
                    }
            }
    }

    override fun consumerGroups(): CompletableFuture<List<ConsumerGroupId>> {
        return adminClient
            .listConsumerGroups(ListConsumerGroupsOptions().withReadTimeout())
            .valid()
            .asCompletableFuture("list consumer groups")
            .thenApply { groups ->
                groups.map { it.groupId() }.sorted()
            }
    }

    override fun consumerGroup(groupId: ConsumerGroupId): CompletableFuture<ConsumerGroup> {
        val groupDescriptionFuture = adminClient
            .describeConsumerGroups(listOf(groupId), DescribeConsumerGroupsOptions().withReadTimeout())
            .describedGroups()[groupId]!!
            .asCompletableFuture("describe consumer group")
        val topicPartitionOffsetsFuture = adminClient
            .listConsumerGroupOffsets(groupId, ListConsumerGroupOffsetsOptions().withReadTimeout())
            .partitionsToOffsetAndMetadata()
            .asCompletableFuture("list consumer group offsets")
            .thenApply { topicsOffsets -> topicsOffsets.mapValues { it.value?.offset() } }
        return groupDescriptionFuture.thenCombine(topicPartitionOffsetsFuture) { groupDescription, topicPartitionOffsets ->
            val members = groupDescription.members().map {
                ConsumerGroupMember(
                    memberId = it.consumerId(),
                    clientId = it.clientId(),
                    host = it.host()
                )
            }.sortedBy { it.memberId }
            val offsets = topicPartitionOffsets
                .mapNotNull { (tp, offset) ->
                    offset?.let { TopicPartitionOffset(tp.topic(), tp.partition(), it) }
                }
                .sortedBy { it.topic + it.partition }
            val assignments = groupDescription.members()
                .flatMap { member ->
                    member.assignment().topicPartitions().map {
                        TopicPartitionMemberAssignment(
                            topic = it.topic(),
                            partition = it.partition(),
                            memberId = member.consumerId(),
                        )
                    }
                }
                .sortedBy { it.topic + it.partition }
            ConsumerGroup(
                id = groupId,
                status = groupDescription.state().convert(),
                partitionAssignor = groupDescription.partitionAssignor(),
                members = members,
                offsets = offsets,
                assignments = assignments,
            )
        }
    }

    override fun deleteConsumer(groupId: ConsumerGroupId): CompletableFuture<Unit> {
        return adminClient
            .deleteConsumerGroups(listOf(groupId), DeleteConsumerGroupsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete consumer group")
            .thenApply { }
    }

    override fun deleteConsumerOffsets(
        groupId: ConsumerGroupId, topicPartitions: Map<TopicName, List<Partition>>
    ): CompletableFuture<Unit> {
        val topicPartitionsSet = topicPartitions.flatMap { (topic, partitions) ->
            partitions.map { TopicPartition(topic, it) }
        }.toSet()
        if (topicPartitionsSet.isEmpty()) {
            return CompletableFuture.completedFuture(Unit)
        }
        return adminClient
            .deleteConsumerGroupOffsets(groupId, topicPartitionsSet, DeleteConsumerGroupOffsetsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete consumer group offsets")
            .thenApply { }
    }

    override fun resetConsumerGroup(
        groupId: ConsumerGroupId,
        reset: GroupOffsetsReset
    ): CompletableFuture<GroupOffsetResetChange> {

        fun checkGroupState(consumerGroup: ConsumerGroup) {
            when (consumerGroup.status) {
                ConsumerGroupStatus.EMPTY, ConsumerGroupStatus.DEAD, ConsumerGroupStatus.UNKNOWN -> Unit
                else -> throw KafkaClusterManagementException(
                        "Aborting reset to consumer group's '$groupId' offset(s) because it need to be inactive, " +
                                "current state: " + consumerGroup.status
                )
            }
        }

        fun currentGroupOffsets(): Map<TopicPartition, Long> {
            return consumerSupplier.createNewConsumer { props ->
                props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
                props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            }.use { consumer ->
                val subscribeLatch = CountDownLatch(1)
                consumer.subscribe(reset.topics.map { it.topic }, object : ConsumerRebalanceListener {
                    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) = subscribeLatch.countDown()
                    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) = Unit
                })
                //do some polling which is needed for KafkaConsumer to assign offsets
                val consumedRecordsCounts = sequence {
                    var remainingAttempts = 10
                    while (remainingAttempts > 0) {
                        consumer.poll(Duration.ofSeconds(1)).also { yieldAll(it) }
                        val subscribed = subscribeLatch.await(1, TimeUnit.SECONDS)
                        if (subscribed) {
                            break
                        }
                        remainingAttempts--
                    }
                }.groupingBy { TopicPartition(it.topic(), it.partition()) }.eachCount()

                consumer.assignment().associateWith { topicPartition ->
                    val currentOffset = consumer.position(topicPartition, readTimeoutDuration())
                    val correction = consumedRecordsCounts[topicPartition] ?: 0
                    currentOffset - correction
                }
            }
        }

        fun resolveTopicPartitionSeeks(
            topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
        ): Map<TopicPartition, OffsetSeek> {
            return reset.topics
                .associateBy { it.topic }
                .mapValues { (topic, topicSeek) ->
                    val topicPartitions = topicsOffsets[topic]?.keys
                        ?: throw KafkaClusterManagementException("Did not get response offsets for topic '$topic'")
                    when (topicSeek.partitions) {
                        null -> topicPartitions.associate { TopicPartition(topic, it) to reset.seek }
                        else -> topicSeek.partitions.associate {
                            val topicPartition = TopicPartition(topic, it.partition)
                            if (it.partition !in topicPartitions) {
                                throw KafkaClusterManagementException("$topicPartition does not exist, can't perform offset reset")
                            }
                            topicPartition to (it.seek ?: reset.seek)
                        }
                    }
                }
                .flatMap { (_, partitionSeeks) ->
                    partitionSeeks.map { it.toPair() }
                }
                .associate { it }
        }

        fun resolveTargetOffsets(
                topicPartitionSeeks: Map<TopicPartition, OffsetSeek>,
                currentGroupOffsets: Map<TopicPartition, Long>
        ): CompletableFuture<Map<TopicPartition, Long>> {
            val lookupSeeks = mutableMapOf<TopicPartition, OffsetSpec>()
            val explicitOffsets = mutableMapOf<TopicPartition, Long>()
            val relativeSeeks = mutableMapOf<TopicPartition, Long>()
            val lookupClones = mutableMapOf<TopicPartition, ConsumerGroupId>()
            topicPartitionSeeks.forEach { (topicPartition, seek) ->
                when (seek.type) {
                    EARLIEST -> lookupSeeks[topicPartition] = OffsetSpec.earliest()
                    LATEST -> lookupSeeks[topicPartition] = OffsetSpec.latest()
                    TIMESTAMP -> lookupSeeks[topicPartition] = OffsetSpec.forTimestamp(seek.timestamp())
                    EXPLICIT -> explicitOffsets[topicPartition] = seek.offset()
                    RELATIVE -> relativeSeeks[topicPartition] = seek.offset()
                    CLONE -> lookupClones[topicPartition] = seek.cloneFromConsumerGroup()
                }
            }

            val lookupOffsetsFuture = if (lookupSeeks.isNotEmpty()) {
                adminClient
                    .listOffsets(lookupSeeks, ListOffsetsOptions().withReadTimeout())
                    .all()
                    .asCompletableFuture("reset offsets - list topic offsets")
                    .thenApply { topicOffsets -> topicOffsets.mapValues { it.value.offset() } }
                    .thenApply { topicOffsets ->
                        topicOffsets.mapValues { (topicPartition, offset) ->
                            topicPartitionSeeks[topicPartition]?.let {
                                when (it.type) {
                                    EARLIEST -> offset + it.offset()
                                    LATEST -> offset - it.offset()
                                    else -> null
                                }
                            } ?: offset
                        }
                    }
            } else {
                CompletableFuture.completedFuture(emptyMap())
            }

            val cloneOffsetsFutures = lookupClones.map { it }
                .groupBy ({ it.value }, {it.key})
                .map { (clonedGroup, neededTopicPartitions) ->
                    adminClient
                        .listConsumerGroupOffsets(clonedGroup, ListConsumerGroupOffsetsOptions().withReadTimeout())
                        .partitionsToOffsetAndMetadata()
                        .asCompletableFuture("reset offsets - list group offsets")
                        .thenApply { groupOffsets ->
                            neededTopicPartitions.associateWith {
                                groupOffsets[it]?.offset() ?: throw KafkaClusterManagementException(
                                    "Tried to clone offset from $it of group '$clonedGroup', " +
                                            "but that group have no committed offset for that topic partition"
                                )
                            }
                        }
                }

            val relativeOffsets = if (relativeSeeks.isNotEmpty()) {
                relativeSeeks.mapValues { (topicPartition, seek) ->
                    val currentOffset = currentGroupOffsets[topicPartition]
                        ?: throw KafkaClusterManagementException(
                            "Can't perform relative seek for topic partition not assigned to consumer group: $topicPartition, " +
                                    "there might be other active consumer in group"
                        )
                    currentOffset + seek
                }
            } else {
                emptyMap()
            }

            return CompletableFuture.allOf(lookupOffsetsFuture, *cloneOffsetsFutures.toTypedArray()).thenApply {
                val clonedOffsets = cloneOffsetsFutures.map { it.get() }
                    .takeIf { it.isNotEmpty() }
                    ?.reduce { acc, partitionOffsets ->  acc + partitionOffsets }
                    .orEmpty()
                explicitOffsets + lookupOffsetsFuture.get() + relativeOffsets + clonedOffsets
            }
        }

        fun ensureTargetOffsetsWithinBounds(
                targetOffsets: Map<TopicPartition, Long>,
                topicsOffsets: Map<TopicName, Map<Partition, PartitionOffsets>>
        ): Map<TopicPartition, Long> {
            return targetOffsets.mapValues { (topicPartition, offset) ->
                val partitionOffsets = topicsOffsets[topicPartition.topic()]
                        ?.get(topicPartition.partition())
                        ?: return@mapValues offset
                if (offset == -1L) {
                    //timestamp lookup returns -1 if no newer message than provided timestamp, set it to end
                    partitionOffsets.end
                } else {
                    //ensure final target offset is (>= begin) and (<= end)
                    offset.coerceIn(partitionOffsets.begin..partitionOffsets.end)
                }
            }
        }

        fun doResetConsumerGroup(
            topicPartitionTargetOffsets: Map<TopicPartition, Long>
        ): CompletableFuture<Void> {
            val offsets = topicPartitionTargetOffsets.mapValues { OffsetAndMetadata(it.value) }
            return adminClient
                .alterConsumerGroupOffsets(groupId, offsets, AlterConsumerGroupOffsetsOptions().withWriteTimeout())
                .all()
                .asCompletableFuture("reset offsets - alter offsets")
        }

        fun constructResult(
            currentOffsets: Map<TopicPartition, Long>,
            targetOffsets: Map<TopicPartition, Long>,
            currentConsumerGroup: ConsumerGroup,
        ): GroupOffsetResetChange {
            val newlyInitialized = currentConsumerGroup.status in setOf(
                ConsumerGroupStatus.DEAD, ConsumerGroupStatus.UNKNOWN
            )
            val changes = targetOffsets.map { (topicPartition, targetOffset) ->
                TopicPartitionOffsetChange(
                    topic = topicPartition.topic(),
                    partition = topicPartition.partition(),
                    offset = targetOffset,
                    delta = currentOffsets[topicPartition]
                        ?.takeUnless { newlyInitialized }
                        ?.let { targetOffset - it }
                )
            }.sortedBy { it.topic + it.partition }
            return GroupOffsetResetChange(
                groupId = groupId,
                changes = changes,
                totalSkip = changes.mapNotNull { it.delta }.filter { it > 0 }.sum(),
                totalRewind = -changes.mapNotNull { it.delta }.filter { it < 0 }.sum()
            )
        }

        val hasPartitionsToReset = reset.topics.any { it.partitions == null || it.partitions.isNotEmpty() }
        if (!hasPartitionsToReset) {
            throw KafkaClusterManagementException("Can't perform reset, no topic/partitions selected")
        }
        val topicsOffsets = topicsOffsets(reset.topics.map { it.topic })
        val consumerGroupFuture = consumerGroup(groupId)
        val currentGroupOffsets: Map<TopicPartition, Long> by lazy { currentGroupOffsets() }
        return CompletableFuture.allOf(topicsOffsets, consumerGroupFuture)
            .thenApply { checkGroupState(consumerGroupFuture.get()) }
            .thenApply { resolveTopicPartitionSeeks(topicsOffsets.get()) }
            .thenCompose { resolveTargetOffsets(it, currentGroupOffsets) }
            .thenApply { ensureTargetOffsetsWithinBounds(it, topicsOffsets.get()) }
            .thenCompose { targetOffsets -> doResetConsumerGroup(targetOffsets).thenApply { targetOffsets } }
            .thenApply { targetOffsets ->
                constructResult(currentGroupOffsets, targetOffsets, consumerGroupFuture.get())
            }
    }

    override fun listAcls(): CompletableFuture<List<KafkaAclRule>> {
        return adminClient
            .describeAcls(AclBindingFilter.ANY, DescribeAclsOptions().withReadTimeout())
            .values()
            .asCompletableFuture("list acls")
            .thenApply { aclBindings -> aclBindings.map { it.toAclRule() } }
    }

    override fun createAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        val aclBindings = acls.map { it.toAclBinding() }
        return adminClient.createAcls(aclBindings, CreateAclsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("create acls")
            .thenApply { }
    }

    override fun deleteAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        val aclFilters = acls.map { it.toAclBinding().toFilter() }
        return adminClient.deleteAcls(aclFilters, DeleteAclsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete acls")
            .thenApply { }
    }

    override fun sampleRecords(
        topicPartitionOffsets: TopicPartitionOffsets,
        samplingPosition: SamplingPosition,
        recordVisitor: RecordVisitor,
    ): Unit = recordReadSampler.readSampleRecords(
        topicPartitionOffsets, samplingPosition, recordVisitor
    )

    override fun listQuotas(): CompletableFuture<List<ClientQuota>> {
        if (clusterVersion() < VERSION_2_6) {
            return runOperation("list entity quotas") {
                val allEntityQuotas = fetchQuotasFromZk().map { (entity, quotas) ->
                    ClientQuota(entity, quotas.toQuotaProperties())
                }
                CompletableFuture.completedFuture(allEntityQuotas)
            }
        }
        return adminClient
            .describeClientQuotas(ClientQuotaFilter.all(), DescribeClientQuotasOptions().withReadTimeout())
            .entities()
            .asCompletableFuture("describe client quotas")
            .thenApply { entityQuotas ->
                entityQuotas.map { (entity, quotas) ->
                    ClientQuota(entity.toQuotaEntity(), quotas.toQuotaProperties())
                }
            }
    }

    private fun fetchQuotasFromZk(): Map<QuotaEntity, Map<String, Double>> {
        val adminZkClient = AdminZkClient(zkClient)
        fun Properties.toQuotaValues(): Map<String, Double> = this
            .mapKeys { it.key.toString() }
            .filterKeys { QuotaConfigs.isClientOrUserConfig(it) }
            .mapValues { it.value.toString().toDouble() }
        fun String.deSanitize(): String = when (this) {
            QuotaEntity.DEFAULT -> this
            else -> Sanitizer.desanitize(this)
        }
        val userConfig = adminZkClient.fetchAllEntityConfigs(ConfigType.User()).toJavaMap()
            .mapKeys { QuotaEntity(user = it.key.deSanitize()) }
        val clientConfig = adminZkClient.fetchAllEntityConfigs(ConfigType.Client()).toJavaMap()
            .mapKeys { QuotaEntity(clientId = it.key.deSanitize()) }
        val userClientConfig = adminZkClient.fetchAllChildEntityConfigs(ConfigType.User(), ConfigType.Client()).toJavaMap()
            .mapKeys {
                val (user,_,client) = it.key.split("/")
                QuotaEntity(user = user.deSanitize(), clientId = client.deSanitize())
            }
        return (userConfig + clientConfig + userClientConfig)
            .filterValues { it.isNotEmpty() }
            .mapValues { it.value.toQuotaValues() }
    }

    override fun setClientQuotas(quotas: List<ClientQuota>): CompletableFuture<Unit> {
        val quotaAlterations = quotas.map { it.toQuotaAlteration() }
        return alterQuotas(quotaAlterations)
    }

    override fun removeClientQuotas(quotaEntities: List<QuotaEntity>): CompletableFuture<Unit> {
        val quotaAlterations = quotaEntities.map {
            ClientQuotaAlteration(it.toClientQuotaEntity(), QuotaProperties.NONE.toQuotaAlterationOps())
        }
        return alterQuotas(quotaAlterations)
    }

    private fun alterQuotas(quotaAlterations: List<ClientQuotaAlteration>): CompletableFuture<Unit> {
        if (clusterVersion() < VERSION_2_6) {
            val adminZkClient = AdminZkClient(zkClient)
            quotaAlterations.forEach {
                runOperation("alter entity quotas") {
                    alterQuotasOnZk(adminZkClient, it)
                }
            }
            return CompletableFuture.completedFuture(Unit)
        }
        return adminClient
            .alterClientQuotas(quotaAlterations, AlterClientQuotasOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("alter client quotas")
            .thenApply {  }
    }

    private fun alterQuotasOnZk(adminZkClient: AdminZkClient, alteration: ClientQuotaAlteration) {
        fun String?.sanitize() = when (this) {
            null -> QuotaEntity.DEFAULT
            else -> Sanitizer.sanitize(this)
        }
        val sanitizedEntityProps = alteration.entity().entries().mapValues { it.value.sanitize() }
        val user = sanitizedEntityProps[ClientQuotaEntity.USER]
        val clientId = sanitizedEntityProps[ClientQuotaEntity.CLIENT_ID]
        val (path, configType) = when {
            user != null && clientId != null -> "$user/clients/$clientId" to ConfigType.User()
            user != null && clientId == null -> user to ConfigType.User()
            user == null && clientId != null -> clientId to ConfigType.Client()
            else -> throw IllegalArgumentException("Both user and clientId are null")
        }
        val props = adminZkClient.fetchEntityConfig(configType, path)
        alteration.ops().forEach { op ->
            when (op.value()) {
                null -> props.remove(op.key())
                else -> {
                    val value = when (op.key()) {
                        QuotaConfigs.PRODUCER_BYTE_RATE_OVERRIDE_CONFIG -> op.value().toLong().toString()
                        QuotaConfigs.CONSUMER_BYTE_RATE_OVERRIDE_CONFIG -> op.value().toLong().toString()
                        QuotaConfigs.REQUEST_PERCENTAGE_OVERRIDE_CONFIG -> op.value().toString()
                        else -> throw IllegalArgumentException("Unknown quota property key '${op.key()}'")
                    }
                    props[op.key()] = value
                }
            }
        }
        adminZkClient.changeConfigs(configType, path, props)
    }

    private fun KafkaTopicConfiguration.toNewTopic() = NewTopic(name, partitionsReplicas).apply {
        configs(this@toNewTopic.config)
    }

    private fun ConfigEntry.toTopicConfigValue() = ConfigValue(
        value = value(),
        default = isDefault,
        readOnly = isReadOnly,
        sensitive = isSensitive,
        source = source()
    )

    private fun TopicPartitionInfo.toPartitionAssignments(): PartitionAssignments {
        val leaderBrokerId = leader()?.id()
        val inSyncBrokerIds = isr().map { it.id() }.toSet()
        return PartitionAssignments(
            partition = partition(),
            replicasAssignments = replicas().mapIndexed { index, broker ->
                ReplicaAssignment(
                    brokerId = broker.id(),
                    leader = leaderBrokerId == broker.id(),
                    inSyncReplica = broker.id() in inSyncBrokerIds,
                    preferredLeader = index == 0,
                    rank = index
                )
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
        .plus(TOPIC_CONFIG_PROPERTIES
            .filter { it !in this.keys }
            .map { AlterConfigOp(ConfigEntry(it, null), AlterConfigOp.OpType.DELETE) }
        )

    private fun ClientQuotaEntity.toQuotaEntity(): QuotaEntity {
        val entries = entries().mapValues { it.value.orDefault() }
        return QuotaEntity(
            user = entries[ClientQuotaEntity.USER],
            clientId = entries[ClientQuotaEntity.CLIENT_ID],
        )
    }

    private fun String?.orDefault() = this ?: QuotaEntity.DEFAULT
    private fun String.orNullIfDefault() = this.takeIf { it != QuotaEntity.DEFAULT }

    private fun QuotaEntity.toClientQuotaEntity() = ClientQuotaEntity(
        listOfNotNull(
            user?.let { ClientQuotaEntity.USER to it.orNullIfDefault() },
            clientId?.let { ClientQuotaEntity.CLIENT_ID to it.orNullIfDefault() },
        ).toMap()
    )

    private fun Map<String, Double>.toQuotaProperties(): QuotaProperties {
        return QuotaProperties(
            producerByteRate = this[QuotaConfigs.PRODUCER_BYTE_RATE_OVERRIDE_CONFIG]?.toLong(),
            consumerByteRate = this[QuotaConfigs.CONSUMER_BYTE_RATE_OVERRIDE_CONFIG]?.toLong(),
            requestPercentage = this[QuotaConfigs.REQUEST_PERCENTAGE_OVERRIDE_CONFIG],
        )
    }

    private fun QuotaProperties.toQuotaAlterationOps(): List<ClientQuotaAlteration.Op> {
        return listOf(
            ClientQuotaAlteration.Op(QuotaConfigs.PRODUCER_BYTE_RATE_OVERRIDE_CONFIG, producerByteRate?.toDouble()),
            ClientQuotaAlteration.Op(QuotaConfigs.CONSUMER_BYTE_RATE_OVERRIDE_CONFIG, consumerByteRate?.toDouble()),
            ClientQuotaAlteration.Op(QuotaConfigs.REQUEST_PERCENTAGE_OVERRIDE_CONFIG, requestPercentage),
        )
    }

    private fun ClientQuota.toQuotaAlteration() = ClientQuotaAlteration(
        entity.toClientQuotaEntity(), properties.toQuotaAlterationOps()
    )

    private fun ExistingConfig.extractThrottleRate(): ThrottleRate {
        val dynamicConf = DynamicConfig.`Broker$`.`MODULE$`
        return ThrottleRate(
                leaderRate = get(dynamicConf.LeaderReplicationThrottledRateProp())?.value?.toLongOrNull(),
                followerRate = get(dynamicConf.FollowerReplicationThrottledRateProp())?.value?.toLongOrNull(),
                alterDirIoRate = get(dynamicConf.ReplicaAlterLogDirsIoMaxBytesPerSecondProp())?.value?.toLongOrNull(),
        )
    }

    private fun <T : AbstractOptions<T>> T.withReadTimeout(): T = also {
        timeoutMs(readRequestTimeoutMs.toInt())
    }

    private fun <T : AbstractOptions<T>> T.withWriteTimeout(): T = also {
        timeoutMs(writeRequestTimeoutMs.toInt())
    }

    private fun readTimeoutDuration() = Duration.ofMillis(readRequestTimeoutMs)

    private fun <T> KafkaFuture<T>.asCompletableFuture(ofWhat: String): CompletableFuture<T> {
        return CompletableFuture<T>().also {
            whenComplete { result, ex ->
                when (ex) {
                    null -> it.complete(result)
                    else -> it.completeExceptionally(
                        KafkaClusterManagementException("Execution exception for: $ofWhat", ex)
                    )
                }
            }
        }
    }

    private fun AclBinding.toAclRule(): KafkaAclRule {
        return KafkaAclRule(
            principal = entry().principal(),
            resource = AclResource(
                type = pattern().resourceType().convert(),
                name = pattern().name(),
                namePattern = pattern().patternType().convert()
            ),
            host = entry().host(),
            operation = AclOperation(
                type = entry().operation().convert(),
                policy = entry().permissionType().convert()
            )
        )
    }

    private fun KafkaAclRule.toAclBinding(): AclBinding {
        return AclBinding(
            ResourcePattern(
                resource.type.convert(),
                resource.name,
                resource.namePattern.convert()
            ),
            AccessControlEntry(
                principal,
                host,
                operation.type.convert(),
                operation.policy.convert()
            )
        )
    }

    private inline fun <E1 : Enum<E1>, reified E2 : Enum<E2>> E1.convert(): E2 {
        return java.lang.Enum.valueOf(E2::class.java, name)
    }

}

fun <T> runOperation(what: String, operation: () -> T): T {
    return try {
        operation()
    } catch (ex: Throwable) {
        throw KafkaClusterManagementException("Exception while performing: $what", ex)
    }
}

private enum class ReAssignmentStatus {
    COMPLETED, IN_PROGRESS, FAILED
}

