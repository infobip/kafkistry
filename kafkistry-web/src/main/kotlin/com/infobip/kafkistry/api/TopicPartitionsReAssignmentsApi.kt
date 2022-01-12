package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReAssignment
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.reassignments.TopicReAssignmentsMonitorService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/topics/partition-re-assignments")
class TopicPartitionsReAssignmentsApi(
    private val reAssignmentsMonitorService: TopicReAssignmentsMonitorService
) {

    @GetMapping
    fun getTopicPartitionReAssignments(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): Map<Partition, TopicPartitionReAssignment> = reAssignmentsMonitorService.topicReAssignments(clusterIdentifier, topicName)

}