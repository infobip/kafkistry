package com.infobip.kafkistry.service.generator.balance

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.DataMigration
import com.infobip.kafkistry.service.generator.AssignmentsChange

/////////////////////////////
// Load Facts
/////////////////////////////

data class PartitionLoad(
        val size: Long,
        val rate: Double,
        val consumers: Int
) {
    companion object {
        val ZERO = PartitionLoad(0, 0.0, 0)
    }
}

data class TopicLoad(
        val topic: TopicName,
        val partitionLoads: List<PartitionLoad>
)

data class BrokerLoad(
    val size: Double,
    val rate: Double,
    val consumeRate: Double,
    val replicationRate: Double,
    val replicas: Double,
    val leaders: Double
) {
    companion object {
        val ZERO = BrokerLoad(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}


/////////////////////////////
// Assignment Facts
/////////////////////////////

data class TopicAssignments(
        val topic: TopicName,
        val partitionAssignments: Map<Partition, List<BrokerId>>
)

//ref link class
data class TopicPartition(
        val topic: TopicName,
        val partition: Partition
)


//ref link class
data class TopicPartitionAssignment(
        val topic: TopicName,
        val partition: Partition,
        val brokerId: BrokerId
)


/////////////////////////////
// Whole system
/////////////////////////////

data class GlobalState(
        val brokerIds: List<BrokerId>,
        val loads: Map<TopicName, TopicLoad>,
        val assignments: Map<TopicName, TopicAssignments>
)


/////////////////////////////
// Algorithm parameter
/////////////////////////////

enum class BalancePriority {
    SIZE,
    RATE,
    CONSUMERS_RATE,
    REPLICATION_RATE,
    REPLICAS_COUNT,
    LEADERS_COUNT
}

data class BalanceObjective(
    val priorities: List<BalancePriority>
) {
    companion object {
        val EQUAL = BalanceObjective(emptyList())
        fun of(vararg priorities: BalancePriority) = BalanceObjective(priorities.distinct())
    }
}

data class BalanceSettings(
        val objective: BalanceObjective,
        val maxIterations: Int,
        val maxMigrationBytes: Long,
        val timeLimitIterationMs: Long,
        val timeLimitTotalMs: Long,
)


/////////////////////////////
// Cluster Balance Status
/////////////////////////////

data class ClusterBalanceStatus(
        val brokerIds: List<BrokerId>,
        val brokerLoads: Map<BrokerId, BrokerLoad>,
        val brokersAverageLoad: BrokerLoad,
        val brokersTotalLoad: BrokerLoad,
        val brokersLoadDiff: BrokerLoad,
        val loadDiffPortion: BrokerLoad,
        val combinedLoadDiff: Map<BalancePriority, Double>,
        val maxLoadBrokers: BrokerByLoad,
        val minLoadBrokers: BrokerByLoad,
)

data class BrokerByLoad(
    val size: List<BrokerId>,
    val rate: List<BrokerId>,
    val consumeRate: List<BrokerId>,
    val replicationRate: List<BrokerId>,
    val replicas: List<BrokerId>,
    val leaders: List<BrokerId>,
)


/////////////////////////////
// Re-Balance Output
/////////////////////////////

data class PartitionReAssignment(
    val topicPartition: TopicPartition,
    val oldReplicas: List<BrokerId>,
    val newReplicas: List<BrokerId>
)

data class Migrations(
        val partitions: List<PartitionReAssignment>
) {
    constructor(vararg partitions: PartitionReAssignment): this(listOf(*partitions))
}

data class TopicPartitionMigration(
        val topicPartition: TopicPartition,
        val fromBrokerIds: List<BrokerId>,
        val toBrokerIds: List<BrokerId>,
        val oldLeader: BrokerId,
        val newLeader: BrokerId,
        val load: PartitionLoad,
)

data class ProposedMigrations(
    val clusterBalanceBefore: ClusterBalanceStatus,
    val clusterBalanceAfter: ClusterBalanceStatus,
    val iterationMigrations: List<List<TopicPartitionMigration>>,
    val migrations: List<TopicPartitionMigration>,
    val dataMigration: DataMigration,
    val topicsAssignmentChanges: Map<TopicName, AssignmentsChange>,
)



