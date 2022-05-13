package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.BulkReAssignmentOptions.TopicBy.MIGRATION_BYTES
import com.infobip.kafkistry.service.topic.BulkReAssignmentOptions.TopicBy.RE_ASSIGNED_PARTITIONS_COUNT
import com.infobip.kafkistry.service.topic.InspectionResultType.*
import com.infobip.kafkistry.service.topic.ReBalanceMode.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.OverridesMinimizer
import com.infobip.kafkistry.service.generator.PartitionLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.resources.RequiredResourcesInspector
import com.infobip.kafkistry.service.topic.validation.TopicConfigurationValidator
import com.infobip.kafkistry.service.topic.validation.rules.ClusterMetadata
import com.infobip.kafkistry.service.topic.wizard.TopicWizardConfigGenerator
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Service
class OperationSuggestionService(
    private val clustersRegistry: ClustersRegistryService,
    private val topicsRegistryService: TopicsRegistryService,
    private val inspectionService: TopicsInspectionService,
    private val clusterStateProvider: KafkaClustersStateProvider,
    private val rulesValidator: TopicConfigurationValidator,
    private val configValueInspector: ConfigValueInspector,
    private val overridesMinimizer: OverridesMinimizer,
    private val partitionsAssignor: PartitionsReplicasAssignor,
    private val resourcesInspector: RequiredResourcesInspector,
    private val topicWizardConfigGenerator: TopicWizardConfigGenerator,
    private val replicaDirsService: ReplicaDirsService,
) {

    fun suggestDefaultTopic(): TopicDescription {
        return TopicDescription(
            name = "",
            owner = "",
            description = "",
            resourceRequirements = null,
            producer = "",
            presence = Presence(PresenceType.INCLUDED_CLUSTERS, emptyList()),
            properties = TopicProperties(1, 2),
            config = emptyMap(),
            perClusterProperties = emptyMap(),
            perClusterConfigOverrides = emptyMap(),
            perTagProperties = emptyMap(),
            perTagConfigOverrides = emptyMap(),
        )
    }

    fun suggestTopicImport(topicName: TopicName): TopicDescription {
        val perClusterInfos = inspectionService.inspectUnknownTopics()
            .find { it.topicName == topicName }
            ?.statusPerClusters
            ?.map { it.toDefinedTopicClusterStatus() }
            ?: throw KafkistryIllegalStateException("Can't import topic '$topicName', it's not listed as unexpected on any cluster")
        return suggestTopicDescription(topicName, perClusterInfos)
    }

    fun suggestTopicUpdate(topicName: TopicName): TopicDescription {
        val topicStatuses = inspectionService.inspectTopic(topicName)
        val perClusterInfos = topicStatuses.statusPerClusters
            .sortedBy { it.clusterIdentifier }
            .filter {
                //this filter is here to select only clusters on which this topic is present
                it.status.types.all { type ->
                    when (type) {
                        OK, INTERNAL, UNEXPECTED, UNKNOWN, WRONG_CONFIG, WRONG_PARTITION_COUNT, WRONG_REPLICATION_FACTOR,
                        CONFIG_RULE_VIOLATIONS, CURRENT_CONFIG_RULE_VIOLATIONS, PARTITION_REPLICAS_DISBALANCE,
                        PARTITION_LEADERS_DISBALANCE, NEEDS_LEADER_ELECTION, HAS_OUT_OF_SYNC_REPLICAS, HAS_REPLICATION_THROTTLING -> true
                        MISSING, NOT_PRESENT_AS_EXPECTED, CLUSTER_DISABLED -> false
                        CLUSTER_UNREACHABLE -> throw KafkistryIllegalStateException("Can't suggest topic update when cluster $it is unreachable")
                        HAS_UNVERIFIED_REASSIGNMENTS, RE_ASSIGNMENT_IN_PROGRESS -> throw KafkistryIllegalStateException(
                            "Can't suggest topic update when topic has status $type"
                        )
                        UNAVAILABLE -> throw KafkistryIllegalStateException("Can't suggest topic update for topic that does not exist in registry nor on cluster")
                    }
                }
            }
            .map { it.toDefinedTopicClusterStatus() }
        val disabledClusterIdentifiers = topicStatuses.statusPerClusters
            .filter { CLUSTER_DISABLED in it.status.types }
            .map { it.clusterIdentifier }
        val currentTopicDescription = topicsRegistryService.getTopic(topicName)
        return suggestTopicDescription(topicName, perClusterInfos, currentTopicDescription, disabledClusterIdentifiers)
            .copy(
                owner = currentTopicDescription.owner,
                description = currentTopicDescription.description,
                resourceRequirements = currentTopicDescription.resourceRequirements,
                producer = currentTopicDescription.producer
            )
    }

    fun generateExpectedTopicInfo(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
    ): ExpectedTopicInfo {
        val topic = topicsRegistryService.getTopic(topicName)
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return topic.toExpectedInfoForCluster(clusterRef)
    }

    fun offerTopicPartitionPropertiesChanges(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
    ): PartitionPropertiesChanges {
        val topicDescription = topicsRegistryService.getTopic(topicName)
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return inspectionService.inspectTopicPartitionPropertiesChanges(topicDescription, clusterRef)
    }

    fun reBalanceTopicAssignments(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        reBalanceMode: ReBalanceMode
    ): ReBalanceSuggestion {
        return reAssignTopicSuggestion(topicName, clusterIdentifier) {
            when (reBalanceMode) {
                REPLICAS -> partitionsAssignor.reBalanceReplicasAssignments(
                    existingAssignments, allClusterBrokers, partitionLoads
                )
                LEADERS -> partitionsAssignor.reBalancePreferredLeaders(
                    existingAssignments, allClusterBrokers
                )
                REPLICAS_THEN_LEADERS -> partitionsAssignor.reBalanceReplicasThenLeaders(
                    existingAssignments, allClusterBrokers, partitionLoads
                )
                LEADERS_THEN_REPLICAS -> partitionsAssignor.reBalanceLeadersThenReplicas(
                    existingAssignments, allClusterBrokers, partitionLoads
                )
                ROUND_ROBIN -> partitionsAssignor.reBalanceRoundRobin(
                    existingAssignments, allClusterBrokers
                )
            }
        }
    }

    fun reAssignTopicUnwantedLeader(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier, unwantedBrokerId: BrokerId
    ): ReBalanceSuggestion {
        return reAssignTopicSuggestion(topicName, clusterIdentifier) {
            partitionsAssignor.reAssignUnwantedPreferredLeaders(existingAssignments, unwantedBrokerId)
        }
    }

    fun reAssignTopicWithoutBrokers(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier, excludedBrokers: List<BrokerId>
    ): ReBalanceSuggestion {
        return reAssignTopicSuggestion(topicName, clusterIdentifier) {
            partitionsAssignor.reAssignWithoutBrokers(
                existingAssignments = existingAssignments,
                allBrokers = allClusterBrokers,
                excludedBrokers = excludedBrokers,
                existingPartitionLoads = partitionLoads,

                )
        }
    }

    private fun reAssignTopicSuggestion(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        newAssignmentGenerator: ReAssignContext.() -> AssignmentsChange
    ): ReBalanceSuggestion {
        val topicOnCluster = inspectionService.inspectTopicOnCluster(topicName, clusterIdentifier)
        val existingTopicInfo = topicOnCluster.existingTopicInfo
            ?: throw KafkistryIllegalStateException(
                "Can't get topic assignments for topic '$topicName' on cluster '$clusterIdentifier' when topic status is ${topicOnCluster.status.types}"
            )
        val existingAssignments = existingTopicInfo.partitionsAssignments.toPartitionReplicasMap()
        val partitionLoads = existingAssignments.partitionLoads(topicOnCluster.currentTopicReplicaInfos)
        val allClusterBrokers = clusterStateProvider.getLatestClusterStateValue(clusterIdentifier).clusterInfo.nodeIds
        val assignmentsChange = newAssignmentGenerator(
            ReAssignContext(
                topicName, existingAssignments, partitionLoads, allClusterBrokers
            )
        )
        return ReBalanceSuggestion(
            existingTopicInfo = existingTopicInfo,
            assignmentsChange = assignmentsChange,
            oldDisbalance = existingTopicInfo.assignmentsDisbalance,
            newDisbalance = partitionsAssignor.assignmentsDisbalance(
                assignmentsChange.newAssignments,
                allClusterBrokers,
                partitionLoads
            ),
            dataMigration = with(inspectionService) {
                assignmentsChange.calculateDataMigration(
                    clusterIdentifier,
                    topicName,
                    topicOnCluster.currentTopicReplicaInfos
                )
            }
        )
    }

    fun customReAssignInspect(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier,
        newAssignments: Map<Partition, List<BrokerId>>
    ): ReBalanceSuggestion {
        return reAssignTopicSuggestion(topicName, clusterIdentifier) {
            partitionsAssignor.computeChangeDiff(existingAssignments, newAssignments)
        }
    }

    fun suggestBulkReBalanceTopics(
        clusterIdentifier: KafkaClusterIdentifier,
        options: BulkReAssignmentOptions,
    ): BulkReAssignmentSuggestion {
        val includeTopicFilter = options.includeTopicNamePattern
            ?.takeIf { it.isNotEmpty() }
            ?.toRegex()
            ?.let { regex -> { topic: TopicName -> regex.containsMatchIn(topic) } }
            ?: { true }
        val excludeTopicFilter = options.excludeTopicNamePattern
            ?.takeIf { it.isNotEmpty() }
            ?.toRegex()
            ?.let { regex -> { topic: TopicName -> !regex.containsMatchIn(topic) } }
            ?: { true }
        val (clusterInfo, statusPerTopics) = inspectionService.inspectCluster(clusterIdentifier)
            .run {
                if (statusPerTopics != null && clusterInfo != null) clusterInfo to statusPerTopics
                else throw KafkistryIllegalStateException(
                    "Can't show topics which need re-balance because cluster status is: $clusterState"
                )
            }

        val topicCountSum = AtomicInteger()
        val migrationBytesSum = AtomicLong()
        val topicPartitionsSum = AtomicInteger()
        val inclusionLimited = AtomicBoolean(false)
        val exclusionLimited = AtomicBoolean(false)

        fun ((TopicName) -> Boolean).recordFilteredOut(boolean: AtomicBoolean): (TopicName) -> Boolean {
            return { topicName ->
                this(topicName).also {
                    if (!it) boolean.set(true)
                }
            }
        }

        val topicsReBalanceSuggestions = statusPerTopics.asSequence()
            .filter { topic ->
                topic.status.types.any { it == PARTITION_REPLICAS_DISBALANCE || it == PARTITION_LEADERS_DISBALANCE }
            }
            .map { it.topicName }
            .filter(includeTopicFilter.recordFilteredOut(inclusionLimited))
            .filter(excludeTopicFilter.recordFilteredOut(exclusionLimited))
            .map { it to reBalanceTopicAssignments(it, clusterIdentifier, options.reBalanceMode) }
            .sortedByDescending { (_, suggestion) ->
                val factor = when (options.topicSelectOrder) {
                    BulkReAssignmentOptions.TopicSelectOrder.TOP -> 1
                    BulkReAssignmentOptions.TopicSelectOrder.BOTTOM -> -1
                }
                factor * when (options.topicBy) {
                    MIGRATION_BYTES -> suggestion.dataMigration.totalAddBytes
                    RE_ASSIGNED_PARTITIONS_COUNT -> suggestion.assignmentsChange.reAssignedPartitionsCount.toLong()
                }
            }
            .takeWhile {
                topicCountSum.accumulateAndGet(1, Int::plus) <= options.topicCountLimit
            }
            .takeWhile { (_, suggestion) ->
                migrationBytesSum.accumulateAndGet(
                    suggestion.dataMigration.totalAddBytes,
                    Long::plus
                ) <= options.totalMigrationBytesLimit
            }
            .takeWhile { (_, suggestion) ->
                topicPartitionsSum.accumulateAndGet(
                    suggestion.assignmentsChange.newAssignments.size,
                    Int::plus
                ) <= options.topicPartitionCountLimit
            }
            .toMap()
        val topicsReBalanceStatuses = topicsReBalanceSuggestions.mapValues {
            it.value.existingTopicInfo.partitionsAssignments.toAssignmentsInfo(
                it.value.assignmentsChange,
                clusterInfo.nodeIds
            )
        }
        val totalDataMigration = topicsReBalanceSuggestions.values
            .map { it.dataMigration }
            .merge()

        return BulkReAssignmentSuggestion(
            clusterInfo, topicsReBalanceSuggestions, topicsReBalanceStatuses, totalDataMigration,
            selectionLimitedBy = listOfNotNull(
                takeIf { inclusionLimited.get() },
                takeIf { exclusionLimited.get() },
                takeIf { topicCountSum.get() > options.topicCountLimit },
                takeIf { topicPartitionsSum.get() > options.topicPartitionCountLimit },
                takeIf { migrationBytesSum.get() > options.totalMigrationBytesLimit },
            )
        )
    }

    private data class ReAssignContext(
        val topicName: TopicName,
        val existingAssignments: Map<Partition, List<BrokerId>>,
        val partitionLoads: Map<Partition, PartitionLoad>,
        val allClusterBrokers: List<BrokerId>,
    )


    private data class DisabledConfig(
        val presentClusterIdentifiers: List<KafkaClusterIdentifier>,
        val clustersProperties: Map<KafkaClusterIdentifier, TopicProperties>,
        val clustersConfigs: Map<KafkaClusterIdentifier, TopicConfigMap>
    )

    private fun suggestTopicDescription(
        topicName: TopicName, perClusterInfos: List<DefinedTopicClusterStatus>,
        currentTopicDescription: TopicDescription? = null,
        disabledClusterIdentifiers: List<KafkaClusterIdentifier> = emptyList()
    ): TopicDescription {
        val disabledClusterRefs =
            clustersRegistry.listClustersRefs().filter { it.identifier in disabledClusterIdentifiers }
        val disabledConfig = if (currentTopicDescription != null) {
            val disabledPresentClusters = disabledClusterRefs.filter {
                currentTopicDescription.presence.needToBeOnCluster(it)
            }
            val disabledClustersProperties = disabledPresentClusters.associate {
                it.identifier to currentTopicDescription.propertiesForCluster(it)
            }
            val disabledClustersConfigs = disabledPresentClusters.associate {
                it.identifier to currentTopicDescription.configForCluster(it)
            }
            DisabledConfig(
                disabledPresentClusters.map { it.identifier },
                disabledClustersProperties,
                disabledClustersConfigs
            )
        } else {
            DisabledConfig(emptyList(), emptyMap(), emptyMap())
        }
        val topicPresence = suggestTopicPresence(perClusterInfos, disabledConfig.presentClusterIdentifiers)
        val perClusterSpecificProperties =
            perClusterInfos.associate { it.clusterInfo.identifier to it.existingTopicInfo.properties }
        val perClusterConfigOverrides = perClusterInfos.asSequence()
            .map {
                it.clusterInfo.identifier to it.existingTopicInfo.config
                    .filter { configEntry ->
                        configValueInspector.needIncludeConfigValue(
                            configEntry.key,
                            configEntry.value,
                            it.clusterInfo.config
                        )
                    }
                    .mapValues { (_, configValue) -> configValue.value }
            }
            .filter { it.second.isNotEmpty() }
            .toMap()
        val currentCommonConfig = currentTopicDescription
            ?.config
            ?.filterKeys { key ->
                perClusterConfigOverrides.isNotEmpty() && perClusterConfigOverrides.all { key in it.value.keys }
            }
            ?: emptyMap()
        val topicDescription = TopicDescription(
            name = topicName,
            owner = "",
            description = "",
            resourceRequirements = null,
            producer = "",
            presence = topicPresence,
            properties = currentTopicDescription?.properties ?: TopicProperties(1, 1),
            perClusterProperties = perClusterSpecificProperties + disabledConfig.clustersProperties,
            config = currentCommonConfig,
            perClusterConfigOverrides = perClusterConfigOverrides + disabledConfig.clustersConfigs,
            perTagProperties = emptyMap(),
            perTagConfigOverrides = emptyMap(),
        )
        return topicDescription.minimize()
    }

    private fun suggestTopicPresence(
        perClusterInfos: List<DefinedTopicClusterStatus>,
        disabledClusterIdentifiers: List<KafkaClusterIdentifier>
    ): Presence {
        val allClusters = clustersRegistry.listClustersRefs()
        return allClusters.computePresence(
            presentOnClusters = perClusterInfos.map { it.clusterInfo.identifier },
            disabledClusters = disabledClusterIdentifiers
        )
    }

    fun suggestFixRuleViolations(topicName: TopicName): TopicDescription {
        val topicDescription = topicsRegistryService.getTopic(topicName)
        var fixedTopicDescription = topicDescription
        var attempts = 100
        while (attempts-- > 0) {
            // might need several iterations, because one validation rule fix
            // can introduce violation for other already executed rule
            val prevTopicDescription = fixedTopicDescription
            fixedTopicDescription = doSuggestFixRuleViolations(fixedTopicDescription)
            if (prevTopicDescription == fixedTopicDescription) {
                break
            }
        }
        return fixedTopicDescription
    }

    private fun doSuggestFixRuleViolations(topicDescription: TopicDescription): TopicDescription {
        val topicStatuses = inspectionService.inspectTopic(topicDescription.name)
        val affectedClusters = topicStatuses.statusPerClusters
            .filter { CONFIG_RULE_VIOLATIONS in it.status.types && CLUSTER_UNREACHABLE !in it.status.types }
            .map { it to clusterStateProvider.getLatestClusterState(it.clusterIdentifier) }
            .filter { (_, clusterState) -> clusterState.stateType == StateType.VISIBLE }
            .mapNotNull { (topicStatus, clusterState) ->
                clusterState.valueOrNull()?.clusterInfo?.let {
                    ClusterMetadata(ref = ClusterRef(it.identifier, topicStatus.clusterTags), info = it)
                }
            }
        val fixedTopicDescription = rulesValidator.fixConfigViolations(topicDescription, affectedClusters)
        return fixedTopicDescription.minimize()
    }

    private fun TopicClusterStatus.toDefinedTopicClusterStatus() = DefinedTopicClusterStatus(
        status = status,
        clusterInfo = clusterStateProvider.getLatestClusterState(clusterIdentifier).let {
            it.valueOrNull()?.clusterInfo
                ?: throw KafkistryIllegalStateException("Can't access cluster info for $clusterIdentifier, state is ${it.stateType}")
        },
        existingTopicInfo = existingTopicInfo
            ?: throw KafkistryIllegalStateException("Expected to have non-null topic info")
    )

    private fun TopicDescription.minimize(): TopicDescription {
        val allClusterRefs = clustersRegistry.listClustersRefs()
            .filter { presence.needToBeOnCluster(it) }
        return overridesMinimizer.minimizeOverrides(this, allClusterRefs)
    }

    fun applyResourceRequirements(topicDescription: TopicDescription): TopicDescription {
        val resourceRequirements = topicDescription.resourceRequirements
            ?: throw KafkistryIllegalStateException("Can't apply undefined resource requirements")
        return clustersRegistry.listClusters()
            .map { it to clusterStateProvider.getLatestClusterState(it.identifier) }
            .fold(topicDescription) { resultTopicDescription, (cluster, clusterState) ->
                val currentPartitionCount = clusterState.valueOrNull()
                    ?.topics
                    ?.find { it.name == topicDescription.name }
                    ?.partitionsAssignments?.size
                val newPartitionCount =
                    topicWizardConfigGenerator.determinePartitionCount(resourceRequirements, cluster.ref())
                val oldTopicProperties = topicDescription.propertiesForCluster(cluster.ref())
                val topicProperties = if (currentPartitionCount == null || currentPartitionCount < newPartitionCount) {
                    //do not suggest lower partition count than existing topic already has
                    oldTopicProperties.copy(partitionCount = newPartitionCount)
                } else {
                    oldTopicProperties
                }
                val requiredUsages = resourcesInspector.inspectTopicResources(
                    topicProperties = topicProperties,
                    resourceRequirements = resourceRequirements,
                    clusterInfo = clusterState.valueOrNull()?.clusterInfo,
                    clusterRef = cluster.ref(),
                )

                val retentionBytes = requiredUsages.diskUsagePerPartitionReplica
                val segmentBytes = topicWizardConfigGenerator.determineSegmentBytes(retentionBytes)
                resultTopicDescription
                    .withClusterProperties(cluster.identifier, topicProperties)
                    .withClusterProperty(cluster.identifier, "retention.bytes", retentionBytes.toString())
                    .withClusterProperty(
                        cluster.identifier,
                        "retention.ms",
                        resourceRequirements.retention(cluster.ref()).toMillis().toString()
                    )
                    .withClusterProperty(cluster.identifier, "segment.bytes", segmentBytes.toString())
            }
            .minimize()
    }

    fun suggestBrokerTopicPartitionsThrottle(
        throttleBrokerTopicPartitions: ThrottleBrokerTopicPartitions
    ): ThrottleBrokerTopicPartitionsSuggestion {
        val throttledBrokerIds = throttleBrokerTopicPartitions.brokerIds
        if (throttledBrokerIds.isEmpty()) {
            throw KafkistryValidationException("Specify at least one broker to generate throttle for, got empty brokers list")
        }
        val clusterIdentifier = throttleBrokerTopicPartitions.clusterIdentifier
        val clusterData = clusterStateProvider.getLatestClusterStateValue(clusterIdentifier)
        val existingTopics = clusterData.topics.associateBy { it.name }
        val topicThrottleConfigs = throttleBrokerTopicPartitions.topicNames
            .map {
                existingTopics[it]
                    ?: throw KafkistryIllegalStateException("Topic '$it' does not exist on cluster '$clusterIdentifier'")
            }
            .associate { it.name to it.generateThrottledReplicas(throttledBrokerIds) }
            .filterValues { it.leaderReplicas.isNotEmpty() || it.followerReplicas.isNotEmpty() }
            .mapValues { (_, throttledReplicas) ->
                mapOf(
                    "leader.replication.throttled.replicas" to throttledReplicas.leaderReplicas.format(),
                    "follower.replication.throttled.replicas" to throttledReplicas.followerReplicas.format()
                )
            }
        val maximumDataMigrations =
            computeMaximumDataMigrations(clusterIdentifier, clusterData.topics, throttledBrokerIds)
        val totalMaximumDataMigration = maximumDataMigrations.values.merge()
        return ThrottleBrokerTopicPartitionsSuggestion(
            throttleRequest = throttleBrokerTopicPartitions,
            topicThrottleConfigs = topicThrottleConfigs,
            maximumDataMigrations = maximumDataMigrations,
            totalMaximumDataMigration = totalMaximumDataMigration,
        )
    }

    private fun computeMaximumDataMigrations(
        clusterIdentifier: KafkaClusterIdentifier,
        topics: List<KafkaExistingTopic>,
        throttledBrokerIds: List<BrokerId>,
    ): Map<TopicName, DataMigration> {
        val topicAssignments = topics.associate { it.name to it.partitionsAssignments.toPartitionReplicasMap() }
        val topicAssignmentsWithoutThrottledBrokers = topicAssignments
            .mapValues { (_, assignments) ->
                assignments
                    .mapValues { (_, replicas) -> replicas.minus(throttledBrokerIds) }
                    .filterValues { it.isNotEmpty() }
            }
        val topicsReplicaInfos = replicaDirsService.clusterTopicReplicaInfos(clusterIdentifier)
        return topicAssignments.mapValues { (topic, currentAssignments) ->
            val assignmentsWithoutThrottledBrokers = topicAssignmentsWithoutThrottledBrokers[topic] ?: emptyMap()
            val assignmentsChange =
                partitionsAssignor.computeChangeDiff(assignmentsWithoutThrottledBrokers, currentAssignments)
            with(inspectionService) {
                assignmentsChange.calculateDataMigration(clusterIdentifier, topic, topicsReplicaInfos[topic])
            }
        }
    }

    private fun KafkaExistingTopic.generateThrottledReplicas(brokerIds: List<BrokerId>): TopicThrottledPartitionBrokers {
        val (followers, leaders) = partitionsAssignments
            .toPartitionReplicasMap()
            .flatMap { (partition, replicas) ->
                brokerIds.mapNotNull { brokerId ->
                    if (replicas.size >= 2 && brokerId in replicas) {
                        val otherBrokers = replicas.minus(brokerId)
                        PartitionBroker(partition, brokerId) to otherBrokers.map { PartitionBroker(partition, it) }
                    } else {
                        null
                    }
                }
            }
            .unzip()
        return TopicThrottledPartitionBrokers(
            leaderReplicas = leaders.flatten(),
            followerReplicas = followers
        )
    }

    private fun List<PartitionBroker>.format(): String =
        joinToString(separator = ",") { "${it.partition}:${it.brokerId}" }

    private data class TopicThrottledPartitionBrokers(
        val leaderReplicas: List<PartitionBroker>,
        val followerReplicas: List<PartitionBroker>
    )

    private data class PartitionBroker(
        val partition: Partition,
        val brokerId: BrokerId
    )

    private data class DefinedTopicClusterStatus(
        val status: TopicOnClusterInspectionResult,
        val clusterInfo: ClusterInfo,
        val existingTopicInfo: ExistingTopicInfo
    )

}
