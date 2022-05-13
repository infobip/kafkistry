package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.PartitionLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos

fun TopicDescription.propertiesForCluster(cluster: ClusterRef): TopicProperties =
    perClusterProperties[cluster.identifier]
        ?: cluster.tags.mapNotNull { perTagProperties[it] }.firstOrNull()
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
    allBrokers: List<BrokerId>
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
        val brokerReplicasStatuses = allBrokers.sorted().map { brokerId ->
            val currentStatus = existingAssignments[partition]?.get(brokerId)
            if (assignmentsChange != null) {
                BrokerReplicaAssignmentStatus(
                    brokerId = brokerId,
                    currentStatus = currentStatus,
                    added = brokerId in assignmentsChange.addedPartitionReplicas[partition].orEmpty(),
                    removed = brokerId in assignmentsChange.removedPartitionReplicas[partition].orEmpty(),
                    newLeader = brokerId == assignmentsChange.newLeaders[partition],
                    exLeader = brokerId == assignmentsChange.exLeaders[partition],
                    rank = assignmentsChange.newAssignments[partition]
                        ?.indexOf(brokerId)
                        ?.takeIf { it >= 0 }
                        ?: currentStatus?.rank
                        ?: -1
                )
            } else {
                BrokerReplicaAssignmentStatus(
                    brokerId = brokerId,
                    currentStatus = currentStatus,
                    added = false, removed = false, newLeader = false, exLeader = false,
                    rank = currentStatus?.rank ?: -1
                )
            }
        }
        val numAddedAssignments = brokerReplicasStatuses.filter { it.added }.size
        val numRemovedAssignments = brokerReplicasStatuses.filter { it.removed }.size
        val numExLeaders = brokerReplicasStatuses.filter { it.exLeader }.size
        PartitionAssignmentsStatus(
            partition = partition,
            brokerReplicas = brokerReplicasStatuses,
            newReplicasCount = numAddedAssignments - numRemovedAssignments,
            movedReplicasCount = numRemovedAssignments,
            reElectedLeadersCount = numExLeaders
        )
    }
    return PartitionsAssignmentsStatus(
        partitions = partitions,
        newReplicasCount = partitions.sumOf { it.newReplicasCount },
        movedReplicasCount = partitions.sumOf { it.movedReplicasCount },
        reElectedLeadersCount = partitions.sumOf { it.reElectedLeadersCount }
    )
}

fun KafkaExistingTopic.toTopicInfo(
    clusterBrokerIds: List<BrokerId>,
    topicReplicaInfos: TopicReplicaInfos?,
    partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
    partitionsReplicasAssignor: PartitionsReplicasAssignor
): ExistingTopicInfo = ExistingTopicInfo(
    name = name,
    properties = TopicProperties(
        partitionCount = partitionsAssignments.size,
        replicationFactor = partitionsAssignments.map {
            it.resolveReplicationFactor(partitionReAssignments)
        }.maxOrNull() ?: 0  //max in case there are currently different number of replicas per partition
    ),
    config = config,
    partitionsAssignments = partitionsAssignments,
    assignmentsDisbalance = partitionsReplicasAssignor.assignmentsDisbalance(
        existingAssignments = currentAssignments(),
        allBrokers = clusterBrokerIds,
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
    clusterIdentifier: KafkaClusterIdentifier, key: String, value: String
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