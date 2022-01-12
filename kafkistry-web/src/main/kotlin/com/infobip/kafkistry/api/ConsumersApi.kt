package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.GroupOffsetResetChange
import com.infobip.kafkistry.kafka.GroupOffsetsReset
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consumers.AllConsumersData
import com.infobip.kafkistry.service.consumers.ClusterConsumerGroups
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.consumers.KafkaConsumerGroup
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/consumers")
class ConsumersApi(
    private val consumersService: ConsumersService
) {

    @GetMapping
    fun allConsumersData(): AllConsumersData = consumersService.allConsumersData()

    @GetMapping("/clusters/{clusterIdentifier}")
    fun clusterConsumerGroups(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ClusterConsumerGroups = consumersService.listConsumerGroups(clusterIdentifier)

    @GetMapping("/clusters/{clusterIdentifier}/groups")
    fun listClusterConsumerGroupIds(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): List<ConsumerGroupId> = consumersService.listConsumerGroupIds(clusterIdentifier)

    @GetMapping("/clusters/{clusterIdentifier}/groups/{consumerGroupId}")
    fun clusterConsumerGroup(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("consumerGroupId") consumerGroupId: ConsumerGroupId,
    ): KafkaConsumerGroup? = consumersService.consumerGroup(clusterIdentifier, consumerGroupId)

    @GetMapping("/clusters/{clusterIdentifier}/topics/{topicName}")
    fun clusterTopicConsumers(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("topicName") topicName: TopicName,
    ): List<KafkaConsumerGroup> = consumersService.clusterTopicConsumers(clusterIdentifier, topicName)

    @DeleteMapping("/clusters/{clusterIdentifier}/groups/{consumerGroupId}")
    fun deleteClusterConsumerGroup(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("consumerGroupId") consumerGroupId: ConsumerGroupId,
    ): Unit = consumersService.deleteConsumerGroup(clusterIdentifier, consumerGroupId)

    @PutMapping("/clusters/{clusterIdentifier}/groups/{consumerGroupId}/offsets/delete")
    fun deleteClusterConsumerGroupOffsets(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("consumerGroupId") consumerGroupId: ConsumerGroupId,
        @RequestBody topicPartitions: Map<TopicName, List<Partition>>,
    ): Unit = consumersService.deleteConsumerGroupOffsets(clusterIdentifier, consumerGroupId, topicPartitions)

    @PostMapping("/clusters/{clusterIdentifier}/groups/{consumerGroupId}/reset-offsets")
    fun resetConsumerGroupOffsets(
        @PathVariable("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @PathVariable("consumerGroupId") consumerGroupId: ConsumerGroupId,
        @RequestBody reset: GroupOffsetsReset,
    ): GroupOffsetResetChange = consumersService.resetConsumerGroupOffsets(clusterIdentifier, consumerGroupId, reset)

}