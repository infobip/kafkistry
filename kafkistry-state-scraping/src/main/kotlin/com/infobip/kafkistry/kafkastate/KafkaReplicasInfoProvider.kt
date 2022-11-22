package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component

@Component
class KafkaReplicasInfoProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<ReplicaDirs>(
    clustersRepository, clusterFilter, poolingProperties, promProperties, issuesRegistry,
) {

    companion object {
        const val DIR_REPLICAS = "dir_replicas"
    }

    override val stateTypeName = DIR_REPLICAS

    override fun fetchState(kafkaCluster: KafkaCluster): ReplicaDirs {
        val replicas = clientProvider.doWithClient(kafkaCluster) {
            it.describeReplicas().get()
        }
        return ReplicaDirs(replicas.toTopicsReplicasInfos())
    }

}
fun List<TopicPartitionReplica>.toTopicsReplicasInfos(): Map<TopicName, TopicReplicaInfos> {
    return this
        .groupBy { it.topic }
        .mapValues { (topicName, replicas) ->
            replicas.toTopicReplicasInfos(topicName)
        }
}

fun List<TopicPartitionReplica>.toTopicReplicasInfos(topicName: TopicName): TopicReplicaInfos {
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
    val brokerTotalSizes = this
        .groupBy { it.brokerId }
        .mapValues { (_, replicas) ->
            replicas.sumOf { it.sizeBytes }
        }
    return TopicReplicaInfos(
        topic = topicName,
        totalSizeBytes = this.sumOf { it.sizeBytes },
        brokerTotalSizes = brokerTotalSizes,
        partitionBrokerReplicas = partitionBrokerReplicas,
        brokerPartitionReplicas = brokerPartitionReplicas
    )
}
