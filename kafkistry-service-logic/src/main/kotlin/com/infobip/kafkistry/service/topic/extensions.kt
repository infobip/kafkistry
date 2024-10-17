package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.TopicReplicaInfos
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.Broker
import com.infobip.kafkistry.service.generator.PartitionLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.sumPerValue

fun TopicDescription.propertiesForCluster(cluster: ClusterRef): TopicProperties =
    perClusterProperties[cluster.identifier]
        ?: cluster.tags.firstNotNullOfOrNull { perTagProperties[it] }
        ?: properties

fun TopicDescription.configForCluster(cluster: ClusterRef): TopicConfigMap {
    val tagsConfig = cluster.tags
        .mapNotNull { perTagConfigOverrides[it] }
        .foldRight(emptyMap<String, String?>()) { acc, conf -> acc + conf }
    val clusterConfig = perClusterConfigOverrides[cluster.identifier].orEmpty()
    return config + tagsConfig + clusterConfig
}

fun TopicDescription.toExpectedInfoForCluster(cluster: ClusterRef): ExpectedTopicInfo = ExpectedTopicInfo(
    name = name,
    properties = propertiesForCluster(cluster),
    config = configForCluster(cluster),
    resourceRequirements = resourceRequirements
)

/**
 * @return a map of partition: (Int) to list of broker ids: (Int-s) of replicas for that partition
 */
fun KafkaExistingTopic.currentAssignments(): Map<Partition, List<BrokerId>> {
    return partitionsAssignments.associate {
        it.partition to it.replicasAssignments.map { replica -> replica.brokerId }
    }
}

fun Map<Partition, List<BrokerId>>.partitionLoads(topicReplicaInfos: TopicReplicaInfos?): Map<Partition, PartitionLoad> {
    return this.mapValues { (partition, brokers) ->
        val leader = brokers.firstOrNull() ?: return@mapValues PartitionLoad(0L)
        val leaderSizeBytes = topicReplicaInfos
                ?.partitionBrokerReplicas
                ?.get(partition)
                ?.get(leader)
                ?.sizeBytes
                ?: return@mapValues PartitionLoad(0L)
        PartitionLoad(leaderSizeBytes)
    }
}

fun List<PartitionAssignments>.toAssignmentsInfo(
    assignmentsChange: AssignmentsChange?,
    allBrokers: List<Broker>,
): PartitionsAssignmentsStatus {
    val existingAssignments = associate {
        it.partition to it.replicasAssignments.associateBy { replica -> replica.brokerId }
    }
    val allPartitions = if (assignmentsChange != null) {
        assignmentsChange.oldAssignments.keys + assignmentsChange.newAssignments.keys
    } else {
        existingAssignments.keys
    }.sorted()
    val partitions = allPartitions.map { partition ->
        val brokerReplicasStatuses = allBrokers.sortedBy { it.id }.map { broker ->
            val currentStatus = existingAssignments[partition]?.get(broker.id)
            if (assignmentsChange != null) {
                BrokerReplicaAssignmentStatus(
                    brokerId = broker.id,
                    currentStatus = currentStatus,
                    added = broker.id in assignmentsChange.addedPartitionReplicas[partition].orEmpty(),
                    removed = broker.id in assignmentsChange.removedPartitionReplicas[partition].orEmpty(),
                    newLeader = broker.id == assignmentsChange.newLeaders[partition],
                    exLeader = broker.id == assignmentsChange.exLeaders[partition],
                    rank = assignmentsChange.newAssignments[partition]
                        ?.indexOf(broker.id)
                        ?.takeIf { it >= 0 }
                        ?: currentStatus?.rank
                        ?: -1,
                    rack = broker.rack
                )
            } else {
                BrokerReplicaAssignmentStatus(
                    brokerId = broker.id,
                    currentStatus = currentStatus,
                    added = false, removed = false, newLeader = false, exLeader = false,
                    rank = currentStatus?.rank ?: -1,
                    rack = broker.rack,
                )
            }
        }
        val numAddedAssignments = brokerReplicasStatuses.count { it.added }
        val numRemovedAssignments = brokerReplicasStatuses.count { it.removed }
        val numExLeaders = brokerReplicasStatuses.count { it.exLeader }
        val rackCounts = brokerReplicasStatuses.asSequence()
            .filter { it.rank > -1 }    //take only involved
            .groupBy { it.rack }
            .mapValues { (rack, r) ->
                val added = r.count { it.added }
                val removed = r.count { it.removed }
                PartitionBrokerRackCount(
                    rack = rack,
                    oldCount = r.size - added,
                    newCount = r.size - removed,
                )
            }
            .values.toList()
        val userRacks = brokerReplicasStatuses.asSequence()
            .filter { it.rank > -1 && !it.removed }    //take only current/after state
            .map { it.rack }
            .toSet()
        PartitionAssignmentsStatus(
            partition = partition,
            brokerReplicas = brokerReplicasStatuses,
            newReplicasCount = numAddedAssignments - numRemovedAssignments,
            movedReplicasCount = numRemovedAssignments,
            reElectedLeadersCount = numExLeaders,
            rackCounts = rackCounts,
            singleRackReplicas = userRacks.size == 1,
        )
    }
    return PartitionsAssignmentsStatus(
        partitions = partitions,
        newReplicasCount = partitions.sumOf { it.newReplicasCount },
        movedReplicasCount = partitions.sumOf { it.movedReplicasCount },
        reElectedLeadersCount = partitions.sumOf { it.reElectedLeadersCount },
        clusterHasRacks = allBrokers.distinctBy { it.rack }.size > 1,
    )
}

fun KafkaExistingTopic.toTopicInfo(
    clusterBrokers: List<Broker>,
    topicReplicaInfos: TopicReplicaInfos?,
    partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
    partitionsReplicasAssignor: PartitionsReplicasAssignor
): ExistingTopicInfo = ExistingTopicInfo(
    uuid = uuid,
    name = name,
    properties = TopicProperties(
        partitionCount = partitionsAssignments.size,
        replicationFactor = partitionsAssignments.maxOfOrNull {
            it.resolveReplicationFactor(partitionReAssignments)
        } ?: 0  //max in case there are currently different number of replicas per partition
    ),
    config = config,
    partitionsAssignments = partitionsAssignments,
    assignmentsDisbalance = partitionsReplicasAssignor.assignmentsDisbalance(
        existingAssignments = currentAssignments(),
        allBrokers = clusterBrokers,
        existingPartitionLoads = currentAssignments().partitionLoads(topicReplicaInfos)
    )
)

fun PartitionAssignments.resolveReplicationFactor(partitionReAssignments: Map<Partition, TopicPartitionReAssignment>): Int {
    return partitionReAssignments[partition]
        ?.let { reAssignment ->
            reAssignment.allReplicas.size - reAssignment.removingReplicas.size
        }
        ?: replicasAssignments.size
}

fun PartitionAssignments.needsReElection() = replicasAssignments.any { it.leader xor it.preferredLeader }
fun List<PartitionAssignments>.partitionsToReElectLeader(): List<Partition> = filter { it.needsReElection() }.map { it.partition }
fun TopicDescription.withClusterProperty(
    clusterIdentifier: KafkaClusterIdentifier, key: TopicConfigKey, value: TopicConfigValue
): TopicDescription {
    val entry = Pair(key, value)
    val clusterConfigOverrides = perClusterConfigOverrides[clusterIdentifier] ?: emptyMap()
    return copy(
            perClusterConfigOverrides = perClusterConfigOverrides + Pair(
                    clusterIdentifier, clusterConfigOverrides + entry
            )
    )
}

fun TopicDescription.withClusterProperties(
    clusterIdentifier: KafkaClusterIdentifier, topicProperties: TopicProperties
): TopicDescription = copy(
        perClusterProperties = perClusterProperties + Pair(clusterIdentifier, topicProperties)
)

fun Iterable<DataMigration>.merge() = DataMigration(
    reAssignedPartitions = sumOf { it.reAssignedPartitions },
    totalIOBytes = sumOf { it.totalIOBytes },
    totalAddBytes = sumOf { it.totalAddBytes },
    totalReleaseBytes = sumOf { it.totalReleaseBytes },
    perBrokerTotalIOBytes = map { it.perBrokerTotalIOBytes }.sumPerValue(),
    perBrokerInputBytes = map { it.perBrokerInputBytes }.sumPerValue(),
    perBrokerOutputBytes = map { it.perBrokerOutputBytes }.sumPerValue(),
    perBrokerReleasedBytes = map { it.perBrokerReleasedBytes }.sumPerValue(),
    maxBrokerIOBytes = 0L
).run {
    copy(maxBrokerIOBytes = (perBrokerInputBytes.values + perBrokerOutputBytes.values).maxOrNull() ?: 0L)
}

fun ClusterInfo.assignableBrokers() = brokers().map { Broker(id = it.nodeId, rack = it.rack) }
