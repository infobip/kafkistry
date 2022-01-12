package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.KafkistryConsumeException
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class KafkaConsumerService(
    private val topicReader: KafkaTopicReader,
    private val clustersRepository: ClustersRegistryService,
    private val userResolver: CurrentRequestUserResolver,
    private val clusterEnabledFilter: ClusterEnabledFilter
) {

    fun readRecords(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        readConfig: ReadConfig
    ): KafkaRecordsResult {
        clusterIdentifier.checkClusterEnabled()
        val cluster = clustersRepository.getCluster(clusterIdentifier)
        val user = userResolver.resolveUserOrUnknown()
        return topicReader.readTopicRecords(topicName, cluster, user.username, readConfig)
    }

    private fun KafkaClusterIdentifier.checkClusterEnabled() {
        if (!clusterEnabledFilter.enabled(this)) {
            throw KafkistryConsumeException("Cluster '$this' is disabled")
        }
    }

}
