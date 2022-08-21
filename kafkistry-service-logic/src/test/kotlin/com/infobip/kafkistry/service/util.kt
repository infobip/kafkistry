package com.infobip.kafkistry.service

import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DEFAULT_CONFIG
import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.assertj.core.api.SoftAssertions
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.git.GitRepository
import com.infobip.kafkistry.service.topic.InspectionResultType.*
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.InspectionResultType.Companion.WRONG_CONFIG
import com.infobip.kafkistry.service.topic.InspectionResultType.Companion.WRONG_PARTITION_COUNT
import com.infobip.kafkistry.service.topic.InspectionResultType.Companion.WRONG_REPLICATION_FACTOR
import java.time.Duration
import java.util.concurrent.TimeUnit

fun assertAll(block: SoftAssertions.() -> Unit) {
    SoftAssertions.assertSoftly { it.block() }
}

fun newTopic(
    name: String = "test-topic-name",
    owner: String = "test-owner",
    description: String = "test-description",
    producer: String = "test-producer",
    presence: Presence = Presence(PresenceType.ALL_CLUSTERS, null),
    properties: TopicProperties = TopicProperties(1, 1),
    config: TopicConfigMap = mapOf(),
    perClusterProperties: Map<KafkaClusterIdentifier, TopicProperties> = emptyMap(),
    perClusterConfigOverrides: Map<KafkaClusterIdentifier, TopicConfigMap> = emptyMap(),
    perTagProperties: Map<Tag, TopicProperties> = emptyMap(),
    perTagConfigOverrides: Map<Tag, TopicConfigMap> = emptyMap(),
    emptyOwnerDescriptionProducer: Boolean = false
) = TopicDescription(
        name,
        if (emptyOwnerDescriptionProducer) "" else owner,
        if (emptyOwnerDescriptionProducer) "" else description,
        null,
        if (emptyOwnerDescriptionProducer) "" else producer,
        presence,
        properties,
        config,
        perClusterProperties,
        perClusterConfigOverrides,
        perTagProperties,
        perTagConfigOverrides,
)

fun newCluster(
    clusterId: String = "test-cluster-cluster-id",
    identifier: String = "test-cluster-identifier",
    connection: String = "test-connection",
    ssl: Boolean = false,
    sasl: Boolean = false,
    tags: List<Tag> = emptyList(),
) = KafkaCluster(
    clusterId = clusterId, identifier = identifier, connectionString = connection,
    sslEnabled = ssl, saslEnabled = sasl,
    tags = tags
)

fun newQuota(
    entity: QuotaEntity = QuotaEntity.user("test-user"),
    owner: String = "test-owner",
    presence: Presence = Presence.ALL,
    properties: QuotaProperties = QuotaProperties(producerByteRate = 1024_000, consumerByteRate = 2048_000, requestPercentage = 150.0),
    clusterOverrides: Map<KafkaClusterIdentifier, QuotaProperties> = emptyMap(),
    tagOverrides: Map<Tag, QuotaProperties> = emptyMap(),
) = QuotaDescription(entity, owner, presence, properties, clusterOverrides, tagOverrides)

fun KafkaCluster.newState(
    vararg topics: TopicDescription,
    stateType: StateType = StateType.VISIBLE,
    nonDefaultConfig: TopicConfigMap = emptyMap(),
    defaultConfig: TopicConfigMap = emptyMap(),
    numBrokers: Int = 6,
    clusterConfig: ExistingConfig = emptyMap(),
    acls: List<KafkaAclRule> = emptyList(),
    securityEnabled: Boolean = false,
    existingTopicsGenerator: (TopicDescription) -> KafkaExistingTopic = {
        it.newExistingKafkaTopic(identifier, nonDefaultConfig, defaultConfig, numBrokers)
    },
) = StateData(
        stateType = stateType,
        clusterIdentifier = identifier,
        stateTypeName = "cluster_state",
        lastRefreshTime = System.currentTimeMillis(),
        value = KafkaClusterState(
                clusterInfo = newClusterInfo(
                        clusterId = clusterId,
                        identifier = identifier,
                        config = clusterConfig,
                        perBrokerConfig = (1..numBrokers).associateWith { clusterConfig },
                        perBrokerThrottle = (1..numBrokers).associateWith { ThrottleRate.NO_THROTTLE },
                        nodeIds = (1..numBrokers).toList(),
                        onlineNodeIds = (1..numBrokers).toList(),
                        connectionString = connectionString,
                        securityEnabled = securityEnabled
                ),
                topics = topics.map { existingTopicsGenerator(it) },
                acls = acls
        ).takeIf { stateType == StateType.VISIBLE }
)

fun newClusterInfo(
    clusterId: String = "clusterId",
    identifier: KafkaClusterIdentifier = "identifier",
    config: ExistingConfig = emptyMap(),
    perBrokerConfig: Map<BrokerId, ExistingConfig> = emptyMap(),
    perBrokerThrottle: Map<BrokerId, ThrottleRate> = emptyMap(),
    controllerId: BrokerId = 1,
    nodeIds: List<BrokerId> = emptyList(),
    onlineNodeIds: List<BrokerId> = emptyList(),
    connectionString: String = "broker:9092",
    zookeeperConnectionString: String = "zk_conn",
    clusterVersion: Version = Version.of("1.0"),
    securityEnabled: Boolean = false,
) = ClusterInfo(
    clusterId = clusterId,
    identifier = identifier,
    config = config,
    perBrokerConfig = perBrokerConfig,
    perBrokerThrottle = perBrokerThrottle,
    controllerId = controllerId,
    nodeIds = nodeIds,
    onlineNodeIds = onlineNodeIds,
    brokers = nodeIds.map { ClusterBroker(it, "broker-$it", 9092) },
    connectionString = connectionString,
    zookeeperConnectionString = zookeeperConnectionString,
    clusterVersion = clusterVersion,
    securityEnabled = securityEnabled,
)

fun TopicDescription.newExistingKafkaTopic(
    clusterIdentifier: KafkaClusterIdentifier,
    nonDefaultConfig: TopicConfigMap,
    defaultConfig: TopicConfigMap,
    numClusterBrokers: Int = 3
) = newExistingKafkaTopic(
    ClusterRef(clusterIdentifier, emptyList()), nonDefaultConfig, defaultConfig, numClusterBrokers
)

fun TopicDescription.newExistingKafkaTopic(
    clusterRef: ClusterRef,
    nonDefaultConfig: TopicConfigMap,
    defaultConfig: TopicConfigMap,
    numClusterBrokers: Int = 3
) = KafkaExistingTopic(
        name = name,
        internal = false,
        config = defaultValues.toMap()
                .mapValues { (_, value) ->
                    ConfigValue(value, default = true, readOnly = false, sensitive = false, DEFAULT_CONFIG)
                }
                +
                defaultConfig.toMap()
                        .mapValues { (_, value) ->
                            ConfigValue(value, default = true, readOnly = false, sensitive = false, DEFAULT_CONFIG)
                        }
                +
                nonDefaultConfig.toMap()
                        .mapValues { (_, value) ->
                            ConfigValue(value, default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG)
                        }
                +
                configForCluster(clusterRef)
                        .mapValues { (_, value) ->
                            ConfigValue(value, default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG
                            )
                        },
        partitionsAssignments = PartitionsReplicasAssignor()
                .assignNewPartitionReplicas(
                        existingAssignments = emptyMap(),
                        allBrokers = (1..numClusterBrokers).toList(),
                        numberOfNewPartitions = propertiesForCluster(clusterRef).partitionCount,
                        replicationFactor = propertiesForCluster(clusterRef).replicationFactor,
                        existingPartitionLoads = emptyMap(),
                        clusterBrokersLoad = emptyMap()
                )
                .newAssignments
                .map { (partition, brokerIds) ->
                    PartitionAssignments(
                            partition = partition,
                            replicasAssignments = brokerIds.mapIndexed { index, brokerId ->
                                ReplicaAssignment(brokerId, index == 0, true, index == 0, index)
                            }
                    )
                }
)

private val defaultValues = mapOf(
        "compression.type" to "producer",
        "leader.replication.throttled.replicas" to "",
        "message.downconversion.enable" to "true",
        "segment.jitter.ms" to "0",
        "cleanup.policy" to "delete",
        "flush.ms" to "9223372036854775807",
        "follower.replication.throttled.replicas" to "",
        "segment.bytes" to "524288",
        "retention.ms" to "604800000",
        "flush.messages" to "9223372036854775807",
        "file.delete.delay.ms" to "60000",
        "max.message.bytes" to "1000012",
        "min.compaction.lag.ms" to "0",
        "message.timestamp.type" to "CreateTime",
        "preallocate" to "false",
        "min.cleanable.dirty.ratio" to "0.5",
        "index.interval.bytes" to "4096",
        "unclean.leader.election.enable" to "false",
        "retention.bytes" to "-1",
        "delete.retention.ms" to "86400000",
        "segment.ms" to "604800000",
        "message.timestamp.difference.max.ms" to "9223372036854775807",
        "segment.index.bytes" to "10485760"
)

private fun String.toIssueType(): InspectionResultType = when (this) {
    "replication-factor" -> WRONG_REPLICATION_FACTOR
    "partition-count" -> WRONG_PARTITION_COUNT
    else -> WRONG_CONFIG
}

fun wrongValue(key: String, actual: String, expected: String) = WrongValueAssertion(key.toIssueType(), key, false, expected, actual)

fun wrongValueDefaultExpected(key: String, actual: String, expected: String) = WrongValueAssertion(key.toIssueType(), key, true, expected, actual)

fun newWizardAnswers(
        topicNameSuffix: String = "test-name",
        purpose: String = "no test purpose",
        teamName: String = "Test_Team",
        producerServiceName: String = "test-produces",
        messagesPerDay: Long = 3600L * 24,
        avgMessageSizeBytes: Int = 4096,
        retentionDays: Int = 7,
        highAvailability: HighAvailability = HighAvailability.BASIC,
        presence: Presence = Presence(PresenceType.ALL_CLUSTERS)
) = TopicCreationWizardAnswers(
        purpose = purpose,
        teamName = teamName,
        producerServiceName = producerServiceName,
        topicNameMetadata = TopicNameMetadata(attributes = mapOf("name" to topicNameSuffix)),
        resourceRequirements = ResourceRequirements(
                messagesRate = MessagesRate(messagesPerDay, ScaleFactor.ONE, RateUnit.MSG_PER_DAY),
                messagesRateOverrides = emptyMap(),
                messagesRateTagOverrides = emptyMap(),
                avgMessageSize = MsgSize(avgMessageSizeBytes, BytesUnit.B),
                retention = DataRetention(retentionDays, TimeUnit.DAYS)
        ),
        highAvailability = highAvailability,
        presence = presence
)

fun ClusterInfo.toKafkaCluster() = KafkaCluster(
        identifier, clusterId, connectionString, detectIsSslConnection(connectionString), false, emptyList()
)

fun detectIsSslConnection(connectionString: String): Boolean {
    return when {
        ":9092" in connectionString -> false
        ":9093" in connectionString -> true
        else -> false
    }
}

fun GitRepository.updateOrInitRepositoryTest() {
    return javaClass.getDeclaredMethod("updateOrInitRepository").let {
        it.isAccessible = true
        it.invoke(this)
    }
}

fun aclsFor(p: PrincipalId) = PrincipalAclRules(p, "test-desc", "test", emptyList())

fun <K, V> KafkaConsumer<K, V>.poolAll(maxIterations: Int = 10): List<ConsumerRecord<K, V>> {
    val result = mutableListOf<ConsumerRecord<K, V>>()
    var i = 0
    while (true) {
        i++
        val records = poll(Duration.ofSeconds(1))
        if (records.isEmpty || i >= maxIterations) {
            break
        }
        records.forEach { result.add(it) }
    }
    return result
}

fun TopicsRegistryService.createTopic(description: TopicDescription) = createTopic(description, UpdateContext("test msg"))

fun KafkaManagementClient.deleteAllOnCluster() {
    val topics = listAllTopicNames().get()
    topics.forEach { deleteTopic(it).get() }
    val groups = consumerGroups().get()
    groups.forEach { deleteConsumer(it).exceptionally {  }.get() }
    try {
        val acls = listAcls().get()
        deleteAcls(acls).get()
    } catch (_: Exception) {
        //security disabled, ignore
    }
    //try to ensure that all topics are deleted
    for (iteration in 1..10) {
        val constantlyReportsAllDeleted = (1..6).all {
            Thread.sleep(100)
            val noTopics = try {
                listAllTopics().get().isEmpty()
            } catch (e: Exception) {
                false
            }
            val noConsumerGroups = consumerGroups().get().isEmpty()
            val noAcls = try {
                listAcls().get().isEmpty()
            } catch (_: Exception) {
                true
            }
            noTopics && noConsumerGroups && noAcls
        }
        if (constantlyReportsAllDeleted) {
            break
        }
        Thread.sleep(1000)
    }

}

fun Map<Partition, List<BrokerId>>.toOkPartitionAssignments(): List<PartitionAssignments> {
    return map { (partition, brokerIds) ->
        PartitionAssignments(
            partition = partition,
            replicasAssignments = brokerIds.mapIndexed { index, brokerId ->
                ReplicaAssignment(
                    brokerId = brokerId,
                    leader = index == 0,
                    inSyncReplica = true,
                    preferredLeader = index == 0,
                    rank = index
                )
            }
        )
    }
}

fun Any?.asTopicConfigValue() = ConfigValue(
    value = this?.toString(), default = false, readOnly = false, sensitive = false, source = DYNAMIC_TOPIC_CONFIG
)