package com.infobip.kafkistry.service.generator.balance

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicName

data class CollectionLoad<K, L>(
        val elements: Map<K, L>,
        val average: L
) {

    fun sortedElements(by: (load: L, avgLoad: L) -> Double): List<ElementScore<K>> {
        return elements.map { ElementScore(it.key, by(it.value, average))  }.sortedBy { it.score }
    }

}

data class ElementScore<K>(
        val id: K,
        val score: Double
)

data class BrokerAssignments(
        val brokerId: BrokerId,
        val topicPartitions: List<TopicPartition>,
        val topics: Map<TopicName, List<Partition>>
)

data class GlobalContext(
        val state: GlobalState,
        val topicsAssignments: Map<TopicName, TopicAssignments>,
        val brokersLoad: CollectionLoad<BrokerId, BrokerLoad>,
        val topicPartitionsLoad: Map<TopicName, CollectionLoad<Partition, PartitionLoad>>,
        val brokersAssignments: Map<BrokerId, BrokerAssignments>,
        val brokerTopicPartitionLoads: Map<BrokerId, Map<TopicPartition, PartitionLoad>>
)

enum class LoadDiffType {
        BALANCED,
        OVERLOADED,
        UNDERLOADED,
        MIXED
}


