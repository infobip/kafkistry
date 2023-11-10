package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.ElectionType
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import java.util.*
import java.util.concurrent.CompletableFuture

class TopicAssignmentsOps (
    clientCtx: ClientCtx,
    private val configOps: ConfigOps,
    private val topicOps: TopicOps,
    private val clusterOps: ClusterOps,
): BaseOps(clientCtx) {

    fun reAssignPartitions(
        topicPartitionsAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>, throttleBytesPerSec: Int
    ): CompletableFuture<Unit> {
        val partitionsAssignments = topicPartitionsAssignments
            .flatMap { (topic, partitionAssignments) ->
                partitionAssignments.map { (partition, replicas) ->
                    TopicPartition(topic, partition) to replicas
                }
            }
            .toMap()

        runOperation("verify reassignments used brokers") {
            val brokerIds = partitionsAssignments.values.flatten().toSet()
            val allNodeIds = adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
                .nodes()
                .asCompletableFuture("describe cluster for re-assignments")
                .get().map { it.id() }.toSet()
            brokerIds.filter { it !in allNodeIds }.takeIf { it.isNotEmpty() }?.run {
                throw KafkaClusterManagementException("Unknown broker(s) used in assignments: $this (known nodes: $allNodeIds)")
            }
        }
        val currentPartitionAssignments = adminClient
            .describeTopics(topicPartitionsAssignments.keys, DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe re-assigning topics").get()
            .flatMap { (topic, description) ->
                description.partitions()
                    .map { it.toPartitionAssignments() }
                    .map { partitionsAssignments ->
                        val partitionReplicas = partitionsAssignments.replicasAssignments.map { it.brokerId }
                        TopicPartition(topic, partitionsAssignments.partition) to partitionReplicas
                    }
            }
            .toMap()
        runOperation("verify reassignments partitions") {
            val nonExistentPartitions = partitionsAssignments.keys.filter { it !in currentPartitionAssignments.keys }
            if (nonExistentPartitions.isNotEmpty()) {
                throw KafkaClusterManagementException("Trying to reassign non-existent topic partitions: $nonExistentPartitions")
            }
        }
        fun setupThrottle(currentReassignments: Map<TopicPartition, PartitionReassignment>) = runOperation("setup re-assignment throttle") {
            val throttleRate = ThrottleRate(throttleBytesPerSec.toLong(), throttleBytesPerSec.toLong())
            val topicsMoveMap = ReAssignmentSupport.calculateProposedMoveMap(
                currentReassignments, partitionsAssignments, currentPartitionAssignments
            )
            val leaderThrottlesMap = ReAssignmentSupport.calculateLeaderThrottles(topicsMoveMap)
            val followerThrottlesMap = ReAssignmentSupport.calculateFollowerThrottles(topicsMoveMap)
            val reassigningBrokersSet = ReAssignmentSupport.calculateReassigningBrokers(topicsMoveMap)

            val topicNames = leaderThrottlesMap.keys + followerThrottlesMap.keys
            topicNames.map { topic ->
                val topicThrottleConfig = mapOf(
                    "leader.replication.throttled.replicas" to leaderThrottlesMap[topic],
                    "follower.replication.throttled.replicas" to followerThrottlesMap[topic],
                ).filterValues { it != null }
                configOps.partialUpdateTopicConfig(topic, topicThrottleConfig)
            }.forEach { it.get() }
            reassigningBrokersSet.map { configOps.updateThrottleRate(it, throttleRate) }.forEach { it.get() }
        }
        return if (clusterVersion < VERSION_2_4) {
            runOperation("execute re-assignment via ZK") {
                if (throttleBytesPerSec >= 0) {
                    setupThrottle(currentReassignments = emptyMap())
                }
                val newAssignmentsScala = partitionsAssignments
                    .mapValues { it.value.toScalaList().toSeq() }
                    .toScalaMap()
                zkClient.createPartitionReassignment(newAssignmentsScala.cast())
            }
            CompletableFuture.completedFuture(Unit)
        } else {
            val reassignments = partitionsAssignments.mapValues {
                Optional.of(NewPartitionReassignment(it.value))
            }
            if (throttleBytesPerSec >= 0) {
                val currentReassignments = adminClient.listPartitionReassignments().reassignments().get()
                setupThrottle(currentReassignments)
            }
            adminClient
                .alterPartitionReassignments(reassignments, AlterPartitionReassignmentsOptions().withWriteTimeout())
                .all()
                .asCompletableFuture("alter partition reassignments")
                .thenApply { }
        }
    }

    fun listReAssignments(): CompletableFuture<List<TopicPartitionReAssignment>> {
        if (clusterVersion < VERSION_2_4) {
            val topicNames = topicOps.listAllTopicNames().get()
            val result = runOperation("get current re-assignments in ZK") {
                val targetAssignments = zkClient.partitionReassignment
                    .toJavaMap()
                    .mapValues { it.value.toJavaList().cast<List<BrokerId>>() }
                val assignmentForTopics = zkClient.getFullReplicaAssignmentForTopics(
                    topicNames.toScalaList().toSet()
                )
                val partitionStates = zkClient.getTopicPartitionStates(assignmentForTopics.keys().toSeq())
                assignmentForTopics.toJavaMap()
                    .mapNotNull { (topicPartition, reAssignment) ->
                        val allReplicas = reAssignment.replicas().toJavaList().cast<List<BrokerId>>()
                        val isr = partitionStates.get(topicPartition)
                            .map { it.leaderAndIsr().isr().toJavaList().cast<List<BrokerId>>() }
                            .getOrElse { emptyList<BrokerId>() }
                        if (allReplicas.toSet() == isr.toSet()) {
                            return@mapNotNull null
                        }
                        val targets = targetAssignments[topicPartition] ?: emptyList()
                        TopicPartitionReAssignment(
                            topic = topicPartition.topic(),
                            partition = topicPartition.partition(),
                            addingReplicas = targets - isr,
                            removingReplicas = allReplicas - targets,
                            allReplicas = allReplicas
                        )
                    }
            }
            return CompletableFuture.completedFuture(result)
        }
        return adminClient.listPartitionReassignments(ListPartitionReassignmentsOptions().withReadTimeout())
            .reassignments()
            .asCompletableFuture("list partition reassignments")
            .thenApply { partitionReAssignments ->
                partitionReAssignments.map { (topicPartition, reAssignment) ->
                    TopicPartitionReAssignment(
                        topic = topicPartition.topic(),
                        partition = topicPartition.partition(),
                        addingReplicas = reAssignment.addingReplicas(),
                        removingReplicas = reAssignment.removingReplicas(),
                        allReplicas = reAssignment.replicas()
                    )
                }
            }
    }

    fun verifyReAssignPartitions(topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>): String {
        val currentReAssignments = listReAssignments().get()
            .groupBy { it.topic }
            .mapValues { (_, partitionReAssignments) ->
                partitionReAssignments.associateBy { it.partition }
            }
        val currentTopicDescription = adminClient
            .describeTopics(listOf(topicName), DescribeTopicsOptions().withReadTimeout())
            .allTopicNames()
            .asCompletableFuture("describe topics for reassignment verification")
            .get().getOrElse(topicName) {
                throw KafkaClusterManagementException("Failed to get current topic description for topic: '$topicName'")
            }
        val currentAssignments = currentTopicDescription.partitions()
            .map { it.toPartitionAssignments() }
            .toPartitionReplicasMap()
        val partitionStatuses = partitionsAssignments.mapValues { (partition, replicas) ->
            val currentReplicas = currentAssignments[partition] ?: emptyList()
            val reAssignment = currentReAssignments[topicName]?.get(partition)
            when {
                reAssignment != null || currentReplicas.size > replicas.size -> ReAssignmentStatus.IN_PROGRESS
                currentReplicas == replicas -> ReAssignmentStatus.COMPLETED
                else -> ReAssignmentStatus.FAILED
            }
        }
        val resultMsg = StringBuilder()
        partitionStatuses.forEach { (partition, status) ->
            val topicPartition = TopicPartition(topicName, partition)
            when (status) {
                ReAssignmentStatus.COMPLETED -> resultMsg.append("Reassignment of partition $topicPartition completed successfully\n")
                ReAssignmentStatus.FAILED -> resultMsg.append("Reassignment of partition $topicPartition failed\n")
                ReAssignmentStatus.IN_PROGRESS -> resultMsg.append("Reassignment of partition $topicPartition is still in progress\n")
            }
        }
        if (partitionStatuses.values.all { it == ReAssignmentStatus.COMPLETED }) {
            val topicConfig = adminClient.describeConfigs(
                listOf(ConfigResource(ConfigResource.Type.TOPIC, topicName)),
                DescribeConfigsOptions().withWriteTimeout()
            )
                .all().asCompletableFuture("describe topic configs for reassignment verification")
                .get().getOrElse(ConfigResource(ConfigResource.Type.TOPIC, topicName)) {
                    throw KafkaClusterManagementException("Failed to get current topic description for topic: '$topicName'")
                }
                .entries().associate { it.name() to it.toTopicConfigValue() }
                .filterValues { !it.default }
                .mapValues { it.value.value }
                .plus(
                    mapOf(
                        "leader.replication.throttled.replicas" to null,
                        "follower.replication.throttled.replicas" to null,
                    )
                )
            configOps.updateTopicConfig(topicName, topicConfig).get()
            resultMsg.append("Topic: Throttle was removed.\n")
        }
        if (currentReAssignments.isEmpty()) {
            clusterOps.clusterInfo("").get().nodeIds
                .map { configOps.updateThrottleRate(it, ThrottleRate.NO_THROTTLE) }
                .forEach { it.get() }
            resultMsg.append("Brokers: Throttle was removed.\n")
        } else {
            resultMsg.append("Brokers: Keeping throttle because of topics re-assigning ${currentReAssignments.keys}.\n")
        }
        return resultMsg.toString()
    }

    fun cancelReAssignments(topicName: TopicName, partitions: List<Partition>): CompletableFuture<Unit> {
        if (clusterVersion < VERSION_2_4) {
            throw KafkaClusterManagementException("Unsupported operation for cluster version < $VERSION_2_4, current: $clusterVersion")
        }
        val topicPartitions = partitions
            .associate { TopicPartition(topicName, it) to Optional.empty<NewPartitionReassignment>() }
        return adminClient
            .alterPartitionReassignments(topicPartitions, AlterPartitionReassignmentsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("cancel partition reassignments")
            .thenApply { }
    }

    fun runPreferredReplicaElection(topicName: TopicName, partitions: List<Partition>) {
        val topicPartitions = partitions.map { TopicPartition(topicName, it) }.toSet()
        when {
            clusterVersion < VERSION_2_2 -> runOperation("preferred replica election") {
                zkClient.createPreferredReplicaElection(topicPartitions.toScalaList().toSet())
            }
            else -> {
                adminClient
                    .electLeaders(
                        ElectionType.PREFERRED, topicPartitions, ElectLeadersOptions().withWriteTimeout()
                    )
                    .all().asCompletableFuture("run preferred leader election")
                    .get()
            }
        }
    }

    private enum class ReAssignmentStatus {
        COMPLETED, IN_PROGRESS, FAILED
    }


}