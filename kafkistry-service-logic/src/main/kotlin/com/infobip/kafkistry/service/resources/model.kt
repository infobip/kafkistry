package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable

const val INF_RETENTION = -1L

infix fun Long?.plusNullable(other: Long?) = if (this != null && other != null) this + other else null
infix fun Int?.plusNullable(other: Int?) = if (this != null && other != null) this + other else null
infix fun Long?.minusNullable(other: Long?) = if (this != null && other != null) this - other else null
infix fun Int?.minusNullable(other: Int?) = if (this != null && other != null) this - other else null


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
    val errors: List<String>,
)

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

fun BrokerDiskUsage.portionsOf(diskMetric: BrokerDiskMetric?, usageLevelClassifier: UsageLevelClassifier): BrokerDiskPortions {
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

operator fun ClusterDiskUsage.minus(other: ClusterDiskUsage) = ClusterDiskUsage(
    combined = combined - other.combined,
    brokerUsages = (brokerUsages.keys + other.brokerUsages.keys).associateWith { brokerId ->
        val first = brokerUsages[brokerId]
        val second = other.brokerUsages[brokerId]
        when {
            first != null && second != null -> first - second
            first != null -> first
            second != null -> -second
            else -> throw IllegalStateException("can't subtract two nulls")
        }
    },
    errors = errors - other.errors,
)

operator fun BrokerDisk.minus(other: BrokerDisk): BrokerDisk {
    val brokerDiskMetric = BrokerDiskMetric(usage.totalCapacityBytes, usage.freeCapacityBytes)
    val usageDiff = usage - other.usage
    val diffPortions = usageDiff.portionsOf(brokerDiskMetric, UsageLevelClassifier.NONE)
    return BrokerDisk(usageDiff.copy(totalCapacityBytes = null, freeCapacityBytes = null), diffPortions)
}

operator fun BrokerDisk.unaryMinus(): BrokerDisk {
    val brokerDiskMetric = BrokerDiskMetric(usage.totalCapacityBytes, usage.freeCapacityBytes)
    val first = BrokerDiskUsage.ZERO.copy(
        totalCapacityBytes = usage.totalCapacityBytes,
        freeCapacityBytes = usage.freeCapacityBytes,
    )
    val usageDiff = first - usage
    val diffPortions = usageDiff.portionsOf(brokerDiskMetric, UsageLevelClassifier.NONE)
    return BrokerDisk(usageDiff.copy(totalCapacityBytes = null, freeCapacityBytes = null), diffPortions)
}



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


