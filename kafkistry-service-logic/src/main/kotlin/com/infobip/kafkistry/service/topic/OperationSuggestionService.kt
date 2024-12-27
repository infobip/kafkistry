package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.BulkReAssignmentOptions.TopicBy.MIGRATION_BYTES
import com.infobip.kafkistry.service.topic.BulkReAssignmentOptions.TopicBy.RE_ASSIGNED_PARTITIONS_COUNT
import com.infobip.kafkistry.service.topic.ReBalanceMode.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.resources.RequiredResourcesInspector
import com.infobip.kafkistry.service.topic.validation.TopicConfigurationValidator
import com.infobip.kafkistry.service.topic.validation.rules.ClusterMetadata
import com.infobip.kafkistry.service.topic.wizard.TopicWizardConfigGenerator
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.generator.*
import com.infobip.kafkistry.service.topic.BulkReAssignmentOptions.ReAssignObjective.*
import com.infobip.kafkistry.service.topic.BulkReAssignmentSuggestion.SelectionLimitedCause
import com.infobip.kafkistry.service.topic.BulkReAssignmentSuggestion.TopicSuggestStage
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CURRENT_CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_OUT_OF_SYNC_REPLICAS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_REPLICATION_THROTTLING
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_UNVERIFIED_REASSIGNMENTS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.INTERNAL
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.NEEDS_LEADER_ELECTION
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.PARTITION_LEADERS_DISBALANCE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.PARTITION_REPLICAS_DISBALANCE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.RE_ASSIGNMENT_IN_PROGRESS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_CONFIG
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_PARTITION_COUNT
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_REPLICATION_FACTOR
import com.infobip.kafkistry.utils.deepToString
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
                        else -> true
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
        reBalanceMode: ReBalanceMode,
        brokersLoads: Map<BrokerId, BrokerLoad> = brokersLoad(clusterIdentifier),
        excludedBrokerIds: List<BrokerId> = emptyList(),
    ): ReBalanceSuggestion {
        return reAssignTopicSuggestion(topicName, clusterIdentifier, brokersLoads) {
            if (excludedBrokerIds.isNotEmpty() && reBalanceMode != ROUND_ROBIN) {
                partitionsAssignor.reAssignWithoutBrokers(
                    existingAssignments, allClusterBrokers, excludedBrokerIds, partitionLoads, brokerLoads,
                )
            } else when (reBalanceMode) {
                REPLICAS -> partitionsAssignor.reBalanceReplicasAssignments(
                    existingAssignments, allClusterBrokers, partitionLoads, brokersLoads,
                )
                LEADERS -> partitionsAssignor.reBalancePreferredLeaders(
                    existingAssignments, allClusterBrokers, brokersLoads,
                )
                REPLICAS_THEN_LEADERS -> partitionsAssignor.reBalanceReplicasThenLeaders(
                    existingAssignments, allClusterBrokers, partitionLoads, brokersLoads
                )
                ROUND_ROBIN -> partitionsAssignor.reBalanceRoundRobin(
                    existingAssignments, allClusterBrokers.filter { it.id !in excludedBrokerIds },
                )
                CLUSTER_LOAD -> partitionsAssignor.reBalanceBrokersLoads(
                    existingAssignments, allClusterBrokers, partitionLoads, brokerLoads,
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
                excludedBrokerIds = excludedBrokers,
                existingPartitionLoads = partitionLoads,
            )
        }
    }

    private fun reAssignTopicSuggestion(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        brokersLoads: Map<BrokerId, BrokerLoad> = brokersLoad(clusterIdentifier),
        newAssignmentGenerator: ReAssignContext.() -> AssignmentsChange
    ): ReBalanceSuggestion {
        val topicOnCluster = inspectionService.inspectTopicOnCluster(topicName, clusterIdentifier)
        val existingTopicInfo = topicOnCluster.existingTopicInfo
            ?: throw KafkistryIllegalStateException(
                "Can't get topic assignments for topic '$topicName' on cluster '$clusterIdentifier' when topic status is ${topicOnCluster.status.types}"
            )
        val existingAssignments = existingTopicInfo.partitionsAssignments.toPartitionReplicasMap()
        val partitionLoads = existingAssignments.partitionLoads(topicOnCluster.currentTopicReplicaInfos)
        val allClusterBrokers = clusterStateProvider.getLatestClusterStateValue(clusterIdentifier).clusterInfo
            .assignableBrokers()
        val assignmentsChange = newAssignmentGenerator(
            ReAssignContext(topicName, existingAssignments, partitionLoads, allClusterBrokers, brokersLoads)
        )
        return ReBalanceSuggestion(
            existingTopicInfo = existingTopicInfo,
            assignmentsChange = assignmentsChange,
            oldDisbalance = existingTopicInfo.assignmentsDisbalance,
            newDisbalance = partitionsAssignor.assignmentsDisbalance(
                assignmentsChange.newAssignments, allClusterBrokers, partitionLoads
            ),
            dataMigration = with(inspectionService) {
                assignmentsChange.calculateDataMigration(
                    clusterIdentifier, topicName, topicOnCluster.currentTopicReplicaInfos
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
        val topicNameFilter = TopicNameFilter(options.includeTopicNamePattern, options.excludeTopicNamePattern)
        val (clusterInfo, statusPerTopics) = inspectionService.inspectClusterTopics(clusterIdentifier)
            .run {
                if (statusPerTopics != null && clusterInfo != null) clusterInfo to statusPerTopics
                else throw KafkistryIllegalStateException(
                    "Can't show topics which need re-balance because cluster status is: $clusterState"
                )
            }
        val brokersLoad = brokersLoad(clusterIdentifier)

        val filterStages = mutableListOf<TopicSuggestStage>()
        val qualifyStages = mutableListOf<TopicSuggestStage>()
        val candidatesStages = mutableListOf<TopicSuggestStage>()
        val constraintsStages = mutableListOf<TopicSuggestStage>()

        val topicCountSum = AtomicInteger()
        val migrationBytesSum = AtomicLong()
        val topicPartitionsSum = AtomicInteger()
        val inclusionLimited = AtomicBoolean(false)
        val exclusionLimited = AtomicBoolean(false)

        fun ClusterTopicStatus.disbalance(extract: AssignmentsDisbalance.() -> Boolean) =
            topicClusterStatus.status.assignmentsDisbalance?.extract() ?: false

        fun Map<Partition, List<BrokerId>>.usesExcludedBroker(): Boolean {
            val usedBrokerIds = values.asSequence().flatten().toSet()
            return options.excludedBrokerIds.any { it in usedBrokerIds }
        }
        fun ExistingTopicInfo.usesExcludedBroker(): Boolean = partitionsAssignments.toPartitionReplicasMap().usesExcludedBroker()

        fun ClusterTopicStatus.usesExcludedBroker(): Boolean {
            if (options.excludedBrokerIds.isEmpty()) return false
            return topicClusterStatus.existingTopicInfo?.usesExcludedBroker() ?: false
        }

        data class ExplainedMatch(val passed: Boolean, val explanation: String)

        fun Boolean.explained(onTrue: String, onFalse: String): ExplainedMatch {
            return if (this) ExplainedMatch(true, onTrue) else ExplainedMatch(false, onFalse)
        }

        fun List<ExplainedMatch>.matchAny(topicName: String, emptyExplain: String): TopicSuggestStage {
            return if (isEmpty()) {
                TopicSuggestStage(topicName, false, listOf(emptyExplain))
            } else {
                val matchReasons = filter { it.passed }
                if (matchReasons.isEmpty()) {
                    TopicSuggestStage(topicName, false, map { it.explanation })
                } else {
                    TopicSuggestStage(topicName, true, matchReasons.map { it.explanation })
                }
            }
        }

        fun List<String>.matchAll(topicName: String, noDiscardsExplain: String): TopicSuggestStage {
            return if (isEmpty()) {
                TopicSuggestStage(topicName, true, listOf(noDiscardsExplain))
            } else {
                TopicSuggestStage(topicName, false, this)
            }
        }

        fun ClusterTopicStatus.filterByName(): TopicSuggestStage {
            val includeOk = topicNameFilter.includeFilter(topicName)
            val excludeOk = topicNameFilter.excludeFilter(topicName)
            if (!includeOk) inclusionLimited.set(true)
            if (!excludeOk) exclusionLimited.set(true)
            return buildList {
                if (!includeOk) add("not matching include /${topicNameFilter.includeTopicNamePattern}/")
                if (!excludeOk) add("matching exclude /${topicNameFilter.excludeTopicNamePattern}/")
            }.matchAll(topicName, "not filtered out")
        }

        fun ClusterTopicStatus.objectiveQualified(): TopicSuggestStage {
            val objectiveMatches = options.objectives.map { objective ->
                when (objective) {
                    EXCLUDE_BROKERS -> usesExcludedBroker().explained(
                        "uses excluded broker", "not using excluded broker",
                    )
                    BALANCE_REPLICAS -> disbalance { hasReplicasDisbalance() }.explained(
                        "has replicas disbalance", "not having replicas disbalance",
                    )
                    BALANCE_LEADERS -> disbalance { hasLeadersDisbalance() }.explained(
                        "has leaders disbalance", "not having leaders disbalance",
                    )
                    BALANCE_RACKS -> disbalance { hasRacksDisbalance() }.explained(
                        "has racks disbalance", "not having racks disbalance",
                    )
                    BALANCE_CLUSTER -> topicClusterStatus.existingTopicInfo?.properties?.partitionCount.let {
                        if (it == null) false
                        else it % clusterInfo.brokerIds.size != 0
                    }.explained("uneven impact on cluster", "even distribution on cluster")
                    AVOID_SINGLE_RACKS -> disbalance { hasSingleRackPartitions() }.explained(
                        "has single rack partitions", "not having single rack partitions",
                    )
                }
            }
            return objectiveMatches.matchAny(topicName, "not having objectives")
        }

        fun Map<Partition, List<BrokerId>>.replicasPerBroker(): Map<BrokerId, Int> = values.flatten().groupingBy { it }.eachCount()
        fun Map<Partition, List<BrokerId>>.leadersPerBroker(): Map<BrokerId, Int> = values.map { it.first() }.groupingBy { it }.eachCount()

        fun ReBalanceSuggestion.objectiveImproved(): TopicSuggestStage {
            if (!assignmentsChange.hasChange) {
                return TopicSuggestStage(existingTopicInfo.name, false, listOf("no change in assignments"))
            }
            val oldRacksStatus = oldDisbalance.partitionsPerRackDisbalance
            val newRacksStatus = newDisbalance.partitionsPerRackDisbalance
            val oldReplicasPerBroker = assignmentsChange.oldAssignments.replicasPerBroker()
            val newReplicasPerBroker = assignmentsChange.newAssignments.replicasPerBroker()
            val oldLeadersPerBroker = assignmentsChange.oldAssignments.leadersPerBroker()
            val newLeadersPerBroker = assignmentsChange.newAssignments.leadersPerBroker()
            val objectiveMatches = options.objectives.map { objective ->
                when (objective) {
                    EXCLUDE_BROKERS -> assignmentsChange.newAssignments.usesExcludedBroker().not().explained(
                        "not using excluded broker anymore", "still uses excluded broker",
                    )
                    BALANCE_REPLICAS -> (newDisbalance.replicasDisbalance < oldDisbalance.replicasDisbalance).explained(
                        "new replicas disbalance ${newDisbalance.replicasDisbalance} better than old ${oldDisbalance.replicasDisbalance}",
                        "new replicas disbalance ${newDisbalance.replicasDisbalance} not improved to old ${oldDisbalance.replicasDisbalance}",
                    )
                    BALANCE_LEADERS -> (newDisbalance.leadersDisbalance < oldDisbalance.leadersDisbalance).explained(
                        "new leaders disbalance ${newDisbalance.leadersDisbalance} better than old ${oldDisbalance.leadersDisbalance}",
                        "new leaders disbalance ${newDisbalance.leadersDisbalance} not improved to old ${oldDisbalance.leadersDisbalance}",
                    )
                    BALANCE_RACKS -> (newRacksStatus.totalDisbalance < oldRacksStatus.totalDisbalance).explained(
                        "new racks disbalance ${newRacksStatus.totalDisbalance} better than old ${oldRacksStatus.totalDisbalance}",
                        "new racks disbalance ${newRacksStatus.totalDisbalance} not improved to old ${oldRacksStatus.totalDisbalance}",
                    )
                    BALANCE_CLUSTER -> (newLeadersPerBroker != oldLeadersPerBroker || newReplicasPerBroker != oldReplicasPerBroker).explained(
                        "number of leaders or replicas per each broker changed with new assignment",
                        "number of leaders and replicas per each broker remained the same",
                    )
                    AVOID_SINGLE_RACKS -> (newRacksStatus.singleRackPartitions.size < oldRacksStatus.singleRackPartitions.size).explained(
                        "new number of single rack partitions ${newRacksStatus.singleRackPartitions.size} better than old ${oldRacksStatus.singleRackPartitions.size}",
                        "new number of single rack partitions ${newRacksStatus.singleRackPartitions.size} not improved to old ${oldRacksStatus.singleRackPartitions.size}",
                    )
                }
            }
            return objectiveMatches.matchAny(existingTopicInfo.name, "not having objectives")
        }

        fun <T> Sequence<T>.filterObserving(
            accumulator: MutableList<TopicSuggestStage>, filter: (T) -> TopicSuggestStage
        ): Sequence<T> = filter  { filter(it).also(accumulator::add).passed }

        fun <T> Sequence<T>.takeWhileObserving(
            accumulator: MutableList<TopicSuggestStage>, filter: (T) -> TopicSuggestStage
        ): Sequence<T> = takeWhile { filter(it).also(accumulator::add).passed }

        fun Pair<TopicName, ReBalanceSuggestion>.addsToConstraints(): TopicSuggestStage {
            val (topicName, suggestion) = this
            val countOk = topicCountSum.accumulateAndGet(1, Int::plus) <= options.topicCountLimit
            val bytesOk = migrationBytesSum.accumulateAndGet(
                suggestion.dataMigration.totalAddBytes, Long::plus
            ) <= options.totalMigrationBytesLimit
            val partitionCountOk = topicPartitionsSum.accumulateAndGet(
                suggestion.assignmentsChange.reAssignedPartitionsCount, Int::plus
            ) <= options.topicPartitionCountLimit
            return buildList {
                if (!countOk) add("over topic count limit")
                if (!bytesOk) add("over migration bytes limit")
                if (!partitionCountOk) add("over partitions count limit")
            }.matchAll(topicName, "fits in constraints")
        }

        val topicsReBalanceSuggestions = statusPerTopics.asSequence()
            .filterObserving(filterStages) { it.filterByName() }
            .filterObserving(qualifyStages) { it.objectiveQualified() }
            .mapNotNull { status ->
                try {
                    status.topicName to reBalanceTopicAssignments(
                        status.topicName, clusterIdentifier, options.reBalanceMode, brokersLoad, options.excludedBrokerIds,
                    )
                } catch (ex: KafkistryValidationException) {
                    qualifyStages.apply { removeIf {it.topic == status.topicName } }.add(
                        TopicSuggestStage(status.topicName, false, listOf("Can't re-assign topic: ${ex.deepToString()}"))
                    )
                    null
                }
            }
            .filterObserving(candidatesStages) { (_, suggestion) -> suggestion.objectiveImproved() }
            .sortedBy { (_, suggestion) ->
                val orderFactor = when (options.topicSelectOrder) {
                    BulkReAssignmentOptions.TopicSelectOrder.TOP -> -1
                    BulkReAssignmentOptions.TopicSelectOrder.BOTTOM -> 1
                }
                orderFactor * when (options.topicBy) {
                    MIGRATION_BYTES -> suggestion.dataMigration.totalAddBytes
                    RE_ASSIGNED_PARTITIONS_COUNT -> suggestion.assignmentsChange.reAssignedPartitionsCount.toLong()
                }
            }
            .takeWhileObserving(constraintsStages) { it.addsToConstraints() }
            .toMap()

        val topicsReBalanceStatuses = topicsReBalanceSuggestions.mapValues {
            it.value.existingTopicInfo.partitionsAssignments.toAssignmentsInfo(
                it.value.assignmentsChange, clusterInfo.assignableBrokers(),
            )
        }
        val totalDataMigration = topicsReBalanceSuggestions.values
            .map { it.dataMigration }
            .merge()

        return BulkReAssignmentSuggestion(
            clusterInfo, topicsReBalanceSuggestions, topicsReBalanceStatuses, totalDataMigration,
            stats = BulkReAssignmentSuggestion.SuggestionStats(
                counts = BulkReAssignmentSuggestion.Counts(
                    all = statusPerTopics.size,
                    filtered = filterStages.count { it.passed },
                    qualified = qualifyStages.count { it.passed },
                    candidates = candidatesStages.count { it.passed },
                    selected = topicsReBalanceSuggestions.size,
                ),
                filter = filterStages,
                qualify = qualifyStages,
                candidate = candidatesStages,
                constraints = constraintsStages,
            ),
            selectionLimitedBy = listOfNotNull(
                SelectionLimitedCause.INCLUSION_FILTERED.takeIf { inclusionLimited.get() },
                SelectionLimitedCause.EXCLUSION_FILTERED.takeIf { exclusionLimited.get() },
                SelectionLimitedCause.TOPIC_COUNT.takeIf { topicCountSum.get() > options.topicCountLimit },
                SelectionLimitedCause.PARTITION_COUNT.takeIf { topicPartitionsSum.get() > options.topicPartitionCountLimit },
                SelectionLimitedCause.MIGRATION_BYTES.takeIf { migrationBytesSum.get() > options.totalMigrationBytesLimit },
            )
        )
    }

    private fun brokersLoad(clusterIdentifier: KafkaClusterIdentifier): Map<BrokerId, BrokerLoad> {
        val clusterData = clusterStateProvider.getLatestClusterStateValue(clusterIdentifier)
        return inspectionService.inspectClusterBrokersLoad(clusterData)
    }

    private data class ReAssignContext(
        val topicName: TopicName,
        val existingAssignments: Map<Partition, List<BrokerId>>,
        val partitionLoads: Map<Partition, PartitionLoad>,
        val allClusterBrokers: List<Broker>,
        val brokerLoads: Map<BrokerId, BrokerLoad>,
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
                    ClusterMetadata(ref = ClusterRef(it.identifier, topicStatus.clusterTags), info = it) to topicStatus.existingTopicInfo
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

    fun applyResourceRequirements(
        topicDescription: TopicDescription,
        onlyClusterIdentifiers: Set<KafkaClusterIdentifier>?,
        onlyClusterTags: Set<Tag>?,
    ): TopicDescription {
        val resourceRequirements = topicDescription.resourceRequirements
            ?: throw KafkistryIllegalStateException("Can't apply undefined resource requirements")
        return clustersRegistry.listClustersRefs()
            .filter { clusterRef ->
                val identifiers = onlyClusterIdentifiers.orEmpty()
                val tags = onlyClusterTags.orEmpty()
                when {
                    identifiers.isEmpty() && tags.isEmpty() -> true
                    else -> (clusterRef.identifier in identifiers) || (tags.any { it in clusterRef.tags })
                }
            }
            .map { it to clusterStateProvider.getLatestClusterState(it.identifier) }
            .fold(topicDescription) { resultTopicDescription, (clusterRef, clusterState) ->
                val currentPartitionCount = clusterState.valueOrNull()
                    ?.topics
                    ?.find { it.name == topicDescription.name }
                    ?.partitionsAssignments?.size
                val newPartitionCount =
                    topicWizardConfigGenerator.determinePartitionCount(resourceRequirements, clusterRef)
                val oldTopicProperties = topicDescription.propertiesForCluster(clusterRef)
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
                    clusterRef = clusterRef,
                )

                val retentionBytes = requiredUsages.diskUsagePerPartitionReplica
                val segmentBytes = topicWizardConfigGenerator.determineSegmentBytes(retentionBytes)
                resultTopicDescription
                    .withClusterProperties(clusterRef.identifier, topicProperties)
                    .withClusterProperty(clusterRef.identifier, "retention.bytes", retentionBytes.toString())
                    .withClusterProperty(
                        clusterRef.identifier, "retention.ms",
                        resourceRequirements.retention(clusterRef).toMillis().toString()
                    )
                    .withClusterProperty(clusterRef.identifier, "segment.bytes", segmentBytes.toString())
            }
            .minimize()
    }

    fun minimizeTopicDescription(topicDescription: TopicDescription): TopicDescription {
        return topicDescription.minimize()
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
        val throttledBrokersSet = throttledBrokerIds.toSet()
        val topicAssignments = topics.associate { it.name to it.partitionsAssignments.toPartitionReplicasMap() }
        val topicAssignmentsWithoutThrottledBrokers = topicAssignments
            .mapValues { (_, assignments) ->
                assignments
                    .mapValues { (_, replicas) -> replicas.minus(throttledBrokersSet) }
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
