package com.infobip.kafkistry.service.replicadirs

import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.kafkastate.KafkaReplicasInfoProvider
import com.infobip.kafkistry.kafkastate.ReplicaDirs
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Service

@Service
class ReplicaDirsService(
        private val replicasInfoProvider: KafkaReplicasInfoProvider
) {

    fun topicReplicaInfos(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): TopicReplicaInfos? {
        val clusterTopicReplicas = replicasInfoProvider.getLatestState(clusterIdentifier)
                .valueOrNull()
                ?.replicas?.filter { it.topic == topicName }
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        return clusterTopicReplicas.toTopicReplicasInfos(topicName)
    }

    fun clusterTopicReplicaInfos(
            clusterIdentifier: KafkaClusterIdentifier
    ): Map<TopicName, TopicReplicaInfos> {
        val clusterReplicas = replicasInfoProvider.getLatestState(clusterIdentifier)
                .valueOrNull()
                ?: return emptyMap()
        return clusterReplicas.toTopicsReplicasInfos()
    }

    fun allClustersTopicReplicaInfos(): Map<KafkaClusterIdentifier, Map<TopicName, TopicReplicaInfos>> {
        return replicasInfoProvider.getAllLatestStates()
                .mapValues { (_, clusterReplicas) ->
                    clusterReplicas.valueOrNull()
                            ?.toTopicsReplicasInfos()
                            ?: emptyMap()
                }
    }

    private fun ReplicaDirs.toTopicsReplicasInfos(): Map<TopicName, TopicReplicaInfos> {
        return replicas
                .groupBy { it.topic }
                .mapValues { (topicName, replicas) ->
                    replicas.toTopicReplicasInfos(topicName)
                }
    }

    private fun List<TopicPartitionReplica>.toTopicReplicasInfos(topicName: TopicName): TopicReplicaInfos {
        val brokerPartitionReplicas = this
                .groupBy { it.brokerId }
                .mapValues { (_, replicas) ->
                    replicas.associateBy { it.partition }
                }
        val partitionBrokerReplicas = this
                .groupBy { it.partition }
                .mapValues { (_, replicas) ->
                    replicas.associateBy { it.brokerId }
                }
        return TopicReplicaInfos(
                topic = topicName,
                partitionBrokerReplicas = partitionBrokerReplicas,
                brokerPartitionReplicas = brokerPartitionReplicas
        )
    }
}