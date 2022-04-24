package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicConfigMap
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.TopicManagementService
import org.springframework.web.bind.annotation.*

/**
 * Management operations on actual kafka clusters
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/management")
class ManagementApi(
    private val managementService: TopicManagementService
) {

    @DeleteMapping("/delete-topic")
    fun deleteTopicOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): Unit = managementService.applyUnexpectedOrUnknownTopicDeletion(topicName, clusterIdentifier)

    //delete topic even if it's configured to be present on given clusterIdentifier
    @DeleteMapping("/force-delete-topic")
    fun forceDeleteTopicOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): Unit = managementService.forceDeleteTopic(topicName, clusterIdentifier)

    @PostMapping("/create-missing-topic")
    fun createMissingTopicOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): Unit = managementService.applyMissingTopicCreation(topicName, clusterIdentifier)

    @PostMapping("/update-topic-to-config")
    fun updateTopicOnClusterToExpectedConfig(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody expectedTopicConfig: TopicConfigMap,
    ): Unit = managementService.applyWrongTopicConfigUpdate(topicName, clusterIdentifier, expectedTopicConfig)

    @PostMapping("/add-topic-partitions")
    fun addTopicPartitionsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody addedPartitionsAssignments: Map<Partition, List<BrokerId>>,
    ): Unit = managementService.applyAddTopicPartitions(topicName, clusterIdentifier, addedPartitionsAssignments)

    @PostMapping("/add-topic-partitions-replicas")
    fun assignTopicPartitionReplicasOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("throttleBytesPerSec") throttleBytesPerSec: Int,
        @RequestBody newPartitionsAssignments: Map<Partition, List<BrokerId>>,
    ): Unit = managementService.applyIncreaseReplicationFactor(
        topicName, clusterIdentifier, newPartitionsAssignments, throttleBytesPerSec
    )

    @PostMapping("/remove-topic-partitions-replicas")
    fun removeTopicPartitionReplicasOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody newPartitionsAssignments: Map<Partition, List<BrokerId>>,
    ): Unit = managementService.applyReduceReplicationFactor(topicName, clusterIdentifier, newPartitionsAssignments)

    @PostMapping("/apply-topic-partitions-reassignment")
    fun applyNewPartitionReplicasAssignments(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("throttleBytesPerSec") throttleBytesPerSec: Int,
        @RequestBody newPartitionsAssignments: Map<Partition, List<BrokerId>>,
    ): Unit = managementService.applyNewPartitionReplicaAssignments(
        topicName, clusterIdentifier, newPartitionsAssignments, throttleBytesPerSec
    )

    @GetMapping("/verify-topic-partitions-reassignment")
    fun verifyPartitionReplicasAssignments(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): String = managementService.verifyPartitionReplicaAssignments(topicName, clusterIdentifier)

    @PostMapping("/run-preferred-replica-elections")
    fun runPreferredReplicaElections(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): Unit = managementService.runPreferredReplicaElections(topicName, clusterIdentifier)

    @DeleteMapping("/cancel-topic-partitions-reassignment")
    fun cancelTopicPartitionsReAssignment(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
    ): Unit = managementService.cancelPartitionReplicasReAssignments(topicName, clusterIdentifier)

    @PostMapping("/set-topic-config")
    fun setTopicConfigOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody topicConfigToSet: TopicConfigMap,
    ): Unit = managementService.setTopicConfigOnCluster(clusterIdentifier, topicName, topicConfigToSet)

}