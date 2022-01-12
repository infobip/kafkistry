package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.events.ConsumerGroupDeletedEvent
import com.infobip.kafkistry.events.ConsumerGroupOffsetsDeletedEvent
import com.infobip.kafkistry.events.ConsumerGroupResetEvent
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ConsumersService(
    private val clustersRegistryService: ClustersRegistryService,
    private val consumersInspector: ConsumersInspector,
    private val permissionAuthorizer: ConsumerAlterPermissionAuthorizer,
    private val clientProvider: KafkaClientProvider,
    private val eventPublisher: EventPublisher
) {

    private val log = LoggerFactory.getLogger(ConsumersService::class.java)

    fun allConsumersData(): AllConsumersData {
        val clusterConsumerGroups = clustersRegistryService.listClustersIdentifiers()
                .map { consumersInspector.inspectClusterConsumerGroups(it) }
        val clustersDataStatuses = clusterConsumerGroups.map {
            ClusterDataStatus(
                    clusterIdentifier = it.clusterIdentifier,
                    lastRefreshTime = it.lastRefreshTime,
                    clusterStatus = it.clusterStateType
            )
        }
        val clustersConsumers = clusterConsumerGroups.flatMap {
            it.consumerGroups.map { group -> group.toClusterConsumerGroup(it.clusterIdentifier) }
        }
        val consumersStats = ConsumersStats(
                clusterCounts = clustersConsumers.groupingBy { it.clusterIdentifier }.eachCountDescending(),
                lagStatusCounts = clustersConsumers.groupingBy { it.consumerGroup.lag.status }.eachCountDescending(),
                partitionAssignorCounts = clustersConsumers.groupingBy { it.consumerGroup.partitionAssignor }.eachCountDescending(),
                consumerStatusCounts = clustersConsumers.groupingBy { it.consumerGroup.status }.eachCountDescending()
        )
        return AllConsumersData(clustersDataStatuses, consumersStats, clustersConsumers)
    }

    fun listConsumerGroups(clusterIdentifier: KafkaClusterIdentifier): ClusterConsumerGroups {
        return consumersInspector.inspectClusterConsumerGroups(clusterIdentifier)
    }

    fun listConsumerGroupIds(clusterIdentifier: KafkaClusterIdentifier): List<ConsumerGroupId> {
        return consumersInspector.listClusterConsumerGroupIds(clusterIdentifier)
    }

    fun consumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ): KafkaConsumerGroup? {
        return consumersInspector.inspectClusterConsumerGroup(clusterIdentifier, consumerGroupId)
    }

    fun deleteConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ) {
        permissionAuthorizer.authorizeGroupAccess(consumerGroupId, clusterIdentifier)
        val kafkaCluster = clustersRegistryService.getCluster(clusterIdentifier)
        log.info("Going to delete consumer group '{}' on cluster '{}'", consumerGroupId, clusterIdentifier)
        clientProvider.doWithClient(kafkaCluster) { client ->
            client.deleteConsumer(consumerGroupId).get()
        }
        log.info("Completed deletion of consumer group '{}' on cluster '{}'", consumerGroupId, clusterIdentifier)
        eventPublisher.publish(ConsumerGroupDeletedEvent(clusterIdentifier, consumerGroupId))
    }

    fun deleteConsumerGroupOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId,
        topicPartitions: Map<TopicName, List<Partition>>,
    ) {
        permissionAuthorizer.authorizeGroupAccess(consumerGroupId, clusterIdentifier)
        val kafkaCluster = clustersRegistryService.getCluster(clusterIdentifier)
        log.info("Going to delete consumer group's '{}' specific topic-partition offsets on cluster '{}', partitions: {}",
            consumerGroupId, clusterIdentifier, topicPartitions
        )
        clientProvider.doWithClient(kafkaCluster) { client ->
            client.deleteConsumerOffsets(consumerGroupId, topicPartitions).get()
        }
        log.info("Completed deletion of consumer group '{}' specific topic-partition offsets on cluster '{}', partitions: {}",
            consumerGroupId, clusterIdentifier, topicPartitions
        )
        eventPublisher.publish(ConsumerGroupOffsetsDeletedEvent(clusterIdentifier, consumerGroupId, topicPartitions))
    }

    fun clusterTopicConsumers(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): List<KafkaConsumerGroup> {
        return consumersInspector.inspectClusterConsumerGroups(clusterIdentifier)
                .consumerGroups
                .filter { group -> group.topicMembers.any { it.topicName == topicName } }
    }

    private fun <K> Grouping<*, K>.eachCountDescending(): Map<K, Int> {
        return eachCount().map { it.toPair() }.sortedByDescending { it.second }.associate { it }
    }

    private fun KafkaConsumerGroup.toClusterConsumerGroup(
            clusterIdentifier: KafkaClusterIdentifier
    ) = ClusterConsumerGroup(
            clusterIdentifier = clusterIdentifier,
            consumerGroup = this
    )

    fun resetConsumerGroupOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId,
        reset: GroupOffsetsReset
    ): GroupOffsetResetChange {
        permissionAuthorizer.authorizeGroupAccess(consumerGroupId, clusterIdentifier)
        val kafkaCluster = clustersRegistryService.getCluster(clusterIdentifier)
        log.info("Going to reset consumer group '{}' on cluster '{}' {}", consumerGroupId, clusterIdentifier, reset)
        val change = clientProvider.doWithClient(kafkaCluster) { client ->
            client.resetConsumerGroup(consumerGroupId, reset).get()
        }
        log.info("Completed reset consumer group '{}' on cluster '{}' {}", consumerGroupId, clusterIdentifier, reset)
        eventPublisher.publish(ConsumerGroupResetEvent(clusterIdentifier, consumerGroupId, change))
        return change
    }

}