package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.brokerdisk.NodeDiskMetric
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.OptionalValue
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable
import com.infobip.kafkistry.service.mapValue
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

const val INF_RETENTION = -1L

infix fun Long?.plusNullable(other: Long?) = if (this != null && other != null) this + other else null
infix fun Int?.plusNullable(other: Int?) = if (this != null && other != null) this + other else null
infix fun Long?.minusNullable(other: Long?) = if (this != null && other != null) this - other else null
infix fun Int?.minusNullable(other: Int?) = if (this != null && other != null) this - other else null
infix fun Double?.minusNullable(other: Double?) = if (this != null && other != null) this - other else null


////////////////////////
// resource requirements
////////////////////////

data class TopicResourceRequiredUsages(
    val numBrokers: Int?,
    val messagesPerSec: Double,
    val bytesPerSec: Double,
    val producedBytesPerDay: Long,
    val diskUsagePerPartitionReplica: Long,
    val diskUsagePerBroker: Long?,
    val totalDiskUsageBytes: Long,
    val partitionInBytesPerSec: Double,
    val partitionSyncOutBytesPerSec: Double,
    val brokerProducerInBytesPerSec: Double?,
    val brokerSyncBytesPerSec: Double?,
    val brokerInBytesPerSec: Double?
)


////////////////////////
// usage level
////////////////////////

enum class UsageLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    OVERFLOW,
}

////////////////////////
// cluster usage
////////////////////////

data class ClusterDiskUsage(
    val combined: BrokerDisk,
    val brokerUsages: Map<BrokerId, BrokerDisk>,
    val topicDiskUsages: Map<TopicName, OptionalValue<TopicClusterDiskUsage>>,
    val worstCurrentUsageLevel: UsageLevel,
    val worstPossibleUsageLevel: UsageLevel,
    val errors: List<String>,
) {
    companion object {
        val EMPTY = ClusterDiskUsage(
            combined = BrokerDisk(
                usage = BrokerDiskUsage.ZERO,
                portions = BrokerDiskPortions(
                    usedPercentOfCapacity = null,
                    usageLevel = UsageLevel.NONE,
                    possibleUsedPercentOfCapacity = null,
                    possibleUsageLevel = UsageLevel.NONE,
                    unboundedUsedPercentOfTotalUsed = null,
                )
            ),
            brokerUsages = emptyMap(),
            topicDiskUsages = emptyMap(),
            worstCurrentUsageLevel = UsageLevel.NONE,
            worstPossibleUsageLevel = UsageLevel.NONE,
            errors = emptyList(),
        )
    }
}

data class BrokerDisk(
    val usage: BrokerDiskUsage,
    val portions: BrokerDiskPortions,
)

data class BrokerDiskUsage(
    val replicasCount: Int?,
    val totalUsedBytes: Long?,
    val boundedReplicasCount: Int,
    val boundedSizePossibleUsedBytes: Long,
    val unboundedReplicasCount: Int,
    val unboundedSizeUsedBytes: Long,
    val orphanedReplicasCount: Int,
    val orphanedReplicasSizeUsedBytes: Long,
    val totalCapacityBytes: Long?,
    val freeCapacityBytes: Long?,
) {
    companion object {
        val ZERO = BrokerDiskUsage(
            0, 0L,
            0, 0L,
            0, 0L,
            0, 0L,
            0L, 0L,
        )
    }
}

data class BrokerDiskPortions(
    val usedPercentOfCapacity: Double?,
    val usageLevel: UsageLevel,
    val possibleUsedPercentOfCapacity: Double?,
    val possibleUsageLevel: UsageLevel,
    val unboundedUsedPercentOfTotalUsed: Double?,
)

operator fun BrokerDiskUsage.plus(other: BrokerDiskUsage) = BrokerDiskUsage(
    replicasCount = replicasCount plusNullable other.replicasCount,
    totalUsedBytes = totalUsedBytes plusNullable other.totalUsedBytes,
    boundedReplicasCount = boundedReplicasCount + other.boundedReplicasCount,
    boundedSizePossibleUsedBytes = boundedSizePossibleUsedBytes + other.boundedSizePossibleUsedBytes,
    unboundedReplicasCount = unboundedReplicasCount + other.unboundedReplicasCount,
    unboundedSizeUsedBytes = unboundedSizeUsedBytes + other.unboundedSizeUsedBytes,
    orphanedReplicasCount = orphanedReplicasCount + other.orphanedReplicasCount,
    orphanedReplicasSizeUsedBytes = orphanedReplicasSizeUsedBytes + other.orphanedReplicasSizeUsedBytes,
    totalCapacityBytes = totalCapacityBytes plusNullable other.totalCapacityBytes,
    freeCapacityBytes = freeCapacityBytes plusNullable other.freeCapacityBytes,
)

fun BrokerDiskUsage.portionsOf(diskMetric: NodeDiskMetric?, usageLevelClassifier: UsageLevelClassifier): BrokerDiskPortions {
    val usedPercentOfCapacity = totalUsedBytes percentageOfNullable diskMetric?.total
    val possibleUsedPercentOfCapacity = boundedSizePossibleUsedBytes percentageOfNullable diskMetric?.total
    return BrokerDiskPortions(
        usedPercentOfCapacity = usedPercentOfCapacity,
        usageLevel = usageLevelClassifier.determineLevel(usedPercentOfCapacity),
        possibleUsedPercentOfCapacity = possibleUsedPercentOfCapacity,
        possibleUsageLevel = usageLevelClassifier.determineLevel(possibleUsedPercentOfCapacity),
        unboundedUsedPercentOfTotalUsed = unboundedSizeUsedBytes percentageOfNullable totalUsedBytes,
    )
}

operator fun ClusterDiskUsage.minus(other: ClusterDiskUsage): ClusterDiskUsage {
    val newBrokerUsages = brokerUsages.subtract(other.brokerUsages, BrokerDisk::minus, BrokerDisk::unaryMinus)
    return ClusterDiskUsage(
        combined = combined - other.combined,
        brokerUsages = newBrokerUsages,
        topicDiskUsages = topicDiskUsages.subtractOptionals(other.topicDiskUsages, TopicClusterDiskUsage::minus, TopicClusterDiskUsage::unaryMinus),
        worstCurrentUsageLevel = newBrokerUsages.worstUsageLevel { usageLevel },
        worstPossibleUsageLevel = newBrokerUsages.worstUsageLevel { possibleUsageLevel },
        errors = errors - other.errors.toSet(),
    )
}

operator fun ClusterDiskUsage.unaryMinus(): ClusterDiskUsage {
    return ClusterDiskUsage(
        combined = -combined,
        brokerUsages = brokerUsages.mapValues { -it.value },
        topicDiskUsages = topicDiskUsages.mapValues { (_, ov) -> ov.mapValue { -it } },
        worstCurrentUsageLevel = worstCurrentUsageLevel,
        worstPossibleUsageLevel = worstPossibleUsageLevel,
        errors = errors,
    )
}

fun Map<BrokerId, BrokerDisk>.worstUsageLevel(selector: BrokerDiskPortions.() -> UsageLevel): UsageLevel = values
    .map { it.portions.selector() }
    .maxByOrNull { it.ordinal }
    ?: UsageLevel.NONE

operator fun BrokerDisk.minus(other: BrokerDisk): BrokerDisk {
    val usageDiff = usage - other.usage
    val diffPortions = portions - other.portions
    return BrokerDisk(usageDiff.copy(totalCapacityBytes = null, freeCapacityBytes = null), diffPortions)
}

operator fun BrokerDisk.unaryMinus(): BrokerDisk {
    val brokerDiskMetric = NodeDiskMetric(usage.totalCapacityBytes, usage.freeCapacityBytes)
    val first = BrokerDiskUsage.ZERO.copy(
        totalCapacityBytes = usage.totalCapacityBytes,
        freeCapacityBytes = usage.freeCapacityBytes,
    )
    val usageDiff = first - usage
    val diffPortions = usageDiff.portionsOf(brokerDiskMetric, UsageLevelClassifier.NONE)
    return BrokerDisk(usageDiff.copy(totalCapacityBytes = null, freeCapacityBytes = null), diffPortions)
}

operator fun BrokerDiskPortions.minus(other: BrokerDiskPortions) = BrokerDiskPortions(
    usedPercentOfCapacity = usedPercentOfCapacity minusNullable other.usedPercentOfCapacity,
    usageLevel = UsageLevel.NONE,
    possibleUsedPercentOfCapacity = possibleUsedPercentOfCapacity minusNullable other.possibleUsedPercentOfCapacity,
    possibleUsageLevel = UsageLevel.NONE,
    unboundedUsedPercentOfTotalUsed = unboundedUsedPercentOfTotalUsed minusNullable other.unboundedUsedPercentOfTotalUsed,
)


////////////////////////
// topic usage
////////////////////////

data class TopicDiskUsage(
    val replicasCount: Int,
    val orphanedReplicasCount: Int,
    val actualUsedBytes: Long?,
    val expectedUsageBytes: Long?,
    val retentionBoundedBytes: Long?,
    val existingRetentionBoundedBytes: Long?,
    val unboundedUsageBytes: Long?,
    val orphanedUsedBytes: Long,
)

data class TopicDiskUsageExt(
    val usage: TopicDiskUsage,
    val retentionBoundedBrokerTotalBytes: Long?,
    val retentionBoundedBrokerPossibleBytes: Long?,
)

data class UsagePortions(
    val replicasPercent: Double?,
    val orphanedReplicasPercent: Double,
    val actualUsedBytesPercentOfBrokerTotal: Double?,
    val actualUsedBytesPercentOfBrokerCapacity: Double?,
    val actualUsedBytesPercentOfExpected: Double?,
    val retentionBoundedBytesPercentOfBrokerTotal: Double?,
    val retentionBoundedBytesPercentOfBrokerCapacity: Double?,
    val retentionBoundedBytesPercentOfExpected: Double?,
    val retentionBoundedBrokerTotalBytesPercentOfCapacity: Double?,
    val retentionBoundedBrokerPossibleBytesPercentOfCapacity: Double?,
    val possibleClusterUsageLevel: UsageLevel,
    val totalPossibleClusterUsageLevel: UsageLevel,
)

data class TopicClusterDiskUsage(
    val unboundedSizeRetention: Boolean,
    val configuredReplicaRetentionBytes: Long,
    val combined: TopicDiskUsage,
    val brokerUsages: Map<BrokerId, TopicDiskUsage>,
)

data class TopicClusterDiskUsageExt(
    val unboundedSizeRetention: Boolean,
    val configuredReplicaRetentionBytes: Long,
    val combined: TopicDiskUsageExt,
    val combinedPortions: UsagePortions,
    val brokerUsages: Map<BrokerId, TopicDiskUsageExt>,
    val brokerPortions: Map<BrokerId, UsagePortions>,
    val clusterDiskUsage: ClusterDiskUsage,
)

operator fun TopicDiskUsage.plus(other: TopicDiskUsage) = TopicDiskUsage(
    replicasCount = replicasCount + other.replicasCount,
    orphanedReplicasCount = orphanedReplicasCount + other.orphanedReplicasCount,
    actualUsedBytes = actualUsedBytes plusNullable other.actualUsedBytes,
    expectedUsageBytes = expectedUsageBytes plusNullable other.expectedUsageBytes,
    retentionBoundedBytes = retentionBoundedBytes plusNullable other.retentionBoundedBytes,
    existingRetentionBoundedBytes = existingRetentionBoundedBytes plusNullable other.existingRetentionBoundedBytes,
    orphanedUsedBytes = orphanedUsedBytes + other.orphanedUsedBytes,
    unboundedUsageBytes = unboundedUsageBytes plusNullable other.unboundedUsageBytes,
)

operator fun TopicDiskUsageExt.plus(other: TopicDiskUsageExt) = TopicDiskUsageExt(
    usage = usage + other.usage,
    retentionBoundedBrokerTotalBytes = retentionBoundedBrokerTotalBytes plusNullable other.retentionBoundedBrokerTotalBytes,
    retentionBoundedBrokerPossibleBytes = retentionBoundedBrokerPossibleBytes plusNullable other.retentionBoundedBrokerPossibleBytes,
)

operator fun BrokerDiskUsage.minus(other: BrokerDiskUsage) = BrokerDiskUsage(
    replicasCount = replicasCount minusNullable other.replicasCount,
    totalUsedBytes = totalUsedBytes minusNullable other.totalUsedBytes,
    boundedReplicasCount = boundedReplicasCount - other.boundedReplicasCount,
    boundedSizePossibleUsedBytes = boundedSizePossibleUsedBytes - other.boundedSizePossibleUsedBytes,
    unboundedReplicasCount = unboundedReplicasCount - other.unboundedReplicasCount,
    unboundedSizeUsedBytes = unboundedSizeUsedBytes - other.unboundedSizeUsedBytes,
    orphanedReplicasCount = orphanedReplicasCount - other.orphanedReplicasCount,
    orphanedReplicasSizeUsedBytes = orphanedReplicasSizeUsedBytes - other.orphanedReplicasSizeUsedBytes,
    totalCapacityBytes = totalCapacityBytes minusNullable other.totalCapacityBytes,
    freeCapacityBytes = freeCapacityBytes minusNullable other.freeCapacityBytes,
)

operator fun TopicDiskUsage.minus(other: TopicDiskUsage) = TopicDiskUsage(
    replicasCount = replicasCount - other.replicasCount,
    orphanedReplicasCount = orphanedReplicasCount - other.orphanedReplicasCount,
    actualUsedBytes = actualUsedBytes minusNullable other.actualUsedBytes,
    expectedUsageBytes = expectedUsageBytes minusNullable other.expectedUsageBytes,
    retentionBoundedBytes = retentionBoundedBytes minusNullable other.retentionBoundedBytes,
    existingRetentionBoundedBytes = existingRetentionBoundedBytes minusNullable other.existingRetentionBoundedBytes,
    unboundedUsageBytes = unboundedUsageBytes minusNullable other.unboundedUsageBytes,
    orphanedUsedBytes = orphanedUsedBytes - other.orphanedUsedBytes,
)

operator fun TopicDiskUsage.unaryMinus() = TopicDiskUsage(
    replicasCount = -replicasCount,
    orphanedReplicasCount = -orphanedReplicasCount,
    actualUsedBytes = actualUsedBytes?.let { -it },
    expectedUsageBytes = expectedUsageBytes?.let { -it },
    retentionBoundedBytes = retentionBoundedBytes?.let { -it },
    existingRetentionBoundedBytes = existingRetentionBoundedBytes?.let { -it },
    unboundedUsageBytes = unboundedUsageBytes?.let { -it },
    orphanedUsedBytes = -orphanedUsedBytes,
)

operator fun TopicClusterDiskUsage.minus(other: TopicClusterDiskUsage) = TopicClusterDiskUsage(
    unboundedSizeRetention = !(unboundedSizeRetention xor other.unboundedSizeRetention),
    configuredReplicaRetentionBytes = configuredReplicaRetentionBytes - other.configuredReplicaRetentionBytes,
    combined = combined - other.combined,
    brokerUsages = brokerUsages.subtract(other.brokerUsages, TopicDiskUsage::minus, TopicDiskUsage::unaryMinus),
)

operator fun TopicClusterDiskUsage.unaryMinus() = TopicClusterDiskUsage(
    unboundedSizeRetention = unboundedSizeRetention,
    configuredReplicaRetentionBytes = -configuredReplicaRetentionBytes,
    combined = -combined,
    brokerUsages = brokerUsages.mapValues { -it.value },
)

inline fun <K, V> Map<K, V>.subtract(
    other: Map<K, V>, minus: (V, V) -> V, negative: (V) -> V
): Map<K, V> = (keys + other.keys).associateWith { key ->
    val first = this[key]
    val second = other[key]
    when {
        first != null && second != null -> minus(first, second)
        first != null -> first
        second != null -> negative(second)
        else -> throw IllegalStateException("can't subtract two nulls for '$key'")
    }
}

inline fun <K, V> Map<K, OptionalValue<V>>.subtractOptionals(
    other: Map<K, OptionalValue<V>>, minus: (V, V) -> V, negative: (V) -> V
): Map<K, OptionalValue<V>> = subtract(
    other,
    minus = { first, second ->
        if (first.value != null && second.value != null) {
            OptionalValue.of(minus(first.value, second.value))
        } else {
            OptionalValue.absent(first.absentReason ?: second.absentReason ?: "")
        }
    },
    negative = { it.value?.let { value -> OptionalValue.of(negative(value)) } ?: it }
)


