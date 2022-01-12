package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.ThrottleRate
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClusterManagementService
import com.infobip.kafkistry.service.topic.TopicManagementService
import org.springframework.web.bind.annotation.*

/**
 * Management operations on kafka cluster brokers
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/clusters-management")
class ClustersManagementApi(
    private val clusterManagementService: ClusterManagementService,
    private val topicManagementService: TopicManagementService,
) {

    @PostMapping("/throttle")
    fun applyAllBrokersThrottle(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestBody throttleRate: ThrottleRate,
    ): Unit = clusterManagementService.applyAllBrokersThrottle(clusterIdentifier, throttleRate)

    @PostMapping("/throttle/{brokerId}")
    fun applyBrokerThrottle(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("brokerId") brokerId: BrokerId,
        @RequestBody throttleRate: ThrottleRate,
    ): Unit = clusterManagementService.applyBrokerThrottle(clusterIdentifier, brokerId, throttleRate)

    /**
     * Reason why this method is written, instead just using ManagementApi.applyNewPartitionReplicasAssignments
     * multiple times, is that kafka clusters with version < 2.4 do not support starting new re-assignments while some
     * previous re-assignment is still in progress. Without this method, re-assigning for multiple topics at the same
     * time would not be possible.
     */
    @PostMapping("/apply-topics-bulk-re-assignments")
    fun bulkApplyTopicsReAssignments(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("throttleBytesPerSec") throttleBytesPerSec: Int,
        @RequestBody topicPartitionReAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>,
    ): Unit = topicManagementService.applyTopicsBulkPartitionReplicaAssignments(
        clusterIdentifier, topicPartitionReAssignments, throttleBytesPerSec
    )

}