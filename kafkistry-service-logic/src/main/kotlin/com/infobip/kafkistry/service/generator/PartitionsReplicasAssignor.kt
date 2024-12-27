package com.infobip.kafkistry.service.generator

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.BrokerRack
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.KafkistryValidationException
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor.Companion.DEBUG_LOG
import org.springframework.stereotype.Component
import java.lang.IllegalStateException
import java.util.*
import java.util.Comparator.comparing
import kotlin.math.max

/**
 * PartitionsReplicasAssignor is a functional utility that can generate new assignments.
 * Properties of generating:
 *  - deterministic out based only on given input (except random re-balance)
 *  - assignment assigning algorithm is greedy type on algorithm.
 *     for every replica that needs to be added {
 *        select best broker candidate to assign partition replica onto
 *     }
 */
@Component
class PartitionsReplicasAssignor {

    /**
     * Method for adding new partitions
     */
    fun assignNewPartitionReplicas(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        numberOfNewPartitions: Int,
        replicationFactor: Int,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf(),  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val initialNumPartitions = existingAssignments.size
        val newPartitionCount = initialNumPartitions + numberOfNewPartitions
        val evenPartitionsDistribution = newPartitionCount % allBrokers.size == 0
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads, clusterBrokersLoad)) {
            repeat(replicationFactor) {
                (initialNumPartitions until newPartitionCount).forEach { partition ->
                    selectBrokerToAssign(partition, it == 0, replicationFactor, evenPartitionsDistribution).also { broker ->
                        addBrokerForPartition(broker, partition).also { changed() }
                    }
                }
            }
            reBalanceAssignments { it >= existingAssignments.size }
            reBalanceReplicaRacks { it >= existingAssignments.size }
            reBalancePreferredLeaders { it >= existingAssignments.size }
            buildChanges()
        }
    }

    /**
     * Method for increasing replication factor
     */
    fun assignPartitionsNewReplicas(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        replicationFactorIncrease: Int,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf(),  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val replicationFactor = replicationFactorIncrease + existingAssignments.detectReplicationFactor()
        val evenPartitionsDistribution = existingAssignments.size % allBrokers.size == 0
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads, clusterBrokersLoad)) {
            repeat(replicationFactorIncrease) {
                partitionsBrokers.keys.forEach { partition ->
                    selectBrokerToAssign(partition, false, replicationFactor, evenPartitionsDistribution).also { broker ->
                        addBrokerForPartition(broker, partition).also { changed() }
                    }
                }
            }
            reBalanceAssignments(true)
            reBalanceReplicaRacks(true)
            reBalancePreferredLeaders(1)
            buildChanges()
        }
    }

    fun reAssignWithoutBrokers(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        excludedBrokerIds: List<BrokerId>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf(),  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val replicationFactor = existingAssignments.detectReplicationFactor()
        val evenPartitionsDistribution = existingAssignments.size % (allBrokers.size - excludedBrokerIds.size) == 0
        val newAssignments = with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads, clusterBrokersLoad)) {
            existingAssignments.forEach { (partition, replicas) ->
                replicas.forEachIndexed { rank, brokerId ->
                    if (brokerId in excludedBrokerIds) {
                        removeBrokerForPartition(brokerId, partition)
                        selectBrokerToAssign(partition, rank == 0, replicationFactor, evenPartitionsDistribution) { broker ->
                            broker !in excludedBrokerIds
                        }.also { broker ->
                            addBrokerForPartition(broker, partition).also { changed() }
                        }
                    }
                }
            }
            reBalanceAssignments(excludedBrokersIds = excludedBrokerIds)
            reBalanceReplicaRacks()
            buildChanges()
        }.newAssignments
        val excludedBrokerIdsSet = excludedBrokerIds.toSet()
        val finalAssignments = reBalancePreferredLeaders(newAssignments,
            allBrokers.filter { it.id !in excludedBrokerIdsSet }).newAssignments
        return computeChangeDiff(existingAssignments, finalAssignments)
    }

    fun reduceReplicationFactor(
        existingAssignments: Map<Partition, List<BrokerId>>,
        targetReplicationFactor: Int,
    ): AssignmentsChange {
        val currentReplicationFactor = existingAssignments.detectReplicationFactor()
        if (targetReplicationFactor > currentReplicationFactor) {
            throw KafkistryValidationException(
                "Can't reduce replication factor to $targetReplicationFactor which is more than current $currentReplicationFactor"
            )
        }
        val newAssignments = existingAssignments.mapValues { it.value.take(targetReplicationFactor) }
        return computeChangeDiff(existingAssignments, newAssignments)
    }

    private fun Map<Partition, List<BrokerId>>.detectReplicationFactor(): Int = values.maxOfOrNull { it.size } ?: 0

    /**
     * Disbalance (Int) is measure how much are assignments out of balance.
     * More precisely, how many partition replicas need to be moved to be balanced.
     *
     * ### legend:
     * ```
     *  columns = brokers
     *  rows = partitions
     *  x  = partition replica assigned to broker
     * (x) = partition replica currently assigned and candidate to be migrated
     * (.) = partition replica currently not assigned but candidate to be migrated onto
     * ```
     *
     * ### example 1: disbalance = 0
     * ```
     *    |  1  2  3
     *  0 |  x  x
     *  1 |     x  x
     *  2 |  x     x
     *  ```
     * ### example 2: disbalance = 1
     * ```
     *    |  1  2  3  4  5  6
     *  0 |  x  x
     *  1 |        x  x
     *  2 |          (x) x (.)
     *  ```
     * ### example 3: disbalance = 2
     * ```
     *    |  1  2  3  4  5  6
     *  0 |  x  x  x (x)   (.)
     *  1 | (.) x (x) x  x
     *  2 |        x  x  x  x
     *  ```
     *
     *  @see reBalanceReplicasAssignments
     */
    fun replicasDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
    ): Int = existingAssignments.values.flatten().freqDisbalance(allBrokers.ids())

    fun leadersDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
    ): Int = existingAssignments.values.mapNotNull { it.firstOrNull() }.freqDisbalance(allBrokers.ids())

    private fun <T> List<T>.freqDisbalance(allBrokersIds: List<T>): Int {
        val brokerLoadCounts = this.groupingBy { it }.eachCount()
            .let { brokerLoads -> allBrokersIds.associateWith { (brokerLoads[it] ?: 0) } }.values
        val avgLoadFloor = brokerLoadCounts.sum() / allBrokersIds.size
        val avgLoadCeil = (brokerLoadCounts.sum() + allBrokersIds.size - 1) / allBrokersIds.size
        val disbalance1 = brokerLoadCounts.filter { it > avgLoadCeil }.sumOf { it - avgLoadCeil }
        val disbalance2 = brokerLoadCounts.filter { it < avgLoadFloor }.sumOf { avgLoadFloor - it }
        return max(disbalance1, disbalance2)
    }

    fun leadersDeepDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
    ): List<Int> {
        if (existingAssignments.isEmpty()) {
            return emptyList()
        }
        val disbalance = leadersDisbalance(existingAssignments, allBrokers)
        if (existingAssignments.entries.first().value.size <= 1) {
            return listOf(disbalance)
        }
        val subAssignments =
            existingAssignments.filterValues { it.isNotEmpty() }.mapValues { it.value.subList(1, it.value.size) }
        val subDisbalance = leadersDeepDisbalance(subAssignments, allBrokers)
        return listOf(disbalance) + subDisbalance
    }

    fun partitionReplicaPerRackDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
    ): AssignmentsDisbalance.PartitionsPerRackDisbalance {
        val allRacks = allBrokers.map { it.rack }.distinct()
        val brokerRacks = allBrokers.associate { it.id to it.rack }
        return partitionReplicaPerRackDisbalance(existingAssignments, allRacks, brokerRacks)
    }

    private fun partitionReplicaPerRackDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allRacks: List<BrokerRack?>,
        brokerRacks: Map<BrokerId, BrokerRack?>,
    ): AssignmentsDisbalance.PartitionsPerRackDisbalance {
        val partitionRacks = existingAssignments.mapValues { (_, brokers) ->
            brokers.map { brokerRacks[it] }
        }
        val singleRackPartitions = if (allRacks.distinct().size > 1) {
            partitionRacks
                .filterValues { it.size > 1 && it.distinct().size == 1 }
                .keys.toList()
        } else {
            emptyList()
        }
        return AssignmentsDisbalance.PartitionsPerRackDisbalance.of(
            partitionRacks.mapValues { it.value.freqDisbalance(allRacks) }, singleRackPartitions,
        )
    }

    fun assignmentsDisbalance(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
    ): AssignmentsDisbalance {
        val totalNumReplicas = existingAssignments.values.asSequence().flatten().count()
        val numPartitions = existingAssignments.values.size
        val replicasDisbalance = replicasDisbalance(existingAssignments, allBrokers)
        val leadersDisbalance = leadersDisbalance(existingAssignments, allBrokers)
        val leadersDeepDisbalance = leadersDeepDisbalance(existingAssignments, allBrokers)
        val partitionsPerRackDisbalance = partitionReplicaPerRackDisbalance(existingAssignments, allBrokers)
        return AssignmentsDisbalance(
            replicasDisbalance = replicasDisbalance,
            replicasDisbalancePercent = 100.0 * replicasDisbalance / totalNumReplicas,
            leadersDisbalance = leadersDisbalance,
            leadersDisbalancePercent = 100.0 * leadersDisbalance / numPartitions,
            leadersDeepDisbalance = leadersDeepDisbalance,
            partitionsPerRackDisbalance = partitionsPerRackDisbalance,
        )
    }

    fun reBalanceBrokersLoads(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad>,  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val newAssignments = assignNewPartitionReplicas(
            existingAssignments = emptyMap(),
            allBrokers = allBrokers,
            numberOfNewPartitions = existingAssignments.size,
            replicationFactor = existingAssignments.values.first().size,
            existingPartitionLoads = existingPartitionLoads,
            clusterBrokersLoad = clusterBrokersLoad,
        )
        return computeChangeDiff(existingAssignments, newAssignments.newAssignments)
    }

    fun reBalanceReplicasThenLeaders(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad>,
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads, clusterBrokersLoad)) {
            reBalanceAssignments()
            reBalanceReplicaRacks()
            reBalancePreferredLeaders()
            buildChanges()
        }
    }

    fun reBalanceRoundRobin(
        existingAssignments: Map<Partition, List<BrokerId>>, allBrokers: List<Broker>,
    ): AssignmentsChange {
        val tmpAssignmentsChange = assignNewPartitionReplicas(
            existingAssignments = emptyMap(),
            allBrokers = allBrokers,
            numberOfNewPartitions = existingAssignments.size,
            replicationFactor = existingAssignments.values.first().size,
            existingPartitionLoads = emptyMap(),
            clusterBrokersLoad = emptyMap()
        )
        return computeChangeDiff(existingAssignments, tmpAssignmentsChange.newAssignments)
    }

    /**
     * Generate re-assignment to achieve balanced assignment using minimal number of partition replica migrations.
     *
     * @see replicasDisbalance
     */
    fun reBalanceReplicasAssignments(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad> = emptyMap(),
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads, clusterBrokersLoad)) {
            reBalanceAssignments()
            reBalanceReplicaRacks()
            buildChanges()
        }
    }

    fun reBalancePreferredLeaders(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad> = emptyMap(),
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, emptyMap(), clusterBrokersLoad)) {
            reBalancePreferredLeaders()
            buildChanges()
        }
    }

    fun reAssignUnwantedPreferredLeaders(
        existingAssignments: Map<Partition, List<BrokerId>>,
        unwantedLeader: BrokerId,
    ): AssignmentsChange {
        val newAssignments = existingAssignments.mapValues { (_, brokers) ->
            if (brokers.size <= 1) {
                throw KafkistryValidationException("Can't make unwantedLeader=$unwantedLeader when replication factor is less than 2")
            }
            if (unwantedLeader == brokers[0]) {
                brokers.toMutableList().apply {
                    removeAt(0)
                    add(1, unwantedLeader)
                }.toList()
            } else {
                brokers
            }
        }
        return computeChangeDiff(existingAssignments, newAssignments)
    }

    private fun AssignmentContext.reBalanceReplicaRacks(
        moveOnlyNewAssignments: Boolean = false,
        partitionFilter: (Partition) -> Boolean = { true },
    ) {
        log("Going to re-balance replica racks...")
        var iteration = 0
        while (true) {
            val partitionRackCounts = partitionsRacks
                .filterKeys(partitionFilter)
                .mapValues { (_, racks) -> racks.groupingBy { it }.eachCount() }
            if (partitionRackCounts.isEmpty()) break
            val maxRackCount = partitionRackCounts.maxOf { (_, rCounts) -> rCounts.values.max() }
            val minRackCount = partitionRackCounts.minOf { (_, rCounts) -> rCounts.values.min() }
            val givers = partitionRackCounts
                .mapValues { (_, rCounts) -> rCounts.filterValues { it > minRackCount } }
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, rCounts) -> rCounts.keys }
            val takers = partitionRackCounts
                .mapValues { (_, rCounts) -> rCounts.filterValues { it < maxRackCount } }
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, rCounts) -> rCounts.keys }
            if (givers.isEmpty() || takers.isEmpty()) {
                break
            }
            if (DEBUG_LOG.decisions) {
                println("givers: $givers")
                println("takers: $takers")
            }
            var performedSwap = false
            for ((giverPartition, giverRacks) in givers) {
                val giverBrokerIds = partitionsBrokers.getValue(giverPartition)
                    .filter { brokerRacks[it] in giverRacks }
                    .filter { !moveOnlyNewAssignments || !brokerWasInitiallyAssignedOnPartition(it, giverPartition) }
                if (giverBrokerIds.isEmpty()) {
                    break
                }
                val takerPartitionBrokers = takers
                    .filterKeys { it != giverPartition }
                    .mapValues { (_, takerRacks) -> takerRacks - giverRacks }
                    .mapValues { (takerPartition, takerRacks) ->
                        partitionsBrokers.getValue(takerPartition)
                            .filter { brokerRacks[it] in takerRacks }
                            .filter { it !in giverBrokerIds }
                            .filter { !moveOnlyNewAssignments || !brokerWasInitiallyAssignedOnPartition(it, takerPartition) }
                    }
                    .filterValues { it.isNotEmpty() }
                if (takerPartitionBrokers.isEmpty()) {
                    break
                }

                fun maybeSwap(
                    giverBrokerId: BrokerId, giverPartition: Partition,
                    takerBrokerId: BrokerId, takerPartition: Partition,
                ): Boolean {
                    val takerCurrentRacks = partitionsRacks.getValue(takerPartition)
                    val takerNewRacks = partitionsBrokers.getValue(takerPartition)
                        .filter { it != takerBrokerId}
                        .plus(giverBrokerId)
                        .map { brokerRacks[it] }
                    val takerMaxUsedRackCount = takerCurrentRacks.groupingBy { it }.eachCount().maxOf { it.value }
                    val takerNewMaxUsedRackCount = takerNewRacks.groupingBy { it }.eachCount().maxOf { it.value }
                    if (takerNewMaxUsedRackCount > takerMaxUsedRackCount) {
                        return false
                    }
                    if (DEBUG_LOG.decisions) {
                        println("swapping giver=(p=$giverPartition,b=$giverBrokerId) with taker=(p=$takerPartition,b=$takerBrokerId)")
                    }
                    removeBrokerForPartition(giverBrokerId, giverPartition)
                    removeBrokerForPartition(takerBrokerId, takerPartition)
                    addBrokerForPartition(giverBrokerId, takerPartition)
                    addBrokerForPartition(takerBrokerId, giverPartition)
                    changed()
                    return true
                }

                combinationsLoop@ for (giverBrokerId in giverBrokerIds) {
                    for ((takerPartition, takerBrokerIds) in takerPartitionBrokers) {
                        for (takerBrokerId in takerBrokerIds) {
                            if (takerBrokerId in partitionsBrokers[giverPartition].orEmpty()) {
                                continue
                            }
                            if (giverBrokerId in partitionsBrokers[takerPartition].orEmpty()) {
                                continue
                            }
                            if (maybeSwap(giverBrokerId, giverPartition, takerBrokerId, takerPartition)) {
                                performedSwap = true
                                break@combinationsLoop
                            }
                        }
                    }
                }
            }
            iteration++
            if (!performedSwap || iteration >= partitionsBrokers.size) {
                break
            }
        }
        log("Done re-balancing replica racks")
    }

    private fun AssignmentContext.reBalanceAssignments(
        moveOnlyNewAssignments: Boolean = false,
        excludedBrokersIds: List<BrokerId> = emptyList(),
        partitionFilter: (Partition) -> Boolean = { true },
    ) {
        log("Going to re-balance assignments...")
        val partitionMoveCount = oldAssignments.keys.associateWith { 0 }.toMutableMap()
        var iteration = 0   //fail-safe for inf loop
        while (true) {
            val brokerPartitionCounts = brokersPartitions
                .filterKeys { it !in excludedBrokersIds }
                .mapValues { (_, partitions) -> partitions.size }
            val (overloadedBrokers, overloadedCount) = brokerPartitionCounts.topKeysByValue(Math::max)
            val (underloadedBrokers, underloadedCount) = brokerPartitionCounts.topKeysByValue(Math::min)
            if (underloadedCount + 1 >= overloadedCount || iteration > 2 * allBrokers.size * partitionsBrokers.size) {
                break
            }
            iteration++
            combinationsLoop@ for (srcBroker in overloadedBrokers) {
                for (dstBroker in underloadedBrokers) {
                    val brokerPartitions = brokersPartitions.getValue(srcBroker)
                        .sortedBy { partitionMoveCount[it] ?: 0 }  //prefer migration of replicas of non-touched partitions
                        .sortedBy { existingPartitionLoads[it]?.diskSize ?: 0L } //prefer migration of size smaller partitions
                    val swapped = makeSwap(
                        srcBroker, dstBroker,
                        brokerPartitions, moveOnlyNewAssignments,
                        partitionMoveCount,
                        excludedBrokersIds, partitionFilter,
                    )
                    if (swapped) {
                        break@combinationsLoop
                    }
                }
            }
        }
        log("Done re-balancing assignments")
    }

    private fun <K, V> Map<K, V>.topKeysByValue(valueSelector: (V, V) -> V): Pair<Set<K>, V> {
        val selected = values.reduce(valueSelector)
        return filterValues { it == selected }.keys to selected
    }

    private fun Map<BrokerId, Int>.withAdded(leaderCounts: Map<BrokerId, Int>): Map<BrokerId, Int> {
        return (keys + leaderCounts.keys).associateWith { (this[it] ?: 0) + (leaderCounts[it] ?: 0) }
    }

    private fun AssignmentContext.makeSwap(
        src: BrokerId,
        dst: BrokerId,
        brokerPartitions: List<Partition>,
        moveOnlyNewAssignments: Boolean,
        partitionMoveCount: MutableMap<Partition, Int>,
        excludedBrokers: List<BrokerId>,
        partitionFilter: (Partition) -> Boolean,
    ): Boolean {
        for (partition in brokerPartitions) {
            if (!partitionFilter(partition)) {
                continue
            }
            if (moveOnlyNewAssignments && brokerWasInitiallyAssignedOnPartition(src, partition)) {
                continue
            }
            if (!brokerAssignedOnPartition(dst, partition)) {
                removeBrokerForPartition(src, partition)
                addBrokerForPartition(dst, partition)
                partitionMoveCount.merge(partition, 1, Int::plus)
                changed()
                return true
            }
            if (moveOnlyNewAssignments && try3waySwap(src, dst, partition, partitionMoveCount, excludedBrokers)) {
                break
            }
        }
        return false
    }

    private fun AssignmentContext.try3waySwap(
        srcBroker: BrokerId,
        dstBroker: BrokerId,
        partition: Partition,
        partitionMoveCount: MutableMap<Partition, Int>,
        excludedBrokers: List<BrokerId>,
    ): Boolean {
        //try to find 3-way swap
        val pivotBrokers = allBrokerIds.filter { it != srcBroker && it != dstBroker && it !in excludedBrokers }
        for (pivotBroker in pivotBrokers) {
            if (brokerAssignedOnPartition(pivotBroker, partition)) {
                continue
            }
            val pivotPartitions = brokersPartitions[pivotBroker] ?: continue
            for (pivotPartition in pivotPartitions) {
                if (brokerWasInitiallyAssignedOnPartition(pivotBroker, pivotPartition)) {
                    continue
                }
                if (brokerAssignedOnPartition(dstBroker, pivotPartition)) {
                    continue
                }
                //found 3-way swap
                removeBrokerForPartition(srcBroker, partition)
                addBrokerForPartition(pivotBroker, partition)
                removeBrokerForPartition(pivotBroker, pivotPartition)
                addBrokerForPartition(dstBroker, pivotPartition)
                partitionMoveCount.merge(partition, 1, Int::plus)
                partitionMoveCount.merge(pivotPartition, 1, Int::plus)
                changed()
                return true
            }
        }
        return false
    }

    private fun AssignmentContext.reBalancePreferredLeaders(
        fromDepth: Int = 0,
        partitionFilter: (Partition) -> Boolean = { true },
    ) {
        log("Going to re-balance preferred leaders...")
        val replicationFactor = partitionsBrokers.values.minOfOrNull { it.size } ?: 0
        for (depth in (fromDepth until replicationFactor)) {
            doReBalancePreferredLeadersForDepth(depth, partitionFilter)
        }
        log("Done re-balancing preferred leaders")
    }

    private fun AssignmentContext.doReBalancePreferredLeadersForDepth(
        depthRank: Int,
        partitionFilter: (Partition) -> Boolean,
    ) {
        val leadersDisbalance = leadersDeepDisbalance(partitionsBrokers.toImmutableAssignments(), allBrokers)[depthRank]
        val leadersPerBrokerAvgCeil = (partitionsBrokers.size + allBrokers.size - 1) / allBrokers.size
        val leadersPerBrokerAvg = partitionsBrokers.size.toDouble() / allBrokers.size
        var iteration = -1   //fail-safe for inf loop
        while (true) {
            iteration++
            val brokerLeaders = brokersLeaders(depthRank)
            val minLoad = brokerLeaders.values.minOf { it.size }
            val maxLoad = brokerLeaders.values.maxOf { it.size }
            if (minLoad + 1 >= maxLoad || iteration > leadersDisbalance * 2) {
                break
            }
            val numStrictlyOverloadedBrokers = brokerLeaders.count { it.value.size >= leadersPerBrokerAvg + 1 }
            val numStrictlyUnderloadedBrokers = brokerLeaders.count { it.value.size <= leadersPerBrokerAvg - 1 }
            val (overloadedFilter, underloadedFilter) = when {
                numStrictlyOverloadedBrokers > 0 && numStrictlyUnderloadedBrokers > 0 -> {
                    brokerFilter { it > leadersPerBrokerAvgCeil } to brokerFilter { it <= leadersPerBrokerAvg - 1 }
                }

                numStrictlyOverloadedBrokers > 0 && numStrictlyUnderloadedBrokers == 0 -> {
                    brokerFilter { it > leadersPerBrokerAvgCeil } to brokerFilter { it <= leadersPerBrokerAvgCeil }
                }

                numStrictlyOverloadedBrokers == 0 && numStrictlyUnderloadedBrokers > 0 -> {
                    brokerFilter { it >= leadersPerBrokerAvgCeil } to brokerFilter { it <= leadersPerBrokerAvg - 1 }
                }

                else -> {
                    brokerFilter { it >= leadersPerBrokerAvgCeil } to brokerFilter { it < leadersPerBrokerAvgCeil }
                }
            }
            val overloadedBrokers = brokerLeaders.asSequence().filter(overloadedFilter)
                .sortedByDescending { it.value.size } //prioritize transfer leadership from broker with most leaders
                .associate { it.toPair() }
            val underloadedBrokers = brokerLeaders.asSequence().filter(underloadedFilter)
                .sortedBy { it.value.size } //prioritize transfer leadership to broker with least leaders
                .map { it.key }.toList()
            val partitionsWithLeaderOnOverloadedBrokers =
                overloadedBrokers.values.flatten().filter(partitionFilter).distinct()
            val newLeader = findAvailablePartitionLeaderBroker(
                partitionsWithLeaderOnOverloadedBrokers, underloadedBrokers, depthRank
            )
            if (newLeader != null) {
                setPreferredLeader(newLeader.first, newLeader.second, depthRank)
                changed()
            } else {
                val possibleUnderloadedSinks = underloadedBrokers.map { underloadedBrokerId ->
                        (brokersPartitions[underloadedBrokerId] ?: emptyList())
                            .filter(partitionFilter)
                            .filter { partitionsBrokers[it]!!.indexOf(underloadedBrokerId) >= depthRank }
                            .map { BrokerPartition(underloadedBrokerId, it) }
                    }.flatten()
                for (underloadedBrokerPartition in possibleUnderloadedSinks) {
                    val newLeaderSwapRoute = findSwapRoute(
                        FindRouteCtx(
                            partitionFilter, overloadedBrokers.keys, depthRank,
                            mutableSetOf(underloadedBrokerPartition.partition),
                        ),
                        underloadedBrokerPartition,
                    )
                    if (newLeaderSwapRoute.isNotEmpty()) {
                        val solution = newLeaderSwapRoute.plus(underloadedBrokerPartition).reversed().dropLast(1)
                        solution.forEach {
                            setPreferredLeader(it.partition, it.broker, depthRank)
                        }
                        changed()
                        break
                    }
                }
            }
        }
        //if we can't have equal number of leaders per broker anyway, check possible swaps
        //to have better cluster balance
        if (depthRank == 0 && partitionsBrokers.size % allBrokers.size != 0) {
            val brokersLeadersBaseCounts = clusterBrokersLoad.mapValues { it.value.numPreferredLeaders }
            val globalAvgLeaderCount = brokersLeadersBaseCounts.values.sum().toDouble() / allBrokers.size
            val topicAvgLeaderCount = brokersLeaders(depthRank).values.sumOf { it.size }.toDouble() / allBrokers.size

            var iterations = 0
            while (iterations++ < partitionsBrokers.size) {
                val brokersLeaders = brokersLeaders(depthRank)
                val topicBrokersLeaders = brokersLeaders.mapValues { it.value.size }
                val totalLoads = brokersLeadersBaseCounts.withAdded(topicBrokersLeaders)
                //brokers that are both globally overloaded and overloaded in scope of this topic
                val givers = totalLoads.filterValues { it > globalAvgLeaderCount + topicAvgLeaderCount + 0.5}.keys
                    .filter { (topicBrokersLeaders[it] ?: 0) > topicAvgLeaderCount }

                //brokers that are both globally underloaded and underloaded in scope of this topic
                val takers = totalLoads.filterValues { it < globalAvgLeaderCount + topicAvgLeaderCount - 0.5 }.keys
                    .filter { (topicBrokersLeaders[it] ?: 0) < topicAvgLeaderCount }
                    .sortedBy { totalLoads[it] ?: 0 }
                if (givers.isEmpty() || takers.isEmpty()) {
                    break
                }
                val (partition, taker) = partitionsBrokers.asSequence()
                    .filter { partitionFilter(it.key) }
                    .mapNotNull { (partition, allReplicas) ->
                        val replicas = allReplicas.drop(depthRank)
                        val leader = replicas.first()
                        if (leader !in givers) {
                            return@mapNotNull null
                        }
                        val followers = replicas.drop(1)
                        val taker = takers.find { it in followers }
                            ?: return@mapNotNull null
                        partition to taker
                    }
                    .firstOrNull()
                    ?: continue
                setPreferredLeader(partition, taker, depthRank)
                changed()
            }
        }
    }

    private fun brokerFilter(
        filter: (Int) -> Boolean,
    ): (Map.Entry<BrokerId, List<Partition>>) -> Boolean = { filter(it.value.size) }

    private data class FindRouteCtx(
        val partitionFilter: (Partition) -> Boolean,
        val overloadedBrokerIds: Set<BrokerId>,
        val depthRank: Int,
        val visitedPartitions: MutableSet<Partition> = mutableSetOf(),
    )

    private fun AssignmentContext.findSwapRoute(
        ctx: FindRouteCtx,
        destinationSink: BrokerPartition,
        maxDepth: Int = 20,
    ): List<BrokerPartition> {
        if (maxDepth == 0) {
            return emptyList()
        }
        val sinkLeaderBroker = (partitionsBrokers[destinationSink.partition] ?: return emptyList())[ctx.depthRank]
        if (sinkLeaderBroker in ctx.overloadedBrokerIds) {
            return listOf(BrokerPartition(sinkLeaderBroker, destinationSink.partition))
        }
        val hopPartitions = brokersPartitions[sinkLeaderBroker] ?: return emptyList()
        for (hopPartition in hopPartitions) {
            if (!ctx.partitionFilter(hopPartition)) continue
            if (partitionsBrokers[hopPartition]!!.indexOf(sinkLeaderBroker) < ctx.depthRank) continue
            if (hopPartition in ctx.visitedPartitions) continue
            ctx.visitedPartitions.add(hopPartition)
            val nextSink = BrokerPartition(sinkLeaderBroker, hopPartition)
            val cycle = findSwapRoute(ctx, nextSink, maxDepth = maxDepth - 1)
            if (cycle.isNotEmpty()) {
                return cycle.plus(nextSink)
            }
        }
        return emptyList()
    }

    private fun AssignmentContext.findAvailablePartitionLeaderBroker(
        partitionsWithLeaderOnOverloadedBrokers: List<Partition>,
        underloadedBrokers: Collection<BrokerId>,
        depthRank: Int,
    ): Pair<Partition, BrokerId>? {
        return underloadedBrokers.asSequence()
            .mapNotNull { underloadedBrokerId ->
                partitionsWithLeaderOnOverloadedBrokers
                    .firstOrNull { partition ->
                        val brokersOfPartition = partitionsBrokers[partition]!!
                        underloadedBrokerId in brokersOfPartition.subList(depthRank, brokersOfPartition.size)
                    }
                    ?.let { partition -> partition to underloadedBrokerId }
            }
            .firstOrNull()
    }

    fun computeChangeDiff(
        oldAssignments: Map<Partition, List<BrokerId>>,
        newAssignments: Map<Partition, List<BrokerId>>,
    ): AssignmentsChange {
        return AssignmentsChange(
            oldAssignments = oldAssignments,
            newAssignments = newAssignments,
            addedPartitionReplicas = oldAssignments.replicasDiff(newAssignments),
            removedPartitionReplicas = newAssignments.replicasDiff(oldAssignments),
            newLeaders = oldAssignments.leadersDiff(newAssignments),
            exLeaders = newAssignments.leadersDiff(oldAssignments),
            reAssignedPartitionsCount = newAssignments.count { (partition, replicas) ->
                oldAssignments[partition] != replicas
            },
        )
    }

    private fun Map<Partition, List<BrokerId>>.replicasDiff(assignments: Map<Partition, List<BrokerId>>): Map<Partition, List<BrokerId>> {
        return assignments
            .mapNotNull { (partition, brokerIds) ->
                val thisBrokerIds = this[partition] ?: return@mapNotNull partition to brokerIds
                (partition to brokerIds.filter { it !in thisBrokerIds }).takeIf { it.second.isNotEmpty() }
            }
            .associate { it }
    }

    private fun Map<Partition, List<BrokerId>>.leadersDiff(assignments: Map<Partition, List<BrokerId>>): Map<Partition, BrokerId> {
        return assignments.filterValues { it.isNotEmpty() }.mapValues { (_, brokerIds) -> brokerIds.first() }
            .filter { (partition, leaderBrokerId) -> this[partition]?.firstOrNull() != leaderBrokerId }
    }

    private fun initializeMappingsContext(
        existingAssignments: Map<Partition, List<BrokerId>>,
        allBrokers: List<Broker>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
        clusterBrokersLoad: Map<BrokerId, BrokerLoad>,
    ): AssignmentContext {
        val brokerRacks = allBrokers.associate { it.id to it.rack }
        val partitionsBrokers: PartitionsBrokers = existingAssignments
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val partitionRacks = existingAssignments
            .mapValues { (_, brokerIds) -> brokerIds.map { brokerRacks[it] }.toMutableList() }
            .toMutableMap()
        val racksPartitions = existingAssignments
            .flatMap { (partition, brokers) ->
                brokers.map { brokerRacks[it] to partition }.toMutableSet()
            }
            .groupByTo(LinkedHashMap(), { it.first }, { it.second })
        val brokersPartitions: BrokersPartitions =
            allBrokers.map { it.id }.associateWith { mutableListOf<Partition>() }.toMutableMap().apply {
                    partitionsBrokers.forEach { (partition, brokers) ->
                        brokers.forEach { broker ->
                            computeIfAbsent(broker) { mutableListOf() }.add(partition)
                        }
                    }
                }
        val lastPartitionLastBroker = existingAssignments.maxByOrNull { it.key }?.value?.lastOrNull() ?: NO_BROKER
        return AssignmentContext(
            allBrokers,
            brokerRacks,
            existingAssignments,
            partitionsBrokers,
            partitionRacks,
            brokersPartitions,
            racksPartitions,
            lastPartitionLastBroker,
            existingPartitionLoads,
            clusterBrokersLoad.withoutTopic(existingAssignments, existingPartitionLoads),
        ).also { it.changed() }
    }

    private fun AssignmentContext.selectBrokerToAssign(
        partition: Partition,
        isLeader: Boolean,
        replicationFactor: Int,
        evenPartitionsDistribution: Boolean,
        brokerFilter: (BrokerId) -> Boolean = { true },
    ): BrokerId {
        val occupiedRacks = partitionsRacks[partition].orEmpty()
        //find broker that has least amount of replicas but does not have selecting partition
        val comparator = nothingComparing<BrokerPartitions>()
            //prefer brokers with smallest number partitions of same topic
            .thenComparing("num-partitions") { partitions: BrokerPartitions -> partitions.value.size }
            //prefer brokers on different rack than the ones where the partition is already assigned
            .thenComparing("occupied-rack") { (brokerId, _) -> if (brokerRacks[brokerId] in occupiedRacks) 1 else 0 }
            //prefer brokers that are on rack which is a rack of more brokers
            .thenComparing("frequent-rack") { (brokerId, _) -> -rackBrokers[brokerRacks[brokerId]].orEmpty().size }
            //prefer brokers on rack that has the least partitions
            //.thenComparing("num-per-racks") { (brokerId, _) -> racksPartitions[brokerRacks[brokerId]]?.size ?: 0 }
            .thenComparing("broker-leaders") { (brokerId, _) -> if (isLeader && !evenPartitionsDistribution) clusterBrokersLoad[brokerId]?.numPreferredLeaders ?: 0 else 0 }
            //prefer brokers with lower disk usage in general (from other topics)
            .thenComparing("disk-load") { partitions: BrokerPartitions -> if (!evenPartitionsDistribution) clusterBrokersLoad[partitions.key]?.diskBytes ?: 0L else 0 }
            //prefer brokers with lower number of partition replicas in general (from other topics)
            .thenComparing("num-replicas") { partitions: BrokerPartitions -> if (!evenPartitionsDistribution) clusterBrokersLoad[partitions.key]?.numReplicas ?: 0 else 0 }
            //lastly, prefer brokers with greater id than previously selected
            .thenComparing("order-id") { (brokerId, _): BrokerPartitions ->
                when {
                    brokerId > lastSelected -> -1
                    else -> 1
                }
            }
        return brokersPartitions.asSequence()
            .filter { partition !in it.value }
            .filter { brokerFilter(it.key) }
            .minWithOrNull(comparator)?.key
            ?: throw KafkistryValidationException(
                "There are no available brokers to generate assignment, num brokers: %d, wanted replication factor: %d".format(
                    allBrokerIds.filter(brokerFilter).size, replicationFactor
                )
        )
    }

    private fun <T : Comparable<T>> Comparator<BrokerPartitions>.thenComparing(what: String, extractor: (BrokerPartitions) -> T): Comparator<BrokerPartitions> {
        return thenComparing { brokerPartitions ->
            extractor(brokerPartitions).also {
                if (DEBUG_LOG.decisions) {
                    println("Comparing broker=${brokerPartitions.key} by $what -> $it")
                }
            }
        }
    }


    private fun AssignmentContext.buildChanges(): AssignmentsChange {
        val newAssignments = partitionsBrokers.toImmutableAssignments()
        return computeChangeDiff(oldAssignments, newAssignments)
    }

    private fun Map<BrokerId, BrokerLoad>.withoutTopic(
        existingAssignments: Map<Partition, List<BrokerId>>,
        existingPartitionLoads: Map<Partition, PartitionLoad>,
    ): Map<BrokerId, BrokerLoad> {
        val topicBrokerLoads = existingAssignments
            .flatMap { (partition, brokerIds) ->
                val partitionLoad = existingPartitionLoads[partition]
                brokerIds.mapIndexed { rank, brokerId ->
                    brokerId to (partitionLoad?.toBrokerLoad(isLeader = rank == 0) ?: BrokerLoad.ZERO)
                }
            }
            .groupBy ({ it.first }, {it.second})
            .mapValues { it.value.reduce(BrokerLoad::plus) }
        return this.mapValues { (brokerId, totalLoad) ->
            totalLoad - (topicBrokerLoads[brokerId] ?: BrokerLoad.ZERO)
        }
    }

    /**
     * Check if given assignment is valid.
     * To be valid it means to:
     *  - cover all partitions
     *  - have correct replication factor
     *  - reference only available broker ids
     */
    fun validateAssignments(
        assignments: Map<Partition, List<BrokerId>>,
        allBrokerIds: List<BrokerId>,
        topicProperties: TopicProperties,
    ): AssignmentsValidation {
        val partitionsRange = PartitionRange(0 until topicProperties.partitionCount)
        val missingPartitions =
            partitionsRange.filter { it !in assignments.keys }.map { "Partition $it is missing in the assignments" }
        val unexpectedPartitions = assignments.keys.filter { it !in partitionsRange }
            .map { "Partition $it is not expected, expected in range $partitionsRange" }
        val partitionProblems = partitionsRange.associate { partition ->
                val brokerAssignments = assignments[partition] ?: return@associate partition to PartitionValidation(
                    false,
                    listOf("Missing assignments of partition")
                )
                val duplicateBrokers = brokerAssignments.groupingBy { it }.eachCount().filterValues { it > 1 }
                val unknownBrokers = brokerAssignments.filter { it !in allBrokerIds }
                val partitionReplicationFactor = brokerAssignments.size
                val partitionProblems = listOfNotNull(duplicateBrokers.takeIf { it.isNotEmpty() }
                    ?.let { "There are brokers ids=${it.keys} listed more than once" },
                    unknownBrokers.takeIf { it.isNotEmpty() }
                        ?.let { "There are brokers ids=$it which are unknown to cluster" },
                    partitionReplicationFactor.takeIf { it != topicProperties.replicationFactor }
                        ?.let { "Replication factor $it is not as expected of ${topicProperties.replicationFactor}" })
                partition to PartitionValidation(
                    valid = partitionProblems.isEmpty(), problems = partitionProblems
                )
            }
        val overallProblems = missingPartitions + unexpectedPartitions
        val valid = overallProblems.isEmpty() && partitionProblems.all { it.value.valid }
        return AssignmentsValidation(
            valid, overallProblems, partitionProblems
        )
    }

    private class PartitionRange(
        override val start: Partition,
        override val endInclusive: Partition,
    ) : Iterable<Partition> by (start..endInclusive), ClosedRange<Partition> {
        constructor(range: IntRange) : this(range.first, range.last)
    }

    enum class LogInclude(val state: Boolean, val decisions: Boolean) {
        NONE(false, false),
        STATE(true, false),
        STATE_DECISIONS(true, true),
    }

    companion object {
        var DEBUG_LOG: LogInclude = LogInclude.NONE
    }

}

private data class AssignmentContext(
    val allBrokers: List<Broker>,
    val brokerRacks: Map<BrokerId, BrokerRack?>,
    val oldAssignments: Map<Partition, List<BrokerId>>,
    val partitionsBrokers: PartitionsBrokers,
    val partitionsRacks: PartitionsRacks,
    val brokersPartitions: BrokersPartitions,
    val racksPartitions: RacksPartitions,
    var lastSelected: BrokerId,
    val existingPartitionLoads: Map<Partition, PartitionLoad>,
    val clusterBrokersLoad: Map<BrokerId, BrokerLoad>,
) {

    val allBrokerIds: List<BrokerId> = allBrokers.ids()
    val allRacks: List<BrokerRack?> = allBrokers.map { it.rack }.distinct()
    val rackBrokers: Map<BrokerRack?, List<BrokerId>> = allBrokers.groupBy ({ it.rack }, { it.id })

    fun addBrokerForPartition(broker: BrokerId, partition: Partition) {
        lastSelected = broker
        partitionsBrokers.putPartitionReplica(partition, broker)
        brokersPartitions.putBrokersReplica(broker, partition)
        val rack = brokerRacks[broker]
        partitionsRacks.putPartitionRack(partition, rack)
        racksPartitions.putRackPartition(rack, partition)
    }

    fun removeBrokerForPartition(broker: BrokerId, partition: Partition) {
        partitionsBrokers.removePartitionReplica(partition, broker)
        brokersPartitions.removeBrokersReplica(broker, partition)
        partitionsRacks.reComputePartition(partition)
        racksPartitions.reComputeRack(brokerRacks[broker])
    }

    fun brokerAssignedOnPartition(broker: BrokerId, partition: Partition): Boolean {
        return partitionsBrokers[partition]?.contains(broker) ?: false
    }

    fun brokerWasInitiallyAssignedOnPartition(broker: BrokerId, partition: Partition): Boolean {
        return oldAssignments[partition]?.contains(broker) ?: false
    }

    fun setPreferredLeader(partition: Partition, leaderBrokerId: BrokerId, rank: Int) {
        val partitionReplicas = partitionsBrokers[partition]!!
        if (DEBUG_LOG.decisions) {
            println("Setting preferred leader: partition=$partition broker=$leaderBrokerId rank=$rank")
        }
        partitionReplicas.subList(rank, partitionReplicas.size).preferredLeader(leaderBrokerId)
    }

    fun brokersLeaders(depthRank: Int): Map<BrokerId, List<Partition>> {
        return partitionsBrokers.filterValues { it.isNotEmpty() }
            .map { (partition, brokers) -> brokers[depthRank] to partition }.groupBy({ it.first }, { it.second })
            .let { allBrokerIds.associateWith { broker -> (it[broker] ?: emptyList()) } }
    }

    private fun MutableList<BrokerId>.preferredLeader(brokerId: BrokerId) {
        if (brokerId !in this) {
            throw IllegalStateException("Broker id $brokerId is not in list $this, can't be preferred leader")
        }
        sort()
        while (this[0] != brokerId) {
            add(removeAt(0))
        }
    }

    private fun PartitionsBrokers.putPartitionReplica(partition: Partition, broker: BrokerId) {
        this.computeIfAbsent(partition) { mutableListOf() }.add(broker)
    }

    private fun PartitionsRacks.putPartitionRack(partition: Partition, rack: BrokerRack?) {
        this.computeIfAbsent(partition) { mutableListOf() }.add(rack)
    }

    private fun RacksPartitions.putRackPartition(rack: BrokerRack?, partition: Partition) {
        this.computeIfAbsent(rack) { mutableListOf() }.add(partition)
    }

    private fun BrokersPartitions.putBrokersReplica(broker: BrokerId, partition: Partition) {
        this.computeIfAbsent(broker) { mutableListOf() }.add(partition)
    }

    private fun PartitionsBrokers.removePartitionReplica(partition: Partition, broker: BrokerId) {
        this[partition]?.remove(broker)
    }

    private fun BrokersPartitions.removeBrokersReplica(broker: BrokerId, partition: Partition) {
        this[broker]?.remove(partition)
    }

    private fun PartitionsRacks.reComputePartition(partition: Partition) {
        this[partition] = partitionsBrokers[partition].orEmpty().map { brokerRacks[it] }.toMutableList()
    }

    private fun RacksPartitions.reComputeRack(rack: BrokerRack?) {
        this[rack] = partitionsRacks.entries.asSequence()
            .filter { rack in it.value }
            .map { it.key }
            .toMutableList()
    }

    fun changed() {
        if (DEBUG_LOG.state) {
            prettyPrint()
        }
    }

    fun log(msg: String) {
        if (DEBUG_LOG.state) {
            println(msg)
        }
    }

    fun prettyPrint() {
        val hasRacks = brokerRacks.values.toSet() != setOf(null)
        val result = StringBuilder().apply {
            fun addSeparatorRow() = append("----+").append("----".repeat(allBrokers.size)).append("+----").append("\n")
            append(" P\\B|")
            allBrokers.ids().forEach { append("%3d ".format(it)) }
            append("|\n")
            addSeparatorRow()
            val numPartitions = partitionsBrokers.size
            for (partition: Partition in (0 until numPartitions)) {
                append("%3d ".format(partition)).append("|")
                allBrokers.forEach { broker ->
                    val leader = partitionsBrokers[partition]?.takeIf { it.isNotEmpty() }?.get(0) == broker.id
                    val assigned = partitionsBrokers[partition]?.run { broker.id in this } ?: false
                    val wasAssigned = oldAssignments[partition]?.run { broker.id in this } ?: false
                    val mark = if (leader) "L" else "x"
                    append(
                        when (assigned) {
                            true -> if (wasAssigned) "  $mark " else " ($mark)"
                            false -> "    "
                        }
                    )
                }
                append("| ").append(partitionsBrokers[partition]?.map { it.toString().padStart(2)})
                if (hasRacks) {
                    append(" ").append(partitionsBrokers[partition].orEmpty().map { brokerRacks[it] })
                }
                append("\n")
            }
            addSeparatorRow()
            append("BrId|")
            allBrokers.forEach { append("%3d ".format(it.id)) }
            append("|\n")
            addSeparatorRow()
            append(" #R |")
            allBrokers.forEach { broker ->
                append("%3d ".format(brokersPartitions[broker.id].orEmpty().size))
            }
            append("|\n")
            append(" #L |")
            allBrokers.forEach { broker ->
                val numLeaders = partitionsBrokers.count { (_, brokers) -> broker.id == brokers[0] }
                append("%3d ".format(numLeaders))
            }
            append("|\n")
            append("Gl#R|")
            allBrokers.forEach { broker ->
                val clusterReplicas = clusterBrokersLoad[broker.id]?.numReplicas ?: 0
                val topicReplicas = brokersPartitions[broker.id]?.size ?: 0
                append("%3d ".format(clusterReplicas + topicReplicas))
            }
            append("|\n")
            append("Gl#L|")
            allBrokers.forEach { broker ->
                val clusterPrefLeaders = clusterBrokersLoad[broker.id]?.numPreferredLeaders ?: 0
                val topicPrefLeaders = partitionsBrokers.count { (_, brokers) -> broker.id == brokers[0] }
                append("%3d ".format(clusterPrefLeaders + topicPrefLeaders))
            }
            append("|\n")
        }
        println(result)
    }

}

private const val NO_BROKER = -1

private fun PartitionsBrokers.toImmutableAssignments(): Map<Partition, List<BrokerId>> = mapValues { it.value.toList() }
private fun <T> nothingComparing(): Comparator<T> = comparing { 0 }
private typealias PartitionsBrokers = MutableMap<Partition, MutableList<BrokerId>>
private typealias PartitionsRacks = MutableMap<Partition, MutableList<BrokerRack?>>
private typealias RacksPartitions = MutableMap<BrokerRack?, MutableList<Partition>>
private typealias BrokersPartitions = MutableMap<BrokerId, MutableList<Partition>>
private typealias BrokerPartitions = Map.Entry<BrokerId, MutableList<Partition>>

private data class BrokerPartition(
    val broker: BrokerId,
    val partition: Partition,
)
