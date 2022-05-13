package com.infobip.kafkistry.service.generator.balance

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.KafkaExistingTopic
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.toPartitionReplicasMap
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.kafkastate.KafkaTopicReAssignmentsProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.DataMigration
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.topic.merge
import com.infobip.kafkistry.service.topic.offsets.TopicOffsets
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import org.springframework.stereotype.Service

@Service
class GlobalBalancerService(
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val reAssignmentsProvider: KafkaTopicReAssignmentsProvider,
    private val consumerGroupsProvider: KafkaConsumerGroupsProvider,
    private val replicaDirsService: ReplicaDirsService,
    private val topicOffsetsService: TopicOffsetsService,
) {

    private val balancer = Balancer()
    private val partitionsAssignor = PartitionsReplicasAssignor()

    fun getCurrentBalanceStatus(clusterIdentifier: KafkaClusterIdentifier): ClusterBalanceStatus {
        return getCurrentGlobalState(clusterIdentifier).extractBalanceStatus()
    }

    fun getCurrentGlobalState(clusterIdentifier: KafkaClusterIdentifier): GlobalState {
        val clusterState = clustersStateProvider.getLatestClusterStateValue(clusterIdentifier)
        val assignments = clusterState.topics.map { it.toTopicAssignments() }.associateBy { it.topic }
        val topicNumberOfConsumers = consumerGroupsProvider.getLatestState(clusterIdentifier)
            .valueOrNull()
            ?.consumerGroups?.values?.mapNotNull { it.getOrNull() }
            ?.flatMap { group -> group.offsets.map { it.topic }.distinct() }
            ?.groupingBy { it }
            ?.eachCount()
            ?: emptyMap()
        val clusterTopicReplicaInfos = replicaDirsService.clusterTopicReplicaInfos(clusterIdentifier)
        val clusterTopicsOffsets = topicOffsetsService.clusterTopicsOffsets(clusterIdentifier)
        val topicLoads = assignments.mapValues { (topic, assignments) ->
            computeTopicLoads(
                topic, topicNumberOfConsumers[topic] ?: 0,
                assignments.partitionAssignments, clusterTopicReplicaInfos[topic], clusterTopicsOffsets[topic]
            )
        }
        return GlobalState(
            brokerIds = clusterState.clusterInfo.nodeIds, loads = topicLoads, assignments = assignments
        )
    }

    private fun KafkaExistingTopic.toTopicAssignments() = TopicAssignments(
        topic = name,
        partitionAssignments = partitionsAssignments.toPartitionReplicasMap()
    )

    private fun computeTopicLoads(
        topicName: TopicName,
        numberOfConsumers: Int,
        assignments: Map<Partition, List<BrokerId>>,
        topicReplicaInfos: TopicReplicaInfos?,
        topicOffsets: TopicOffsets?
    ): TopicLoad {
        return TopicLoad(
            topic = topicName,
            partitionLoads = assignments.map { it }
                .sortedBy { it.key }
                .map { (partition, replicas) ->
                    val sizeBytes = topicReplicaInfos?.partitionBrokerReplicas?.get(partition)
                        ?.let { replicas.mapNotNull { replica -> it[replica] }.firstOrNull() }
                        ?.sizeBytes ?: 0L
                    val msgRate = topicOffsets?.partitionMessageRate?.get(partition)?.upTo24HRate ?: 0.0
                    PartitionLoad(size = sizeBytes, rate = msgRate, consumers = numberOfConsumers)
                }
        )
    }

    fun proposeMigrations(
        clusterIdentifier: KafkaClusterIdentifier, balanceSettings: BalanceSettings
    ): ProposedMigrations {
        val initialGlobalState = getCurrentGlobalState(clusterIdentifier)
        reAssignmentsProvider.getLatestState(clusterIdentifier).valueOrNull()?.run {
            if (topicReAssignments.isNotEmpty()) {
                val topics = topicReAssignments.joinToString { it.topic }
                throw KafkistryIllegalStateException(
                    "Can't propose balance migrations, there are re-assignments in progress for topics: $topics"
                )
            }
        }
        return proposeMigrations(initialGlobalState, balanceSettings)
    }

    fun proposeMigrations(
        initialGlobalState: GlobalState,
        balanceSettings: BalanceSettings
    ): ProposedMigrations {
        var remainingBytesToMigrate = balanceSettings.maxMigrationBytes.takeIf { it > 0 } ?: Long.MAX_VALUE
        var remainingIterations = balanceSettings.maxIterations
        var currentGlobalState = initialGlobalState
        val timeoutDeadline = (System.currentTimeMillis() + balanceSettings.timeLimitTotalMs)
            .takeIf { it > 0 } ?: Long.MAX_VALUE

        val allIterationMigrations = mutableListOf<List<TopicPartitionMigration>>()

        fun Map<TopicName, AssignmentsChange>.toPartitionMigrations(): List<TopicPartitionMigration> {
            return flatMap { (topic, assignmentsChange) ->
                initialGlobalState.partitionMigrationsOf(topic, assignmentsChange)
            }
        }

        while (remainingBytesToMigrate > 0 && remainingIterations > 0 && System.currentTimeMillis() <= timeoutDeadline) {
            //computation
            val migrations = balancer.findRebalanceAction(
                globalState = currentGlobalState,
                balanceObjective = balanceSettings.objective,
                migrationSizeLimitBytes = remainingBytesToMigrate,
                timeoutLimitMs = balanceSettings.timeLimitIterationMs
            ) ?: break

            //record changes
            val (newGlobalState, assignmentChanges) = currentGlobalState.applyMigrations(migrations)
            allIterationMigrations.add(assignmentChanges.toPartitionMigrations())
            val iterationMigrationBytes = migrations.partitions
                .map { it.toTopicPartitionMigration(currentGlobalState).toDataMigration().totalAddBytes }
                .sum()

            //iteration updates
            currentGlobalState = newGlobalState
            remainingIterations--
            remainingBytesToMigrate -= iterationMigrationBytes
        }

        val affectedTopicNames = allIterationMigrations.flatten().map { it.topicPartition.topic }.distinct()
        val topicsAssignmentChanges = affectedTopicNames.associateWith { topicName ->
            val assignmentsBefore = initialGlobalState.assignments[topicName]?.partitionAssignments ?: emptyMap()
            val assignmentsAfter = currentGlobalState.assignments[topicName]?.partitionAssignments ?: emptyMap()
            partitionsAssignor.computeChangeDiff(assignmentsBefore, assignmentsAfter)
        }
        val effectiveMigrations = topicsAssignmentChanges.toPartitionMigrations()

        return ProposedMigrations(
            clusterBalanceBefore = initialGlobalState.extractBalanceStatus(),
            clusterBalanceAfter = currentGlobalState.extractBalanceStatus(),
            iterationMigrations = allIterationMigrations,
            migrations = effectiveMigrations,
            dataMigration = effectiveMigrations.map { it.toDataMigration() }.merge(),
            topicsAssignmentChanges = topicsAssignmentChanges,
        )
    }

    private fun PartitionReAssignment.toTopicPartitionMigration(globalState: GlobalState): TopicPartitionMigration {
        val load = globalState.loads[topicPartition.topic]
            ?.partitionLoads?.get(topicPartition.partition)
            ?: PartitionLoad.ZERO
        return TopicPartitionMigration(topicPartition,
            fromBrokerIds = oldReplicas.minus(newReplicas),
            toBrokerIds = newReplicas.minus(oldReplicas),
            oldLeader = oldReplicas.first(),
            newLeader = newReplicas.first(),
            load
        )
    }

    private fun TopicPartitionMigration.toDataMigration(): DataMigration {
        val sizeBytes = load.size
        return DataMigration(
            reAssignedPartitions = 1,
            totalIOBytes = sizeBytes * (fromBrokerIds.size + toBrokerIds.size),
            totalAddBytes = sizeBytes * toBrokerIds.size,
            totalReleaseBytes = sizeBytes * fromBrokerIds.size,
            perBrokerTotalIOBytes = fromBrokerIds.plus(toBrokerIds).associateWith { sizeBytes },
            perBrokerInputBytes = toBrokerIds.associateWith { sizeBytes },
            perBrokerOutputBytes = fromBrokerIds.associateWith { sizeBytes },
            perBrokerReleasedBytes = fromBrokerIds.associateWith { sizeBytes },
            maxBrokerIOBytes = sizeBytes
        )
    }
}