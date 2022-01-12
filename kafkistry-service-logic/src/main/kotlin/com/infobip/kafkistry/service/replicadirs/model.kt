package com.infobip.kafkistry.service.replicadirs

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.model.TopicName

data class TopicReplicaInfos(
        val topic: TopicName,
        val partitionBrokerReplicas: Map<Partition, Map<BrokerId, TopicPartitionReplica>>,
        val brokerPartitionReplicas: Map<BrokerId, Map<Partition, TopicPartitionReplica>>
)