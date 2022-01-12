package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId

const val INF_RETENTION = -1L

infix fun Long?.plusNullable(other: Long?) = if (this != null && other != null) this + other else null
infix fun Int?.plusNullable(other: Int?) = if (this != null && other != null) this + other else null


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


////////////////////////
// topic usage
////////////////////////

data class DiskUsage(
    val replicasCount: Int,
    val orphanedReplicasCount: Int,
    val actualUsedBytes: Long?,
    val expectedUsageBytes: Long?,
    val retentionBoundedBytes: Long?,
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

operator fun DiskUsage.plus(other: DiskUsage) = DiskUsage(
    replicasCount = replicasCount + other.replicasCount,
    orphanedReplicasCount = orphanedReplicasCount + other.orphanedReplicasCount,
    actualUsedBytes = actualUsedBytes plusNullable other.actualUsedBytes,
    expectedUsageBytes = expectedUsageBytes plusNullable other.expectedUsageBytes,
    retentionBoundedBytes = retentionBoundedBytes plusNullable other.retentionBoundedBytes,
    retentionBoundedBrokerTotalBytes = retentionBoundedBrokerTotalBytes plusNullable other.retentionBoundedBrokerTotalBytes,
    retentionBoundedBrokerPossibleBytes = retentionBoundedBrokerPossibleBytes plusNullable other.retentionBoundedBrokerPossibleBytes,
)

data class TopicDiskUsage(
    val unboundedSizeRetention: Boolean,
    val configuredReplicaRetentionBytes: Long,
    val combined: DiskUsage,
    val combinedPortions: UsagePortions,
    val brokerUsages: Map<BrokerId, DiskUsage>,
    val brokerPortions: Map<BrokerId, UsagePortions>,
    val clusterDiskUsage: ClusterDiskUsage,
)

