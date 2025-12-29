package com.infobip.kafkistry.service.produce

import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.auth.owners.UserOwnerVerifier
import com.infobip.kafkistry.webapp.security.isAdmin
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.produce.enabled", matchIfMissing = false)
class KafkaProducerService(
    private val topicWriter: KafkaTopicWriter,
    private val clustersRepository: ClustersRegistryService,
    private val topicsRepository: TopicsRegistryService,
    private val userResolver: CurrentRequestUserResolver,
    private val clusterEnabledFilter: ClusterEnabledFilter,
    private val userIsOwnerVerifiers: List<UserOwnerVerifier>,
) {

    fun produceRecord(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        produceRequest: ProduceRequest
    ): ProduceResult {
        val cluster = clustersRepository.getCluster(clusterIdentifier)
        cluster.ref().checkClusterEnabled()
        val user = userResolver.resolveUserOrUnknown()
        checkUserAllowedToProduce(topicName, user)
        return topicWriter.produceRecord(topicName, cluster, user.username, produceRequest)
    }

    private fun ClusterRef.checkClusterEnabled() {
        if (!clusterEnabledFilter.enabled(this)) {
            throw KafkistryProduceException("Cluster '$this' is disabled")
        }
    }

    private fun checkUserAllowedToProduce(topicName: TopicName, user: User) {
        if (user.isAdmin()) {
            return
        }
        val topicDescription = topicsRepository.findTopic(topicName)
            ?: throw KafkistryProduceException("Topic '$topicName' not found in registry")

        val allowedOwners = topicDescription.owner
            .split(",").map { it.trim() }
            .filter { it.isNotBlank() }
        val userIsOwner = userIsOwnerVerifiers.any { verifier ->
            allowedOwners.any { owner -> verifier.isUserOwner(user, owner) }
        }
        if (!userIsOwner) {
            throw KafkistryProduceException(
                "User '${user.username}' is not authorized to produce to topic '$topicName'. " +
                "Topic owner is '${topicDescription.owner}'"
            )
        }
    }
}
