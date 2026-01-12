package com.infobip.kafkistry.service.replicadirs

import com.infobip.kafkistry.kafkastate.KafkaReplicasInfoProvider
import com.infobip.kafkistry.kafkastate.TopicReplicaInfos
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.stereotype.Service

@Service
class ReplicaDirsService(
    private val replicasInfoProvider: KafkaReplicasInfoProvider
) {

    fun topicReplicaInfos(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName
    ): TopicReplicaInfos? {
        return replicasInfoProvider.getLatestState(clusterIdentifier)
            .valueOrNull()
            ?.topicReplicas
            ?.get(topicName)
    }

    fun clusterTopicReplicaInfos(
            clusterIdentifier: KafkaClusterIdentifier
    ): Map<TopicName, TopicReplicaInfos> {
       return replicasInfoProvider.getLatestState(clusterIdentifier).valueOrNull()?.topicReplicas ?: emptyMap()
    }

    fun allClustersTopicReplicaInfos(): Map<KafkaClusterIdentifier, Map<TopicName, TopicReplicaInfos>> {
        return replicasInfoProvider.getAllLatestStates()
            .mapValues { (_, clusterReplicas) ->
                clusterReplicas.valueOrNull()?.topicReplicas ?: emptyMap()
            }
    }

}