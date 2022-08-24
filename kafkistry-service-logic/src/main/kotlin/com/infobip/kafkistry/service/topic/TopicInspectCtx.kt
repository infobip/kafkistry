package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class TopicInspectCtx(
    val topicName: TopicName,
    val clusterRef: ClusterRef,
    val latestClusterState: StateData<KafkaClusterState>,
    val topicDescription: TopicDescription?,
    val existingTopic: KafkaExistingTopic?,
    val currentTopicReplicaInfos: TopicReplicaInfos?,
    val partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
) {
    val clusterInfo: ClusterInfo? = latestClusterState.valueOrNull()?.clusterInfo

    private val cachedValues = ConcurrentHashMap<String, Any?>()

    fun <T> cache(type: Class<T>, initializer: TopicInspectCtx.() -> T): T {
        @Suppress("UNCHECKED_CAST")
        return cachedValues.computeIfAbsent(type.name) { initializer() } as T
    }

    inline fun <reified T> cache(noinline initializer: TopicInspectCtx.() -> T): T = cache(T::class.java, initializer)

}