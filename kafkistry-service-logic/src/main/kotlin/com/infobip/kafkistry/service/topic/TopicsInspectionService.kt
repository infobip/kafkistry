package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.kafka.KafkaExistingTopic
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.TopicsInspectionService.Comparison.*
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.BrokerLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.generator.currentLeaders
import com.infobip.kafkistry.service.reassignments.TopicReAssignmentsMonitorService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

@Service
class TopicsInspectionService(
    private val topicsRegistry: TopicsRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
    private val topicIssuesInspector: TopicIssuesInspector,
    private val partitionsReplicasAssignor: PartitionsReplicasAssignor,
    private val configValueInspector: ConfigValueInspector,
    private val replicaDirsService: ReplicaDirsService,
    private val reAssignmentsMonitorService: TopicReAssignmentsMonitorService,
) {

    fun inspectAllTopics(): List<TopicStatuses> {
        val clusterRefs = clustersRegistry.listClustersRefs()
        return loadTopics().map { analyzeTopicStatuses(it.name, it, clusterRefs) }
    }

    fun inspectTopic(topicName: TopicName): TopicStatuses {
        val clusterRefs = clustersRegistry.listClustersRefs()
        val topicDescription = topicsRegistry.findTopic(topicName)
        return analyzeTopicStatuses(topicName, topicDescription, clusterRefs)
    }

    fun inspectTopic(topicDescription: TopicDescription): TopicStatuses {
        val clusterRefs = clustersRegistry.listClustersRefs()
        return analyzeTopicStatuses(topicDescription.name, topicDescription, clusterRefs)
    }

    fun inspectUnknownTopics(): List<TopicStatuses> {
        val clusterRefs = clustersRegistry.listClustersRefs()
        val knownTopicNames = loadTopics()
            .map { it.name }
            .toSet()
        return clusterRefs.asSequence()
            .map { listUnknownTopicsOnCluster(it, knownTopicNames).entries }
            .flatten()
            .groupBy ({ it.key }) { it.value }    //topicInfo is never null on unknown topic
            .map { (topicName, statusPerClusters) ->
                topicStatuses(topicName, null, statusPerClusters)
            }
            .sortedBy { it.topicName }
            .toList()
    }

    private fun listUnknownTopicsOnCluster(
        clusterRef: ClusterRef,
        knownTopicNames: Set<TopicName>,
    ): Map<TopicName, TopicClusterStatus> {
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterRef.identifier)
        return latestClusterState.valueOrNull()
            ?.topics
            .orEmpty()
            .filter { it.name !in knownTopicNames }
            .associate {
                it.name to topicIssuesInspector.inspectTopicDataOnClusterData(
                    it.name, null, it,
                    replicaDirsService.topicReplicaInfos(clusterRef.identifier, it.name),
                    reAssignmentsMonitorService.topicReAssignments(clusterRef.identifier, it.name),
                    clusterRef, latestClusterState
                )
            }
    }

    fun listMissingTopics(clusterIdentifier: KafkaClusterIdentifier): List<TopicDescription> {
        return inspectClusterTopics(clusterIdentifier)
                .let {
                    it.statusPerTopics
                            ?: throw KafkistryIllegalStateException("Can't list cluster topics because cluster state is ${it.clusterState}")
                }
                .filter { InspectionResultType.MISSING in it.status.types }
                .map { topicsRegistry.getTopic(it.topicName) }
    }

    fun inspectClusterTopics(clusterIdentifier: KafkaClusterIdentifier): ClusterTopicsStatuses {
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        return inspectClusterTopics(cluster)
    }

    fun inspectClusterTopics(clusterRef: ClusterRef): ClusterTopicsStatuses {
        val cluster = clustersRegistry.getCluster(clusterRef.identifier)
        return inspectClusterTopics(cluster.copy(tags = clusterRef.tags))
    }

    fun inspectClusterTopics(cluster: KafkaCluster): ClusterTopicsStatuses {
        val allTopics = loadTopics().associateBy { it.name }
        return analyzeClusterTopics(allTopics, cluster)
    }

    fun listTopicsForLeaderReElection(clusterIdentifier: KafkaClusterIdentifier): List<ExistingTopicInfo> {
        return inspectClusterTopics(clusterIdentifier)
                .let {
                    it.statusPerTopics ?: throw KafkistryIllegalStateException(
                            "Can't list cluster topics because cluster state is ${it.clusterState}"
                    )
                }
                .filter { InspectionResultType.NEEDS_LEADER_ELECTION in it.status.types }
                .map {
                    it.existingTopicInfo ?: throw KafkistryIllegalStateException(
                            "Can't read topic's '${it.topicName}' info because its state is: ${it.status.types}")
                }
    }

    fun inspectAllClustersTopics(): List<ClusterTopicsStatuses> {
        val allTopics = loadTopics().associateBy { it.name }
        return clustersRegistry.listClusters()
                .map { analyzeClusterTopics(allTopics, it) }
    }

    private fun analyzeClusterTopics(
            allRegistryTopics: Map<String, TopicDescription>,
            cluster: KafkaCluster
    ): ClusterTopicsStatuses {
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(cluster.identifier)
        val latestClusterData = latestClusterState.valueOrNull()
        val clusterTopicsMap = latestClusterData
                ?.topics
                ?.associateBy { it.name }
                ?: return nonVisibleClusterStatuses(cluster, latestClusterState)
        val topicNames = allRegistryTopics.values.asSequence()
                .map { it.name }
                .plus(clusterTopicsMap.keys)
                .distinct()
                .sorted()
                .toList()
        val topicsReplicasInfos = replicaDirsService.clusterTopicReplicaInfos(cluster.identifier)
        val topicsPartitionReAssignments = reAssignmentsMonitorService.clusterTopicsReAssignments(cluster.identifier)
        val statusPerTopics = topicNames.map { topicName ->
            val topicDescription = allRegistryTopics[topicName]
            val existingTopic = latestClusterData.topics.firstOrNull { it.name == topicName }
            val inspectionResult = topicIssuesInspector.inspectTopicDataOnClusterData(
                    topicName, topicDescription, existingTopic,
                    topicsReplicasInfos[topicName],
                    topicsPartitionReAssignments[topicName] ?: emptyMap(),
                    cluster.ref(), latestClusterState
            )
            ClusterTopicStatus(topicName, inspectionResult.status, inspectionResult.existingTopicInfo)
        }
        val topicsStatusCounts = statusPerTopics.statusTypeCounts { it.status.types }
        val statusFlags = statusPerTopics.map { it.status.flags }.aggregate()
        return ClusterTopicsStatuses(
                latestClusterState.lastRefreshTime, cluster,
                latestClusterData.clusterInfo, latestClusterState.stateType,
                statusFlags, statusPerTopics, topicsStatusCounts
        )
    }

    private fun nonVisibleClusterStatuses(
        cluster: KafkaCluster, latestClusterState: StateData<KafkaClusterState>
    ): ClusterTopicsStatuses {
        val statusFlags = if (latestClusterState.stateType == StateType.DISABLED) {
            StatusFlags.DISABLED
        } else {
            StatusFlags.NON_VISIBLE
        }
        return ClusterTopicsStatuses(
                latestClusterState.lastRefreshTime, cluster,
                latestClusterState.valueOrNull()?.clusterInfo,
                latestClusterState.stateType,
                statusFlags, null, null
        )
    }

    fun topicConfigNeededChanges(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ): List<ConfigValueChange> {
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        val expectedConfig = topicsRegistry.getTopic(topicName).configForCluster(clusterRef)
        val clusterData = kafkaClustersStateProvider.getLatestClusterStateValue(clusterIdentifier)
        val existingTopic = clusterData.topics
                .firstOrNull { it.name == topicName }
                ?: throw KafkistryIllegalStateException("Could not found topic '$topicName' on cluster '$clusterIdentifier'")
        return computeTopicConfigNeededChanges(clusterData.clusterInfo.config, expectedConfig, existingTopic)
    }

    fun inspectTopicsConfigsNeededChanges(
        clusterIdentifier: KafkaClusterIdentifier,
    ): Map<TopicName, List<ConfigValueChange>> {
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        val clusterData = kafkaClustersStateProvider.getLatestClusterStateValue(clusterIdentifier)
        val existingTopics = clusterData.topics.associateBy { it.name }
        val topicDescriptions = topicsRegistry.listTopics()
        return topicDescriptions.mapNotNull { topic ->
            existingTopics[topic.name]
                ?.let { existingTopic ->
                    val expectedConfig = topic.configForCluster(clusterRef)
                    computeTopicConfigNeededChanges(clusterData.clusterInfo.config, expectedConfig, existingTopic)
                }
                ?.takeIf { it.isNotEmpty() }
                ?.let { topic.name to it }
        }.toMap()
    }

    private fun computeTopicConfigNeededChanges(
        clusterConfig: ExistingConfig,
        expectedConfig: TopicConfigMap,
        existingTopic: KafkaExistingTopic,
    ): List<ConfigValueChange> {
        val allConfigKeys = expectedConfig.keys + existingTopic.config.keys
        return allConfigKeys.mapNotNull {
            configValueInspector.requiredConfigValueChange(
                nameKey = it,
                actualValue = existingTopic.config[it] ?: return@mapNotNull null,
                expectedValue = expectedConfig[it],
                clusterConfig = clusterConfig,
            )
        }
    }

    fun inspectTopicPartitionPropertiesChanges(
            topicDescription: TopicDescription,
            clusterRef: ClusterRef
    ): PartitionPropertiesChanges {
        val topicName = topicDescription.name
        val expectedProperties = topicDescription.propertiesForCluster(clusterRef)
        val clusterData = kafkaClustersStateProvider.getLatestClusterStateValue(clusterRef.identifier)
        val clusterBrokersLoad = inspectClusterBrokersLoad(clusterData)
        val allBrokers = clusterData.clusterInfo.nodeIds
        val existingTopic = clusterData.topics
                .find { it.name == topicName }
                ?: throw KafkistryIllegalStateException("There is no topic '$topicName' found on cluster '${clusterRef.identifier}'")
        val currentAssignments = existingTopic.currentAssignments()
        val topicReplicaInfos = replicaDirsService.topicReplicaInfos(clusterRef.identifier, topicName)
        val partitionReAssignments = reAssignmentsMonitorService.topicReAssignments(clusterRef.identifier, topicName)
        val existingTopicInfo = existingTopic.toTopicInfo(clusterData.clusterInfo.nodeIds, topicReplicaInfos, partitionReAssignments, partitionsReplicasAssignor)
        val actualProperties = existingTopicInfo.properties
        val partitions = expectedProperties.partitionCount comparingTo actualProperties.partitionCount
        val replication = expectedProperties.replicationFactor comparingTo actualProperties.replicationFactor

        val partitionCountChange = when (partitions) {
            NEEDS_TO_REDUCE -> PartitionPropertyChange.impossible(
                "Partition count can't be reduced from ${actualProperties.partitionCount} to ${expectedProperties.partitionCount}",
                existingTopic.partitionsAssignments
            )
            IS_AS_EXPECTED -> PartitionPropertyChange.noNeed(existingTopic.partitionsAssignments)
            NEEDS_TO_INCREASE -> {
                val change = partitionsReplicasAssignor.assignNewPartitionReplicas(
                        existingAssignments = currentAssignments,
                        allBrokers = allBrokers,
                        numberOfNewPartitions = expectedProperties.partitionCount - actualProperties.partitionCount,
                        replicationFactor = actualProperties.replicationFactor,
                        existingPartitionLoads = currentAssignments.partitionLoads(topicReplicaInfos),
                        clusterBrokersLoad = clusterBrokersLoad
                )
                PartitionPropertyChange.change(
                    change,
                    existingTopic.partitionsAssignments,
                    change.calculateDataMigration(clusterRef.identifier, topicName, topicReplicaInfos)
                )
            }
        }
        val replicationFactorChange = when (replication) {
            NEEDS_TO_REDUCE -> {
                val change = partitionsReplicasAssignor.reduceReplicationFactor(
                        currentAssignments, targetReplicationFactor = expectedProperties.replicationFactor
                )
                PartitionPropertyChange.change(
                    change,
                    existingTopic.partitionsAssignments,
                    change.calculateDataMigration(clusterRef.identifier, topicName, topicReplicaInfos)
                )
            }
            IS_AS_EXPECTED -> PartitionPropertyChange.noNeed(existingTopic.partitionsAssignments)
            NEEDS_TO_INCREASE -> {
                val change = partitionsReplicasAssignor.assignPartitionsNewReplicas(
                        existingAssignments = currentAssignments,
                        allBrokers = allBrokers,
                        replicationFactorIncrease = expectedProperties.replicationFactor - actualProperties.replicationFactor,
                        existingPartitionLoads = currentAssignments.partitionLoads(topicReplicaInfos),
                        clusterBrokersLoad = clusterBrokersLoad
                )
                PartitionPropertyChange.change(
                    change,
                    existingTopic.partitionsAssignments,
                    change.calculateDataMigration(clusterRef.identifier, topicName, topicReplicaInfos)
                )
            }
        }
        return PartitionPropertiesChanges(
                existingTopicInfo = existingTopicInfo,
                partitionCountChange = partitionCountChange,
                replicationFactorChange = replicationFactorChange
        )
    }

    fun inspectClusterBrokersLoad(clusterData: KafkaClusterState): Map<BrokerId, BrokerLoad> {
        val brokerPartitionCounts = clusterData.topics.asSequence()
                .flatMap { it.partitionsAssignments.asSequence() }
                .flatMap { it.replicasAssignments.asSequence().map { replica -> replica.brokerId } }
                .groupingBy { it }
                .eachCount()
        val brokerUsedDiskSizes = replicaDirsService.clusterTopicReplicaInfos(clusterData.clusterInfo.identifier)
                .values
                .flatMap { it.partitionBrokerReplicas.values }
                .flatMap { it.entries }
                .groupBy({ it.key }, { it.value.sizeBytes })
                .mapValues { (_, sizes) -> sizes.sum() }
        return (brokerPartitionCounts.keys + brokerUsedDiskSizes.keys)
                .distinct()
                .associateWith { broker ->
                    BrokerLoad(
                            brokerPartitionCounts[broker] ?: 0,
                            brokerUsedDiskSizes[broker] ?: 0L
                    )
                }
    }

    enum class Comparison {
        NEEDS_TO_REDUCE,
        IS_AS_EXPECTED,
        NEEDS_TO_INCREASE
    }

    private infix fun Int.comparingTo(other: Int): Comparison =
            when {
                this < other -> NEEDS_TO_REDUCE
                this > other -> NEEDS_TO_INCREASE
                else -> IS_AS_EXPECTED
            }

    fun inspectTopicOnCluster(
            topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
    ): TopicClusterStatus {
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        val topicDescription = topicsRegistry.findTopic(topicName)
        return doInspectTopicOnCluster(topicName, topicDescription, clusterRef)
    }

    fun AssignmentsChange.calculateDataMigration(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        topicReplicaInfos: TopicReplicaInfos?
    ): DataMigration {
        fun MutableMap<BrokerId, AtomicLong>.addBrokerData(broker: BrokerId, bytes: Long) {
            computeIfAbsent(broker) { AtomicLong(0L) }.addAndGet(bytes)
        }

        fun Map<Partition, Long>.sizeOfPartition(partition: Partition): Long = this[partition] ?: 0L
        val replicaInfos = topicReplicaInfos ?: throw KafkistryIllegalStateException(
                "Can't get topic replica infos for topic '$topicName' on cluster '$clusterIdentifier'"
        )
        fun sizeOfPartitionReplica(partition: Partition, replicaBroker: BrokerId): Long = replicaInfos
                .partitionBrokerReplicas[partition]
                ?.get(replicaBroker)
                ?.sizeBytes
                ?: 0L
        val currentLeaders = currentLeaders()
        val currentLeaderSize = currentLeaders.mapValues { (partition, broker) ->
            sizeOfPartitionReplica(partition, broker)
        }
        val perBrokerIn = mutableMapOf<BrokerId, AtomicLong>()
        val perBrokerOut = mutableMapOf<BrokerId, AtomicLong>()
        val perBrokerRelease = mutableMapOf<BrokerId, AtomicLong>()
        addedPartitionReplicas.forEach { (partition, brokers) ->
            brokers.forEach { broker ->
                val sizeOfPartition = currentLeaderSize.sizeOfPartition(partition)
                perBrokerIn.addBrokerData(broker, sizeOfPartition)
                currentLeaders[partition]?.also { leader ->
                    perBrokerOut.addBrokerData(leader, sizeOfPartition)
                }
            }
        }
        removedPartitionReplicas.forEach { (partition, brokers) ->
            brokers.forEach { broker ->
                val sizeOfReplica = sizeOfPartitionReplica(partition, broker)
                perBrokerRelease.addBrokerData(broker, sizeOfReplica)
            }
        }
        val perBrokerTotal = (perBrokerIn.keys + perBrokerOut.keys).distinct()
                .sorted()
                .associateWith { (perBrokerIn[it]?.get() ?: 0L) + (perBrokerOut[it]?.get() ?: 0L) }
        return DataMigration(
                reAssignedPartitions = reAssignedPartitionsCount,
                totalIOBytes = perBrokerTotal.values.sum(),
                totalAddBytes = perBrokerIn.values.map { it.get() }.sum(),
                totalReleaseBytes = perBrokerRelease.values.map { it.get() }.sum(),
                perBrokerTotalIOBytes = perBrokerTotal,
                perBrokerInputBytes = perBrokerIn.mapValues { it.value.get() },
                perBrokerOutputBytes = perBrokerOut.mapValues { it.value.get() },
                perBrokerReleasedBytes = perBrokerRelease.mapValues { it.value.get() },
                maxBrokerIOBytes = (perBrokerIn.values + perBrokerOut.values).map { it.get() }.maxOrNull() ?: 0L
        )

    }

    fun inspectTopicOnCluster(
            topicName: TopicName,
            clusterRef: ClusterRef
    ): TopicClusterStatus {
        val topicDescription = topicsRegistry.findTopic(topicName)
        return doInspectTopicOnCluster(topicName, topicDescription, clusterRef)
    }

    fun inspectTopicOnCluster(
            topicDescription: TopicDescription,
            clusterRef: ClusterRef
    ): TopicClusterStatus = doInspectTopicOnCluster(topicDescription.name, topicDescription, clusterRef)

    private fun doInspectTopicOnCluster(
        topicName: TopicName,
        topicDescription: TopicDescription?,
        clusterRef: ClusterRef
    ): TopicClusterStatus {
        val latestClusterState = kafkaClustersStateProvider.getLatestClusterState(clusterRef.identifier)
        val existingTopic = latestClusterState.valueOrNull()?.topics?.firstOrNull { it.name == topicName }
        val topicReplicaInfos = replicaDirsService.topicReplicaInfos(clusterRef.identifier, topicName)
        val partitionsReAssignments = reAssignmentsMonitorService.topicReAssignments(clusterRef.identifier, topicName)
        return topicIssuesInspector.inspectTopicDataOnClusterData(
                topicName, topicDescription, existingTopic, topicReplicaInfos,
                partitionsReAssignments, clusterRef, latestClusterState
        )
    }

    private fun loadTopics() = topicsRegistry.listTopics()

    private fun analyzeTopicStatuses(
        topicName: TopicName, topicDescription: TopicDescription?, clusterRefs: List<ClusterRef>
    ): TopicStatuses {
        val statusPerClusters = clusterRefs
            .map { doInspectTopicOnCluster(topicName, topicDescription, it) }
            .sortedBy { if (InspectionResultType.CLUSTER_DISABLED in it.status.types) 1 else 0 }
            .sortedBy {
                val clusterRef = ClusterRef(it.clusterIdentifier, it.clusterTags)
                val needToExist = topicDescription?.presence?.needToBeOnCluster(clusterRef) == true
                if (needToExist) 0 else 1
            }
        return topicStatuses(topicName, topicDescription, statusPerClusters)
    }

    private fun topicStatuses(
        topicName: TopicName, topicDescription: TopicDescription?, statusPerClusters: List<TopicClusterStatus>,
    ): TopicStatuses {
        val topicsStatusCounts = statusPerClusters.statusTypeCounts { it.status.types }
        val statusFlags = statusPerClusters.map { it.status.flags }.aggregate()
        val availableActions = statusPerClusters.flatMap { it.status.availableActions }.distinct()
        return TopicStatuses(topicName, topicDescription, statusFlags, statusPerClusters, topicsStatusCounts, availableActions)
    }

    private fun <T, N : NamedType> List<T>.statusTypeCounts(
            statusExtract: (T) -> Collection<N>
    ): List<NamedTypeQuantity<N, Int>> = asSequence()
            .flatMap(statusExtract)
            .groupingBy { it }
            .eachCount()
            .map { NamedTypeQuantity(it.key, it.value) }
            .sortedByDescending { it.quantity }

}
