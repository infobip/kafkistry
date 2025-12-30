package com.infobip.kafkistry.service.produce

import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.produce.config.ProduceProperties
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
    private val produceProperties: ProduceProperties,
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
        val topicDescription = topicsRepository.findTopic(topicName)

        // Allow admin to produce to topics not in registry
        if (topicDescription == null) {
            if (user.isAdmin() && produceProperties.allowedByDefault) {
                return
            }
            throw KafkistryProduceException("Topic '$topicName' not found in registry")
        }

        // Check if user is owner (skip for admin)
        if (!user.isAdmin()) {
            val allowedOwners = topicDescription.owner
                .split(",").map { it.trim() }
                .filter { it.isNotBlank() }
            val userIsOwner = userIsOwnerVerifiers.any { verifier ->
                allowedOwners.any { owner -> verifier.isUserOwner(user, owner) }
            }
            if (!userIsOwner) {
                throw KafkistryProduceException(
                    "User '${user.username}' is not authorized to produce to topic '$topicName'. " +
                            "Topic owner is '${topicDescription.owner}' which user '${user.username}' is not member of."
                )
            }
        }

        // Check explicit allow/deny via allowManualProduce field, fallback to property (applies to all users including admin)
        val allowed = topicDescription.allowManualProduce ?: produceProperties.allowedByDefault
        if (!allowed) {
            throw KafkistryProduceException(
                "Manual produce to topic '$topicName' is not allowed, " +
                        "global default: ${if (produceProperties.allowedByDefault) "ALLOWED" else "DENIED"}, " +
                        "setting for topic: ${if (topicDescription.allowManualProduce != null) "DENIED" else "(use global default)"}"
            )
        }
    }
}
