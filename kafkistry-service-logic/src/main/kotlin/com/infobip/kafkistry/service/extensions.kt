package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.AclInspectionResultType.*
import com.infobip.kafkistry.service.generator.AssignmentsChange
import com.infobip.kafkistry.service.generator.PartitionLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.validation.rules.Placeholder
import com.infobip.kafkistry.service.topic.validation.rules.RuleViolation

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

fun <T, R : Any> Iterable<T>.mostFrequentElement(extractor: (T) -> R?): R? =
        this.mapNotNull(extractor)
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

fun List<ClusterRef>.clustersTags(): Map<Set<KafkaClusterIdentifier>, List<Tag>> {
    val allTags = flatMap { it.tags }.distinct()
    return allTags.groupBy { tag ->
        filter { tag in it.tags }.map { it.identifier }.toSet()
    }
}

fun List<ClusterRef>.computePresence(
    presentOnClusters: List<KafkaClusterIdentifier>,
    disabledClusters: List<KafkaClusterIdentifier> = emptyList()
): Presence {
    val presentAndDisabledSet = (presentOnClusters + disabledClusters).toSet()
    val includedClusters = presentAndDisabledSet.toList()
    val allClusterIdentifiers = map { it.identifier }

    if (includedClusters.containsAll(allClusterIdentifiers)) {
        return Presence.ALL
    }

    val allTags = flatMap { it.tags }.distinct()
    val tagClusters = allTags.associateWith { tag ->
        filter { tag in it.tags }.map { it.identifier }.toSet()
    }
    val presentSet = presentOnClusters.toSet()
    tagClusters.filterValues { it == presentSet }.keys.firstOrNull()?.run {
        return Presence(PresenceType.TAGGED_CLUSTERS, tag = this)
    }
    tagClusters.filterValues { it == presentAndDisabledSet }.keys.firstOrNull()?.run {
        return Presence(PresenceType.TAGGED_CLUSTERS, tag = this)
    }

    return if (includedClusters.size <= allClusterIdentifiers.size / 2) {
        Presence(PresenceType.INCLUDED_CLUSTERS, includedClusters)
    } else {
        Presence(PresenceType.EXCLUDED_CLUSTERS, allClusterIdentifiers.filter { it !in includedClusters })
    }
}

fun Presence.withClusterIncluded(clusterRef: ClusterRef): Presence {
    return if (needToBeOnCluster(clusterRef)) {
        return this
    } else {
        when (type) {
            PresenceType.ALL_CLUSTERS -> this
            PresenceType.INCLUDED_CLUSTERS -> copy(kafkaClusterIdentifiers = kafkaClusterIdentifiers?.plus(clusterRef.identifier))
            PresenceType.EXCLUDED_CLUSTERS -> copy(kafkaClusterIdentifiers = kafkaClusterIdentifiers?.minus(clusterRef.identifier))
            PresenceType.TAGGED_CLUSTERS -> throw KafkistryIllegalStateException(
                "Can't suggest fix of configuration by changing topic's config, topic presence is $this, " +
                        "cluster '${clusterRef.identifier}' should be tagged with '$tag', currently it's only tagged with ${clusterRef.tags}"
            )
        }
    }
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

fun AclRule.toKafkaAclRule(principal: PrincipalId) = KafkaAclRule(
        principal = principal,
        host = host,
        resource = resource,
        operation = operation
)

fun KafkaAclRule.toAclRule(presence: Presence) = AclRule(
        presence = presence,
        host = host,
        resource = resource,
        operation = operation
)

fun PrincipalAclsInspection.transpose(): PrincipalAclsClustersPerRuleInspection {
    val ruleStatuses = clusterInspections
            .flatMap { clusterStatuses -> clusterStatuses.statuses.map { clusterStatuses.clusterIdentifier to it } }
            .groupBy { (_, ruleStatus) -> ruleStatus.rule }
            .map { (rule, statuses) ->
                AclRuleClustersInspection(
                        aclRule = rule,
                        clusterStatuses = statuses.associate { it },
                        status = AclStatus.from(statuses.map { (_, ruleStatus) -> ruleStatus }),
                        availableOperations = statuses.mergeAvailableOps { it.second.availableOperations }
                )
            }
    val clusterAffectingQuotaEntities = clusterInspections.associate {
        it.clusterIdentifier to it.affectingQuotaEntities
    }
    return PrincipalAclsClustersPerRuleInspection(
            principal = principal,
            principalAcls = principalAcls,
            statuses = ruleStatuses,
            status = ruleStatuses.map { it.status }.aggregate(),
            availableOperations = availableOperations,
            clusterAffectingQuotaEntities = clusterAffectingQuotaEntities,
            affectingQuotaEntities = affectingQuotaEntities,
    )
}

fun <T> Iterable<T>.mergeAvailableOps(extractor: (T) -> List<AvailableAclOperation>): List<AvailableAclOperation> =
        flatMap(extractor).distinct().sorted()

fun AclInspectionResultType.availableOperations(
        principalExists: Boolean
): List<AvailableAclOperation> =
        when (this) {
            OK, NOT_PRESENT_AS_EXPECTED, CLUSTER_UNREACHABLE, SECURITY_DISABLED, CLUSTER_DISABLED, UNAVAILABLE -> emptyList()
            MISSING -> listOf(AvailableAclOperation.CREATE_MISSING_ACLS, AvailableAclOperation.EDIT_PRINCIPAL_ACLS)
            UNEXPECTED -> listOf(AvailableAclOperation.DELETE_UNWANTED_ACLS, AvailableAclOperation.EDIT_PRINCIPAL_ACLS)
            UNKNOWN -> listOf(
                    AvailableAclOperation.DELETE_UNWANTED_ACLS,
                    if (principalExists) {
                        AvailableAclOperation.EDIT_PRINCIPAL_ACLS
                    } else {
                        AvailableAclOperation.IMPORT_PRINCIPAL
                    }
            )
        }


private val RULE_VIOLATION_REGEX = Regex("%(\\w+)%")

private fun String.renderMessage(placeholders: Map<String, Placeholder>): String {
    return RULE_VIOLATION_REGEX.replace(this) { match ->
        val key = match.groupValues[1]
        placeholders[key]?.value.toString()
    }
}

fun RuleViolation.renderMessage(): String = message.renderMessage(placeholders)
fun RuleViolationIssue.renderMessage(): String = message.renderMessage(placeholders)

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

fun <K> Iterable<Map<K, Long>>.sumPerValue(): Map<K, Long> = reducePerValue { v1, v2 -> v1 + v2}

fun <K, V : Any> Iterable<Map<K, V>>.reducePerValue(reduce: (V, V) -> V): Map<K, V> {
    val result = mutableMapOf<K, V>()
    forEach { map ->
        map.forEach { (k, v) ->
            result.merge(k, v) { v1, v2 -> reduce(v1, v2) }
        }
    }
    return result
}