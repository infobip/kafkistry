package com.infobip.kafkistry.metric

import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consumers.ConsumerTopicPartitionMember

interface LagMetricsAdditionalLabels {

    fun labelNames(): List<String>

    fun labelValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topic: TopicName,
        consumerGroupId: ConsumerGroupId,
        partitionMember: ConsumerTopicPartitionMember,
    ): List<String>
}

class EmptyLagMetricsAdditionalLabels : LagMetricsAdditionalLabels {

    override fun labelNames(): List<String> = emptyList()

    override fun labelValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topic: TopicName,
        consumerGroupId: ConsumerGroupId,
        partitionMember: ConsumerTopicPartitionMember,
    ): List<String> = emptyList()

}