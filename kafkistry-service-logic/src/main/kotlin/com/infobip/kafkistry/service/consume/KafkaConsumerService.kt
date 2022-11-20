package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryConsumeException
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consume.serialize.KeySerializerType
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class KafkaConsumerService(
    private val topicReader: KafkaTopicReader,
    private val clustersRepository: ClustersRegistryService,
    private val topicPartitionResolver: TopicPartitionResolver,
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val userResolver: CurrentRequestUserResolver,
    private val clusterEnabledFilter: ClusterEnabledFilter
) {

    fun readRecords(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        readConfig: ReadConfig
    ): KafkaRecordsResult {
        val cluster = clustersRepository.getCluster(clusterIdentifier)
        cluster.ref().checkClusterEnabled()
        val user = userResolver.resolveUserOrUnknown()
        return topicReader.readTopicRecords(topicName, cluster, user.username, readConfig)
    }

    fun resolvePartitionForKey(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        key: String,
        keySerializerType: KeySerializerType,
    ): Partition {
        clustersRepository.getCluster(clusterIdentifier).ref().checkClusterEnabled()
        val topic = clustersStateProvider.getLatestClusterStateValue(clusterIdentifier)
            .topics
            .find { it.name == topicName }
            ?: throw KafkistryIllegalStateException("No topic '$topicName' found on cluster '$clusterIdentifier'")
        return topicPartitionResolver.resolvePartition(topicName, key, keySerializerType, topic.partitionsAssignments.size)
    }

    private fun ClusterRef.checkClusterEnabled() {
        if (!clusterEnabledFilter.enabled(this)) {
            throw KafkistryConsumeException("Cluster '$this' is disabled")
        }
    }

}
