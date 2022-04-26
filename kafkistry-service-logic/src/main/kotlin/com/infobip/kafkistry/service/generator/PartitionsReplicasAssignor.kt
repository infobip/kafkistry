package com.infobip.kafkistry.service.generator

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.KafkistryValidationException
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

    constructor() : this(System.currentTimeMillis())

    constructor(randomSeed: Long) {
        random = Random(randomSeed)
    }

    private val random: Random

    /**
     * Method for adding new partitions
     */
    fun assignNewPartitionReplicas(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            numberOfNewPartitions: Int,
            replicationFactor: Int,
            existingPartitionLoads: Map<Partition, PartitionLoad>,
            clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf()  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val initialNumPartitions = existingAssignments.size
        val newPartitionCount = initialNumPartitions + numberOfNewPartitions
        val assignmentBrokersLoadHint = if (newPartitionCount % allBrokers.size == 0) {
            //topic itself can be evenly distributed across all nodes equally, no need to consider other topics distribution
            emptyMap()
        } else {
            clusterBrokersLoad
        }
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            repeat(replicationFactor) {
                (initialNumPartitions until newPartitionCount).forEach { partition ->
                    selectBrokerToAssign(partition, assignmentBrokersLoadHint, replicationFactor)
                            .also { broker ->
                                addBrokerForPartition(broker, partition)
                            }
                }
            }
            reBalanceAssignments { it >= existingAssignments.size }
            reBalancePreferredLeaders { it >= existingAssignments.size }
            buildChanges()
        }
    }

    /**
     * Method for increasing replication factor
     */
    fun assignPartitionsNewReplicas(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            replicationFactorIncrease: Int,
            existingPartitionLoads: Map<Partition, PartitionLoad>,
            clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf()  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val replicationFactor = replicationFactorIncrease + existingAssignments.detectReplicationFactor()
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            repeat(replicationFactorIncrease) {
                partitionsBrokers.keys.forEach { partition ->
                    selectBrokerToAssign(partition, clusterBrokersLoad, replicationFactor)
                            .also { broker ->
                                addBrokerForPartition(broker, partition)
                            }
                }
            }
            reBalanceAssignments(true)
            reBalancePreferredLeaders(1)
            buildChanges()
        }
    }

    fun reAssignWithoutBrokers(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            excludedBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>,
            clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf()  //Map of brokerId to number of partitions from all topics on this broker
    ): AssignmentsChange {
        val replicationFactor = existingAssignments.detectReplicationFactor()
        val newAssignments = with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            existingAssignments.forEach { (partition, replicas) ->
                replicas.forEach { brokerId ->
                    if (brokerId in excludedBrokers) {
                        removeBrokerForPartition(brokerId, partition)
                        selectBrokerToAssign(partition, clusterBrokersLoad, replicationFactor) { broker ->
                            broker !in excludedBrokers
                        }.also { broker ->
                            addBrokerForPartition(broker, partition)
                        }
                    }
                }
            }
            reBalanceAssignments(excludedBrokers = excludedBrokers)
            buildChanges()
        }.newAssignments
        val finalAssignments = reBalancePreferredLeaders(newAssignments, allBrokers.minus(excludedBrokers)).newAssignments
        return computeChangeDiff(existingAssignments, finalAssignments)
    }

    fun reduceReplicationFactor(
            existingAssignments: Map<Partition, List<BrokerId>>,
            targetReplicationFactor: Int
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

    private fun Map<Partition, List<BrokerId>>.detectReplicationFactor(): Int = values.map { it.size }.maxOrNull() ?: 0

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
            allBrokers: List<BrokerId>
    ): Int = existingAssignments.values.flatten().freqDisbalance(allBrokers)

    fun leadersDisbalance(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>
    ): Int = existingAssignments.values.mapNotNull { it.firstOrNull() }.freqDisbalance(allBrokers)

    private fun List<BrokerId>.freqDisbalance(allBrokers: List<BrokerId>): Int {
        val brokerLoadCounts = this
            .groupingBy { it }
            .eachCount()
            .let { brokerLoads -> allBrokers.associateWith { (brokerLoads[it] ?: 0) } }
            .values
        val avgLoadFloor = brokerLoadCounts.sum() / allBrokers.size
        val avgLoadCeil = (brokerLoadCounts.sum() + allBrokers.size - 1) / allBrokers.size
        val disbalance1 = brokerLoadCounts.filter { it > avgLoadCeil }.sumOf { it - avgLoadCeil }
        val disbalance2 = brokerLoadCounts.filter { it < avgLoadFloor }.sumOf { avgLoadFloor - it }
        return max(disbalance1, disbalance2)
    }

    fun leadersDeepDisbalance(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>
    ): List<Int> {
        if (existingAssignments.isEmpty()) {
            return emptyList()
        }
        val disbalance = leadersDisbalance(existingAssignments, allBrokers)
        if (existingAssignments.entries.first().value.size <= 1) {
            return listOf(disbalance)
        }
        val subAssignments = existingAssignments
                .filterValues { it.isNotEmpty() }
                .mapValues { it.value.subList(1, it.value.size) }
        val subDisbalance = leadersDeepDisbalance(subAssignments, allBrokers)
        return listOf(disbalance) + subDisbalance
    }

    fun assignmentsDisbalance(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>
    ): AssignmentsDisbalance {
        val totalNumReplicas = existingAssignments.values.asSequence().flatten().count()
        val numPartitions = existingAssignments.values.size
        val replicasDisbalance = replicasDisbalance(existingAssignments, allBrokers)
        val leadersDisbalance = leadersDisbalance(existingAssignments, allBrokers)
        val leadersDeepDisbalance = leadersDeepDisbalance(existingAssignments, allBrokers)
        return AssignmentsDisbalance(
                replicasDisbalance = replicasDisbalance,
                replicasDisbalancePercent = 100.0 * replicasDisbalance / totalNumReplicas,
                leadersDisbalance = leadersDisbalance,
                leadersDisbalancePercent = 100.0 * leadersDisbalance / numPartitions,
                leadersDeepDisbalance = leadersDeepDisbalance
        )
    }

    fun reBalanceReplicasThenLeaders(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            reBalanceAssignments()
            reBalancePreferredLeaders()
            buildChanges()
        }
    }

    fun reBalanceLeadersThenReplicas(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            reBalancePreferredLeaders()
            reBalanceAssignments()
            buildChanges()
        }
    }

    fun reBalanceRoundRobin(
            existingAssignments: Map<Partition, List<BrokerId>>, allBrokers: List<BrokerId>
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

    fun reBalanceRandom(
            existingAssignments: Map<Partition, List<BrokerId>>, allBrokers: List<BrokerId>
    ): AssignmentsChange {
        val partitionCount = existingAssignments.size
        val replicationFactor = existingAssignments.values.first().size
        val tmpAssignmentsChange = (0 until partitionCount)
                .associateWith {
                    allBrokers.shuffled(random).take(replicationFactor)
                }
                .let { reBalanceReplicasThenLeaders(it, allBrokers, emptyMap()) }
        return computeChangeDiff(existingAssignments, tmpAssignmentsChange.newAssignments)
    }

    /**
     * Generate re-assignment to achieve balanced assignment using minimal number of partition replica migrations.
     *
     * @see replicasDisbalance
     */
    fun reBalanceReplicasAssignments(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, existingPartitionLoads)) {
            reBalanceAssignments()
            buildChanges()
        }
    }

    fun reBalancePreferredLeaders(
            existingAssignments: Map<Partition, List<BrokerId>>, allBrokers: List<BrokerId>
    ): AssignmentsChange {
        return with(initializeMappingsContext(existingAssignments, allBrokers, emptyMap())) {
            reBalancePreferredLeaders()
            buildChanges()
        }
    }

    fun reAssignUnwantedPreferredLeaders(
            existingAssignments: Map<Partition, List<BrokerId>>,
            unwantedLeader: BrokerId
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

    private fun AssignmentContext.reBalanceAssignments(
            moveOnlyNewAssignments: Boolean = false,
            excludedBrokers: List<BrokerId> = emptyList(),
            partitionFilter: (Partition) -> Boolean = { true }
    ) {
        val partitionMoveCount = oldAssignments.keys.associateWith { 0 }.toMutableMap()
        var iteration = 0   //fail-safe for inf loop
        while (true) {
            val brokerPartitionCounts = brokersPartitions
                .filterKeys { it !in excludedBrokers }
                .mapValues { (_, partitions) -> partitions.size }
            val overloadedBrokerAndCount = brokerPartitionCounts.maxByOrNull { it.value }!!
            val underloadedBrokerAndCount = brokerPartitionCounts.minByOrNull { it.value }!!
            if (underloadedBrokerAndCount.value + 1 >= overloadedBrokerAndCount.value || iteration > 2 * allBrokers.size * partitionsBrokers.size) {
                break
            }
            val srcBroker = overloadedBrokerAndCount.key
            val dstBroker = underloadedBrokerAndCount.key
            val brokerPartitions = brokersPartitions[srcBroker]!!
                    .sortedBy {
                        partitionMoveCount[it] ?: 0
                    }  //prefer migration of replicas of partition which haven't been touched
                    .sortedBy {
                        existingPartitionLoads[it]?.diskSize ?: 0L
                    }    //prefer migration of partitions smaller in size
            iteration++
            makeSwap(
                srcBroker, dstBroker,
                brokerPartitions, moveOnlyNewAssignments,
                partitionMoveCount,
                excludedBrokers, partitionFilter,
            )
        }
    }

    private fun AssignmentContext.makeSwap(
        src: BrokerId,
        dst: BrokerId,
        brokerPartitions: List<Partition>,
        moveOnlyNewAssignments: Boolean,
        partitionMoveCount: MutableMap<Partition, Int>,
        excludedBrokers: List<BrokerId>,
        partitionFilter: (Partition) -> Boolean,
    ) {
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
                break
            }
            if (moveOnlyNewAssignments && try3waySwap(src, dst, partition, partitionMoveCount, excludedBrokers)) {
                break
            }
        }
    }

    private fun AssignmentContext.try3waySwap(
        srcBroker: BrokerId,
        dstBroker: BrokerId,
        partition: Partition,
        partitionMoveCount: MutableMap<Partition, Int>,
        excludedBrokers: List<BrokerId>
    ): Boolean {
        //try to find 3-way swap
        val pivotBrokers = allBrokers.filter { it != srcBroker && it != dstBroker && it !in excludedBrokers }
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
                return true
            }
        }
        return false
    }

    private fun AssignmentContext.reBalancePreferredLeaders(
            fromDepth: Int = 0,
            partitionFilter: (Partition) -> Boolean = { true }
    ) {
        val replicationFactor = partitionsBrokers.values.map { it.size }.minOrNull() ?: 0
        for (depth in (fromDepth until replicationFactor)) {
            doReBalancePreferredLeadersForDepth(depth, partitionFilter)
        }
    }

    private fun AssignmentContext.doReBalancePreferredLeadersForDepth(
            depthRank: Int,
            partitionFilter: (Partition) -> Boolean
    ) {
        val leadersDisbalance = leadersDeepDisbalance(partitionsBrokers.toImmutableAssignments(), allBrokers)[depthRank]
        val leadersPerBrokerAvgCeil = (partitionsBrokers.size + allBrokers.size - 1) / allBrokers.size
        var iteration = 0   //fail-safe for inf loop
        while (true) {
            val brokerLeaders = brokersLeaders(depthRank)
            val minLoad = brokerLeaders.values.minOf { it.size }
            val maxLoad = brokerLeaders.values.maxOf { it.size }
            if (minLoad + 1 >= maxLoad || iteration > leadersDisbalance * 2) {
                break
            }
            val overloadedBrokers = brokerLeaders
                    .filterValues { it.size > leadersPerBrokerAvgCeil }
                    .takeIf { it.isNotEmpty() }
                    ?: brokerLeaders.filterValues { it.size == leadersPerBrokerAvgCeil }
            val partitionsWithLeaderOnOverloadedBrokers = overloadedBrokers.values.flatten().filter(partitionFilter).distinct()
            iteration++
            val underloadedBrokers = brokerLeaders.asSequence()
                    .filter { it.value.size < leadersPerBrokerAvgCeil }
                    .sortedBy { it.value.size } //prioritize transfer leadership to broker with least leaders
                    .map { it.key }
                    .toList()
            val newLeader = findAvailablePartitionLeaderBroker(
                    partitionsWithLeaderOnOverloadedBrokers, underloadedBrokers, depthRank
            )
            if (newLeader != null) {
                setPreferredLeader(newLeader.first, newLeader.second, depthRank)
            } else {
                val possibleUnderloadedSinks = underloadedBrokers
                        .map { underloadedBrokerId ->
                            (brokersPartitions[underloadedBrokerId] ?: emptyList())
                                    .filter(partitionFilter)
                                    .filter { partitionsBrokers[it]!!.indexOf(underloadedBrokerId) >= depthRank }
                                    .map { BrokerPartition(underloadedBrokerId, it) }
                        }
                        .flatten()
                for (underloadedBrokerPartition in possibleUnderloadedSinks) {
                    val newLeaderSwapRoute = findSwapRoute(
                            overloadedBrokers.keys,
                            underloadedBrokerPartition,
                            mutableSetOf(underloadedBrokerPartition.partition),
                            partitionFilter,
                            depthRank
                    )
                    if (newLeaderSwapRoute.isNotEmpty()) {
                        val solution = newLeaderSwapRoute.plus(underloadedBrokerPartition).reversed().dropLast(1)
                        solution.forEach {
                            setPreferredLeader(it.partition, it.broker, depthRank)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun AssignmentContext.findSwapRoute(
            overloadedBrokerIds: Set<BrokerId>,
            destinationSink: BrokerPartition,
            visitedPartitions: MutableSet<Partition>,
            partitionFilter: (Partition) -> Boolean,
            depthRank: Int
    ): List<BrokerPartition> {
        val sinkLeaderBroker = (partitionsBrokers[destinationSink.partition] ?: return emptyList())[depthRank]
        if (sinkLeaderBroker in overloadedBrokerIds) {
            return listOf(BrokerPartition(sinkLeaderBroker, destinationSink.partition))
        }
        val hopPartitions = brokersPartitions[sinkLeaderBroker] ?: return emptyList()
        return hopPartitions.asSequence()
                .filter(partitionFilter)
                .filter { partitionsBrokers[it]!!.indexOf(sinkLeaderBroker) >= depthRank }
                .filter { it !in visitedPartitions }
                .map {
                    val nextSink = BrokerPartition(sinkLeaderBroker, it)
                    visitedPartitions.add(it)
                    val cycle = findSwapRoute(overloadedBrokerIds, nextSink, visitedPartitions, partitionFilter, depthRank)
                    visitedPartitions.remove(it)
                    cycle.plus(nextSink)
                }
                .filter { it.isNotEmpty() }
                .firstOrNull()
                ?: emptyList()
    }

    private fun AssignmentContext.findAvailablePartitionLeaderBroker(
            partitionsWithLeaderOnOverloadedBrokers: List<Partition>,
            underloadedBrokers: Collection<BrokerId>,
            depthRank: Int
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
            newAssignments: Map<Partition, List<BrokerId>>
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
        return assignments
                .filterValues { it.isNotEmpty() }
                .mapValues { (_, brokerIds) -> brokerIds.first() }
                .filter { (partition, leaderBrokerId) -> this[partition]?.firstOrNull() != leaderBrokerId }
    }

    private fun initializeMappingsContext(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad>
    ): AssignmentContext {
        val partitionsBrokers: PartitionsBrokers = existingAssignments
                .mapValues { it.value.toMutableList() }
                .toMutableMap()
        val brokersPartitions: BrokersPartitions = allBrokers
                .associateWith { mutableListOf<Partition>() }
                .toMutableMap()
                .apply {
                    partitionsBrokers.forEach { (partition, brokers) ->
                        brokers.forEach { broker ->
                            computeIfAbsent(broker) { mutableListOf() }.add(partition)
                        }
                    }
                }
        val lastPartitionLastBroker = existingAssignments.maxByOrNull { it.key }?.value?.lastOrNull() ?: -1
        return AssignmentContext(
                allBrokers, existingAssignments, partitionsBrokers, brokersPartitions, lastPartitionLastBroker, existingPartitionLoads
        )
    }

    private fun AssignmentContext.selectBrokerToAssign(
            partition: Partition,
            clusterBrokersLoad: Map<BrokerId, BrokerLoad>,
            replicationFactor: Int,
            brokerFilter: (BrokerId) -> Boolean = { true }
    ): BrokerId {
        //find broker that has least amount of replicas but does not have selecting partition
        val comparator = nothingComparing<BrokerPartitions>()
                //prefer brokers with smallest number partitions of same topic
                .thenComparing { partitions: BrokerPartitions -> partitions.value.size }
                //prefer brokers with lower disk usage in general (from other topics)
                .thenComparing { partitions: BrokerPartitions -> clusterBrokersLoad[partitions.key]?.diskBytes ?: 0L }
                //prefer brokers with lower number of partition replicas in general (from other topics)
                .thenComparing { partitions: BrokerPartitions -> clusterBrokersLoad[partitions.key]?.numReplicas ?: 0 }
                //prefer brokers with greater id than previously selected
                .thenComparing { (brokerId, _): BrokerPartitions ->
                    when {
                        brokerId > lastSelected -> -1
                        else -> 1
                    }
                }
        return brokersPartitions.asSequence()
                .filter { partition !in it.value }
                .filter { brokerFilter(it.key) }
                .minWithOrNull(comparator)
                ?.key
                ?: throw KafkistryValidationException(
                        "There are no available brokers to generate assignment, num brokers: %d, wanted replication factor: %d".format(
                                brokersPartitions.size, replicationFactor
                        )
                )
    }

    private fun AssignmentContext.buildChanges(): AssignmentsChange {
        //prettyPrint()
        val newAssignments = partitionsBrokers.toImmutableAssignments()
        return computeChangeDiff(oldAssignments, newAssignments)
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
            allBrokers: List<Int>,
            topicProperties: TopicProperties
    ): AssignmentsValidation {
        val partitionsRange = PartitionRange(0 until topicProperties.partitionCount)
        val missingPartitions = partitionsRange
                .filter { it !in assignments.keys }
                .map { "Partition $it is missing in the assignments" }
        val unexpectedPartitions = assignments.keys
                .filter { it !in partitionsRange }
                .map { "Partition $it is not expected, expected in range $partitionsRange" }
        val partitionProblems = partitionsRange
                .associate { partition ->
                    val brokerAssignments = assignments[partition]
                            ?: return@associate partition to PartitionValidation(false, listOf("Missing assignments of partition"))
                    val duplicateBrokers = brokerAssignments.groupingBy { it }.eachCount().filterValues { it > 1 }
                    val unknownBrokers = brokerAssignments.filter { it !in allBrokers }
                    val partitionReplicationFactor = brokerAssignments.size
                    val partitionProblems = listOfNotNull(
                            duplicateBrokers.takeIf { it.isNotEmpty() }?.let { "There are brokers ids=${it.keys} listed more than once" },
                            unknownBrokers.takeIf { it.isNotEmpty() }?.let { "There are brokers ids=$it which are unknown to cluster" },
                            partitionReplicationFactor.takeIf { it != topicProperties.replicationFactor }?.let { "Replication factor $it is not as expected of ${topicProperties.replicationFactor}" }
                    )
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
            override val endInclusive: Partition
    ) : Iterable<Partition> by (start..endInclusive), ClosedRange<Partition> {
        constructor(range: IntRange) : this(range.first, range.last)
    }

}

private data class AssignmentContext(
        val allBrokers: List<BrokerId>,
        val oldAssignments: Map<Partition, List<BrokerId>>,
        val partitionsBrokers: PartitionsBrokers,
        val brokersPartitions: BrokersPartitions,
        var lastSelected: BrokerId,
        val existingPartitionLoads: Map<Partition, PartitionLoad>
) {

    fun addBrokerForPartition(broker: BrokerId, partition: Partition) {
        lastSelected = broker
        partitionsBrokers.putPartitionReplica(partition, broker)
        brokersPartitions.putBrokersReplica(broker, partition)
    }

    fun removeBrokerForPartition(broker: BrokerId, partition: Partition) {
        partitionsBrokers.removePartitionReplica(partition, broker)
        brokersPartitions.removeBrokersReplica(broker, partition)
    }

    fun brokerAssignedOnPartition(broker: BrokerId, partition: Partition): Boolean {
        return partitionsBrokers[partition]?.contains(broker) ?: false
    }

    fun brokerWasInitiallyAssignedOnPartition(broker: BrokerId, partition: Partition): Boolean {
        return oldAssignments[partition]?.contains(broker) ?: false
    }

    fun setPreferredLeader(partition: Partition, leaderBrokerId: BrokerId, rank: Int) {
        val partitionReplicas = partitionsBrokers[partition]!!
        //println("Setting preferred leader: partition=$partition broker=$leaderBrokerId rank=$rank")
        partitionReplicas
                .subList(rank, partitionReplicas.size)
                .preferredLeader(leaderBrokerId)
        //prettyPrint()
    }

    fun brokersLeaders(depthRank: Int): Map<BrokerId, List<Partition>> {
        return partitionsBrokers
                .filterValues { it.isNotEmpty() }
                .map { (partition, brokers) -> brokers[depthRank] to partition }
                .groupBy({ it.first }, { it.second })
                .let { allBrokers.associateWith { broker -> (it[broker] ?: emptyList()) } }
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

    private fun BrokersPartitions.putBrokersReplica(broker: BrokerId, partition: Partition) {
        this.computeIfAbsent(broker) { mutableListOf() }.add(partition)
    }

    private fun PartitionsBrokers.removePartitionReplica(partition: Partition, broker: BrokerId) {
        this[partition]?.remove(broker)
    }

    private fun BrokersPartitions.removeBrokersReplica(broker: BrokerId, partition: Partition) {
        this[broker]?.remove(partition)
    }

    fun prettyPrint() {
        val result = StringBuilder()
        result.append(" ".repeat(3)).append("|")
        allBrokers.forEach {
            result.append("%2d ".format(it))
        }
        result.append("\n")
        result.append("---+")
        result.append("---".repeat(allBrokers.size))
        result.append("\n")
        val numPartitions = partitionsBrokers.size
        for (partition: Partition in (0 until numPartitions)) {
            result.append("%2d ".format(partition)).append("|")
            allBrokers.forEach { broker ->
                val leader = partitionsBrokers[partition]?.takeIf { it.isNotEmpty() }?.get(0) == broker
                val assigned = partitionsBrokers[partition]?.run { broker in this } ?: false
                val wasAssigned = oldAssignments[partition]?.run { broker in this } ?: false
                val mark = if (leader) "L" else "x"
                result.append(when (assigned) {
                    true -> if (wasAssigned) " $mark " else "($mark)"
                    false -> "   "
                })
            }
            result.append(" ").append(partitionsBrokers[partition]).append("\n")
        }
        println(result)
    }
}

private fun PartitionsBrokers.toImmutableAssignments(): Map<Partition, List<BrokerId>> = mapValues { it.value.toList() }
private fun <T> nothingComparing(): Comparator<T> = comparing { _: T -> 0 }
private typealias PartitionsBrokers = MutableMap<Partition, MutableList<BrokerId>>
private typealias BrokersPartitions = MutableMap<BrokerId, MutableList<Partition>>
private typealias BrokerPartitions = Map.Entry<BrokerId, MutableList<Partition>>

private data class BrokerPartition(
        val broker: BrokerId,
        val partition: Partition
)
