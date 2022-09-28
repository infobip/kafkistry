package com.infobip.kafkistry.service.generator.balance

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.generator.balance.BalancePriority.*
import com.infobip.kafkistry.service.generator.balance.ChangeMode.*
import kotlin.math.*

operator fun PartitionLoad.plus(other: PartitionLoad) = PartitionLoad(
    size + other.size, rate + other.rate, consumers + other.consumers
)

operator fun PartitionLoad.minus(other: PartitionLoad) = PartitionLoad(
    size - other.size, rate - other.rate, consumers - other.consumers
)

operator fun PartitionLoad.div(divisor: Number) = PartitionLoad(
    (size / divisor.toDouble()).roundToLong(), rate / divisor.toDouble(), (consumers / divisor.toDouble()).roundToInt()
)

operator fun BrokerLoad.plus(other: BrokerLoad) = BrokerLoad(
    size + other.size, rate + other.rate,
    consumeRate + other.consumeRate, replicationRate + other.replicationRate,
    replicas + other.replicas, leaders + other.leaders
)

operator fun BrokerLoad.minus(other: BrokerLoad) = BrokerLoad(
    size - other.size, rate - other.rate,
    consumeRate - other.consumeRate, replicationRate - other.replicationRate,
    replicas - other.replicas, leaders - other.leaders
)

operator fun BrokerLoad.times(factor: Number) = BrokerLoad(
    size * factor.toDouble(),
    rate * factor.toDouble(),
    consumeRate * factor.toDouble(),
    replicationRate * factor.toDouble(),
    replicas * factor.toDouble(),
    leaders * factor.toDouble(),
)

operator fun BrokerLoad.div(divisor: Number) = times(1.0 / divisor.toDouble())
operator fun BrokerLoad.unaryMinus() = times(-1.0)

operator fun BrokerLoad.div(divisor: BrokerLoad) = BrokerLoad(
    if (divisor.size > 0) size / divisor.size else 0.0,
    if (divisor.rate > 0) rate / divisor.rate else 0.0,
    if (divisor.consumeRate > 0) consumeRate / divisor.consumeRate else 0.0,
    if (divisor.replicationRate > 0) replicationRate / divisor.replicationRate else 0.0,
    if (divisor.replicas > 0) replicas / divisor.replicas else 0.0,
    if (divisor.leaders > 0) leaders / divisor.leaders else 0.0,
)

fun Collection<PartitionLoad>.average(): PartitionLoad = fold(PartitionLoad.ZERO, PartitionLoad::plus) / size
fun Collection<BrokerLoad>.average(): BrokerLoad = fold(BrokerLoad.ZERO, BrokerLoad::plus) / size

fun PartitionLoad.toBrokerLoad(leader: Boolean, replicationFactor: Int) = BrokerLoad(
    size = size.toDouble(),
    rate = rate,
    consumeRate = rate * consumers,
    replicationRate = if (leader && replicationFactor > 1) rate * (replicationFactor - 1) else 0.0,
    replicas = 1.0,
    leaders = if (leader) 1.0 else 0.0
)

fun GlobalState.brokerLoads(): CollectionLoad<BrokerId, BrokerLoad> {
    val result = mutableMapOf<BrokerId, BrokerLoad>()
    assignments.forEach { (_, topicAssignments) ->
        topicAssignments.partitionAssignments.forEach { (partition, brokers) ->
            brokers.forEachIndexed { rank, brokerId ->
                val partitionLoad = loads[topicAssignments.topic]?.partitionLoads?.get(partition)!!
                val brokerLoad = partitionLoad.toBrokerLoad(
                    leader = rank == 0, replicationFactor = brokers.size
                )
                result.merge(brokerId, brokerLoad, BrokerLoad::plus)
            }
        }
    }
    val loads = brokerIds.associateWith { BrokerLoad.ZERO }
        .plus(result)
        .map { it }
        .sortedBy { it.key }
        .associate { it.toPair() }
    return CollectionLoad(
        elements = loads,
        average = loads.values.average()
    )
}

fun GlobalState.topicPartitionsLoad(): Map<TopicName, CollectionLoad<Partition, PartitionLoad>> {
    return loads.mapValues { (_, topicLoads) ->
        topicLoads.partitionLoads
            .mapIndexed { partition: Partition, load: PartitionLoad -> partition to load }
            .toMap()
            .let {
                CollectionLoad(it, topicLoads.partitionLoads.average())
            }
    }
}

fun Map<BrokerId, BrokerLoad>.diffBy(by: (BrokerLoad) -> Double): Double {
    val metricValues = values.map(by)
    val max = metricValues.maxOrNull() ?: 0.0
    val min = metricValues.minOrNull() ?: 0.0
    return max - min
}

fun Map<BrokerId, BrokerLoad>.deviationBy(by: (BrokerLoad) -> Double): Double {
    if (isEmpty()) return 0.0
    val metricValues = values.map(by)
    val avg = metricValues.average()
    return sqrt(metricValues.sumOf { (it - avg)*(it - avg) } / size)
}

fun MutableMap<TopicName, TopicAssignments>.applyMigrations(migrations: Iterable<PartitionReAssignment>) {
    for (migration in migrations) {
        applyMigration(migration)
    }
}

fun GlobalState.applyMigrations(migrations: Migrations): Pair<GlobalState, Map<TopicName, AssignmentsChange>> {
    val assignor = PartitionsReplicasAssignor()
    val mutableAssignments = assignments.toMutableMap()
    mutableAssignments.applyMigrations(migrations.partitions)
    val migratedTopics = migrations.partitions.map { it.topicPartition.topic }.distinct()
    migratedTopics.forEach { topic ->
        val topicAssignments = mutableAssignments[topic] ?: return@forEach
        if (assignor.leadersDisbalance(topicAssignments.partitionAssignments, brokerIds) > 0) {
            val balancedAssignments = assignor.reBalancePreferredLeaders(
                topicAssignments.partitionAssignments, brokerIds
            )
            mutableAssignments[topic] = topicAssignments.copy(partitionAssignments = balancedAssignments.newAssignments)
        }
    }
    return copy(assignments = mutableAssignments) to migratedTopics.associateWith { topic ->
        val assignmentsBefore = assignments[topic]?.partitionAssignments ?: emptyMap()
        val assignmentsAfter = mutableAssignments[topic]?.partitionAssignments ?: emptyMap()
        assignor.computeChangeDiff(oldAssignments = assignmentsBefore, newAssignments = assignmentsAfter)
    }
}

fun CollectionLoad<BrokerId, BrokerLoad>.brokersLoadDiff() = BrokerLoad(
    size = elements.diffBy { it.size },
    rate = elements.diffBy { it.rate },
    consumeRate = elements.diffBy { it.consumeRate },
    replicationRate = elements.diffBy { it.replicationRate },
    replicas = elements.diffBy { it.replicas },
    leaders = elements.diffBy { it.leaders },
)

fun CollectionLoad<BrokerId, BrokerLoad>.brokersLoadDeviation() = BrokerLoad(
    size = elements.deviationBy { it.size },
    rate = elements.deviationBy { it.rate },
    consumeRate = elements.deviationBy { it.consumeRate },
    replicationRate = elements.deviationBy { it.replicationRate },
    replicas = elements.deviationBy { it.replicas },
    leaders = elements.deviationBy { it.leaders },
)

fun CollectionLoad<BrokerId, BrokerLoad>.normalizedLoadDeviation(balanceObjective: BalanceObjective): Double {
    //closer each broker's load is to average load the better
    val deviation = brokersLoadDeviation()
    return deviation.normalize(average, balanceObjective)
}

fun MutableMap<TopicName, TopicAssignments>.applyMigration(migration: PartitionReAssignment) {
    val topicAssignments = this[migration.topicPartition.topic] ?: return
    val partitionAssignments = topicAssignments.partitionAssignments
    this[migration.topicPartition.topic] = TopicAssignments(
        topicAssignments.topic,
        partitionAssignments.plus(migration.topicPartition.partition to migration.newReplicas)
    )
}

fun GlobalState.brokerAssignments(): Map<BrokerId, BrokerAssignments> {
    val brokerAssignments = mutableMapOf<BrokerId, MutableList<TopicPartition>>()
    assignments.forEach { (_, topicAssignments) ->
        topicAssignments.partitionAssignments.forEach { (partition, brokers) ->
            brokers.forEach { brokerId ->
                val topicPartition = TopicPartition(topicAssignments.topic, partition)
                brokerAssignments.computeIfAbsent(brokerId) { mutableListOf() }.add(topicPartition)
            }
        }
    }
    return brokerIds.associateWith { emptyList<TopicPartition>() }
        .plus(brokerAssignments)
        .mapValues { (brokerId, topicPartitions) ->
            BrokerAssignments(
                brokerId, topicPartitions, topicPartitions.groupBy({ it.topic }, { it.partition })
            )
        }
}

/**
 * Score is abstract value which can be used to compare different loads
 * Score == 0 means balance
 * Score < 0 means underload comparing to average
 * Score > 0 means overload comparing to average
 */
fun BrokerLoad.score(average: BrokerLoad, balanceObjective: BalanceObjective): Double {
    val sizeSkew = skewOf(average) { it.size }
    val rateSkew = skewOf(average) { it.rate }
    val consumeRateSkew = skewOf(average) { it.consumeRate }
    val replicationRateSkew = skewOf(average) { it.replicationRate }
    val replicasSkew = skewOf(average) { it.replicas }
    val leadersSkew = skewOf(average) { it.leaders }
    return balanceObjective.combine(sizeSkew, rateSkew, consumeRateSkew, replicationRateSkew, replicasSkew, leadersSkew)
}

fun BrokerLoad.normalize(average: BrokerLoad, balanceObjective: BalanceObjective): Double {
    val sizeRatio = ratioOf(average) { it.size }
    val rateRatio = ratioOf(average) { it.rate }
    val consumeRateRatio = ratioOf(average) { it.consumeRate }
    val replicationRateRatio = ratioOf(average) { it.replicationRate }
    val replicasRatio = ratioOf(average) { it.replicas }
    val leadersRatio = ratioOf(average) { it.leaders }
    return balanceObjective.combine(
        sizeRatio,
        rateRatio,
        consumeRateRatio,
        replicationRateRatio,
        replicasRatio,
        leadersRatio
    )
}

fun BrokerLoad.classifyLoadDiffType(average: BrokerLoad, balanceObjective: BalanceObjective): LoadDiffType {
    return balanceObjective.classifyLoadDiffType(
        sizeSkew = skewOf(average) { it.size },
        rateSkew = skewOf(average) { it.rate },
        consumeRateSkew = skewOf(average) { it.consumeRate },
        replicationRateSkew = skewOf(average) { it.replicationRate },
        replicasSkew = skewOf(average) { it.replicas },
        leadersSkew = skewOf(average) { it.leaders },
    )
}

private inline fun BrokerLoad.skewOf(average: BrokerLoad, of: (BrokerLoad) -> Number): Double {
    val avgVal = of(average).toDouble()
    val value = of(this).toDouble()
    return if (avgVal > 0) (value - avgVal) / avgVal else 0.0
}

private inline fun BrokerLoad.ratioOf(average: BrokerLoad, of: (BrokerLoad) -> Number): Double {
    val avgVal = of(average).toDouble()
    val value = of(this).toDouble()
    return if (avgVal > 0) value / avgVal else 0.0
}

fun BalanceObjective.combine(
    sizeScore: Double, rateScore: Double,
    consumeRateScore: Double, replicationRateScore: Double,
    replicasScore: Double, leadersScore: Double
): Double {
    if (priorities.isEmpty()) {
        return (sizeScore + rateScore + consumeRateScore + replicationRateScore + replicasScore + leadersScore) / 6
    }
    return priorities.sumOf { priority ->
        when (priority) {
            SIZE -> sizeScore
            RATE -> rateScore
            CONSUMERS_RATE -> consumeRateScore
            REPLICATION_RATE -> replicationRateScore
            REPLICAS_COUNT -> replicasScore
            LEADERS_COUNT -> leadersScore
        }
    }.div(priorities.size)
}

private enum class Signum {
    ZERO, POSITIVE, NEGATIVE;
}

fun BalanceObjective.classifyLoadDiffType(
    sizeSkew: Double, rateSkew: Double,
    consumeRateSkew: Double, replicationRateSkew: Double,
    replicasSkew: Double, leadersSkew: Double
): LoadDiffType {
    fun Double.signum() = when {
        this < 0 -> Signum.NEGATIVE
        this > 0 -> Signum.POSITIVE
        else -> Signum.ZERO
    }

    val prioritiesToUse = priorities.takeIf { it.isNotEmpty() } ?: BalancePriority.values().toList()
    val signumSet = prioritiesToUse.map { priority ->
        when (priority) {
            SIZE -> sizeSkew.signum()
            RATE -> rateSkew.signum()
            CONSUMERS_RATE -> consumeRateSkew.signum()
            REPLICATION_RATE -> replicationRateSkew.signum()
            REPLICAS_COUNT -> replicasSkew.signum()
            LEADERS_COUNT -> leadersSkew.signum()
        }
    }.toSet()
    return when {
        Signum.POSITIVE in signumSet && Signum.NEGATIVE in signumSet -> LoadDiffType.MIXED
        Signum.POSITIVE in signumSet -> LoadDiffType.OVERLOADED
        Signum.NEGATIVE in signumSet -> LoadDiffType.UNDERLOADED
        else -> LoadDiffType.BALANCED
    }
}

fun GlobalState.createGlobalContext(): GlobalContext {
    val brokersLoad = brokerLoads()
    val topicPartitionsLoad = topicPartitionsLoad()
    val brokersAssignments = brokerAssignments()
    val brokerTopicPartitionLoads = brokerTopicPartitionLoads(brokerIds, brokersAssignments, topicPartitionsLoad)
    return GlobalContext(
        state = this,
        topicsAssignments = assignments,
        brokersLoad = brokersLoad,
        topicPartitionsLoad = topicPartitionsLoad,
        brokersAssignments = brokersAssignments,
        brokerTopicPartitionLoads = brokerTopicPartitionLoads
    )
}

fun GlobalState.extractBalanceStatus(): ClusterBalanceStatus {
    val brokerLoads = brokerLoads()
    val brokersLoadDiff = brokerLoads.brokersLoadDiff()
    val combinedLoadDiff = BalancePriority.values().associateWith { priority ->
        brokersLoadDiff.normalize(brokerLoads.average, BalanceObjective(listOf(priority))) * 100
    }
    return ClusterBalanceStatus(
        brokerIds = brokerLoads.elements.keys.sorted(),
        brokerLoads = brokerLoads.elements,
        brokersAverageLoad = brokerLoads.average,
        brokersLoadDiff = brokersLoadDiff,
        loadDiffPortion = BrokerLoad(
            size = brokersLoadDiff.size percentageOf brokerLoads.average.size,
            rate = brokersLoadDiff.rate percentageOf brokerLoads.average.rate,
            consumeRate = brokersLoadDiff.consumeRate percentageOf brokerLoads.average.consumeRate,
            replicationRate = brokersLoadDiff.replicationRate percentageOf brokerLoads.average.replicationRate,
            replicas = brokersLoadDiff.replicas percentageOf brokerLoads.average.replicas,
            leaders = brokersLoadDiff.leaders percentageOf brokerLoads.average.leaders,
        ),
        combinedLoadDiff = combinedLoadDiff,
        maxLoadBrokers = brokerLoads.loadByBroker { maxBrokersBy(it) },
        minLoadBrokers = brokerLoads.loadByBroker { minBrokersBy(it) },
    )
}

infix fun <T : Number> T?.percentageOfNullable(total: T?): Double? = if (this != null && total != null) percentageOf(total) else null

infix fun <T : Number> T.percentageOf(total: T): Double {
    val number = toDouble()
    val totalNumber = total.toDouble()
    if (number == 0.0 && totalNumber == 0.0) {
        return 0.0
    }
    return 100.0 * number / totalNumber
}

private fun CollectionLoad<BrokerId, BrokerLoad>.loadByBroker(
    aggregation: Map<BrokerId, BrokerLoad>.(selector: (BrokerLoad) -> Double) -> List<BrokerId>
): BrokerByLoad = BrokerByLoad(
    size = elements.aggregation { it.size },
    rate = elements.aggregation { it.rate },
    consumeRate = elements.aggregation { it.consumeRate },
    replicationRate = elements.aggregation { it.replicationRate },
    replicas = elements.aggregation { it.replicas },
    leaders = elements.aggregation { it.leaders },
)

private fun Map<BrokerId, BrokerLoad>.maxBrokersBy(selector: (BrokerLoad) -> Double): List<BrokerId> {
    val maxValue = maxByOrNull { selector(it.value) }?.value?.let(selector) ?: return emptyList()
    return filterValues { selector(it) == maxValue }.keys.toList()
}

private fun Map<BrokerId, BrokerLoad>.minBrokersBy(selector: (BrokerLoad) -> Double): List<BrokerId> {
    val minValue = minByOrNull { selector(it.value) }?.value?.let(selector) ?: return emptyList()
    return filterValues { selector(it) == minValue }.keys.toList()
}

private fun brokerTopicPartitionLoads(
    brokerIds: List<BrokerId>,
    brokersAssignments: Map<BrokerId, BrokerAssignments>,
    topicPartitionsLoad: Map<TopicName, CollectionLoad<Partition, PartitionLoad>>
): Map<BrokerId, Map<TopicPartition, PartitionLoad>> {
    return brokerIds.associateWith { brokerId ->
        brokersAssignments[brokerId]
            ?.topicPartitions
            ?.mapNotNull { topicPartition ->
                topicPartitionsLoad[topicPartition.topic]?.elements?.get(topicPartition.partition)?.let {
                    topicPartition to it
                }
            }
            ?.associate { it }
            ?: emptyMap()
    }
}

fun GlobalContext.migrationSizeBytes(migrations: Migrations): Long {
    return migrations.partitions
        .map { brokerTopicPartitionLoads[it.oldReplicas.first()]?.get(it.topicPartition)?.size ?: 0L }
        .sum()
}

fun GlobalContext.loadOf(topicPartition: TopicPartition): PartitionLoad? {
    return topicPartitionsLoad[topicPartition.topic]?.elements?.get(topicPartition.partition)
}

fun GlobalState.partitionMigrationsOf(
    topic: TopicName, assignmentsChange: AssignmentsChange
): List<TopicPartitionMigration> {
    return assignmentsChange.toPartitionReplicasChanges(topic)
        .groupBy { it.topicPartition }
        .map { (topicPartition, changes) ->
            val partitionLoad = loads[topicPartition.topic]
                ?.partitionLoads?.get(topicPartition.partition)
                ?: PartitionLoad.ZERO
            TopicPartitionMigration(topicPartition,
                fromBrokerIds = changes.filter { it.changeMode == REMOVED_FOLLOWER || it.changeMode == REMOVED_LEADER }.map { it.brokerId },
                toBrokerIds = changes.filter { it.changeMode == ADDED_FOLLOWER || it.changeMode == ADDED_LEADER }.map { it.brokerId },
                oldLeader = assignmentsChange.oldAssignments[topicPartition.partition]?.first()!!,
                newLeader = assignmentsChange.newAssignments[topicPartition.partition]?.first()!!,
                load = partitionLoad
            )
        }
}

fun GlobalContext.computeNewBrokersLoadActual(migrations: Migrations): CollectionLoad<BrokerId, BrokerLoad> {
    val (_, assignmentChanges) = state.applyMigrations(migrations)
    return computeNewBrokersLoad(assignmentChanges)
}

private enum class ChangeMode {
    REMOVED_FOLLOWER,
    REMOVED_LEADER,
    REVOKED_LEADER,
    ADDED_FOLLOWER,
    ADDED_LEADER,
    BECOME_LEADER
}

private data class PartitionReplicaChange(
    val topicPartition: TopicPartition,
    val replicationFactor: Int,
    val brokerId: BrokerId,
    val changeMode: ChangeMode
)

private fun AssignmentsChange.toPartitionReplicasChanges(topic: TopicName): List<PartitionReplicaChange> {
    return sequence {
        val replicationFactor = newAssignments.values.map { it.size }.maxOrNull() ?: 0
        addedPartitionReplicas.forEach { (partition, brokerIds) ->
            val topicPartition = TopicPartition(topic, partition)
            brokerIds.forEach { brokerId ->
                val changeMode = if (newLeaders[partition] == brokerId) ADDED_LEADER else ADDED_FOLLOWER
                yield(PartitionReplicaChange(topicPartition, replicationFactor, brokerId, changeMode))
            }
        }
        removedPartitionReplicas.forEach { (partition, brokerIds) ->
            val topicPartition = TopicPartition(topic, partition)
            brokerIds.forEach { brokerId ->
                val changeMode = if (exLeaders[partition] == brokerId) REMOVED_LEADER else REMOVED_FOLLOWER
                yield(PartitionReplicaChange(topicPartition, replicationFactor, brokerId, changeMode))
            }
        }
        newLeaders.forEach { (partition, brokerId) ->
            val topicPartition = TopicPartition(topic, partition)
            val replicaAdded = addedPartitionReplicas[partition]?.contains(brokerId) ?: false
            if (!replicaAdded) {
                yield(PartitionReplicaChange(topicPartition, replicationFactor, brokerId, BECOME_LEADER))
            }
        }
        exLeaders.forEach { (partition, brokerId) ->
            val topicPartition = TopicPartition(topic, partition)
            val replicaRemoved = removedPartitionReplicas[partition]?.contains(brokerId) ?: false
            if (!replicaRemoved) {
                yield(PartitionReplicaChange(topicPartition, replicationFactor, brokerId, REVOKED_LEADER))
            }
        }
    }.toList()
}

private fun GlobalContext.computeNewBrokersLoad(
    assignmentsChanges: Map<TopicName, AssignmentsChange>
): CollectionLoad<BrokerId, BrokerLoad> {
    val partitionReplicaChanges = assignmentsChanges.flatMap { (topic, assignmentsChange) ->
        assignmentsChange.toPartitionReplicasChanges(topic)
    }
    val mutableBrokerLoads = this.brokersLoad.elements.toMutableMap()
    partitionReplicaChanges.forEach { change ->
        val partitionLoad = loadOf(change.topicPartition) ?: PartitionLoad.ZERO
        val replicationFactor = change.replicationFactor
        val loadChange = partitionLoad.toBrokerLoadChange(change.changeMode, replicationFactor)
        mutableBrokerLoads.merge(change.brokerId, loadChange, BrokerLoad::plus)
    }
    return mutableBrokerLoads.toLoadCollection()
}

private fun PartitionLoad.toBrokerLoadChange(changeMode: ChangeMode, replicationFactor: Int): BrokerLoad {
    return when (changeMode) {
        REMOVED_FOLLOWER -> -toBrokerLoad(false, replicationFactor)
        REMOVED_LEADER -> -toBrokerLoad(true, replicationFactor)
        REVOKED_LEADER -> toBrokerLoad(false, replicationFactor) - toBrokerLoad(true, replicationFactor)
        ADDED_FOLLOWER -> toBrokerLoad(false, replicationFactor)
        ADDED_LEADER -> toBrokerLoad(true, replicationFactor)
        BECOME_LEADER -> toBrokerLoad(true, replicationFactor) - toBrokerLoad(false, replicationFactor)
    }
}

fun GlobalContext.computeNewBrokersLoadEstimate(migrations: Migrations): CollectionLoad<BrokerId, BrokerLoad> {
    var brokerLoads = brokersLoad.elements
    for (migration in migrations.partitions) {
        brokerLoads = computeNewBrokersLoadEstimate(brokerLoads, migration)
    }
    return brokerLoads.toLoadCollection()
}

private fun Map<BrokerId, BrokerLoad>.toLoadCollection() = CollectionLoad(this, values.average())

private fun PartitionReAssignment.changeModes(): Map<BrokerId, ChangeMode> {
    return listOf(oldReplicas, newReplicas).flatten()
        .distinct()
        .mapNotNull { brokerId ->
            val removed = brokerId !in newReplicas
            val added = brokerId !in oldReplicas
            val wasLeader = oldReplicas.indexOf(brokerId) == 0
            val isLeader = newReplicas.indexOf(brokerId) == 0
            brokerId to when {
                added -> if (isLeader) ADDED_LEADER else ADDED_FOLLOWER
                removed -> if (wasLeader) REMOVED_LEADER else REMOVED_FOLLOWER
                wasLeader && !isLeader -> REVOKED_LEADER
                !wasLeader && isLeader -> BECOME_LEADER
                else -> return@mapNotNull null
            }
        }
        .toMap()
}

private fun GlobalContext.computeNewBrokersLoadEstimate(
    brokerLoads: Map<BrokerId, BrokerLoad>,
    migration: PartitionReAssignment
): Map<BrokerId, BrokerLoad> {
    val partitionLoad = loadOf(migration.topicPartition) ?: return brokerLoads
    val replicationFactor = max(migration.oldReplicas.size, migration.newReplicas.size)
    val mutableBrokerLoads = brokerLoads.toMutableMap()
    val brokerChangeModes = migration.changeModes()
    brokerChangeModes.forEach {(brokerId, changeMode) ->
        val loadChange = partitionLoad.toBrokerLoadChange(changeMode, replicationFactor)
        mutableBrokerLoads.merge(brokerId, loadChange, BrokerLoad::plus)
    }
    return mutableBrokerLoads
}
