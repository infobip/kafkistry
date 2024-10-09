package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.events.ClustersRepositoryEvent
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.ConnectionDefinition
import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.repository.*
import com.infobip.kafkistry.repository.storage.Branch
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.history.ClusterChange
import com.infobip.kafkistry.service.history.ClusterRequest
import com.infobip.kafkistry.service.topic.validation.NamingValidator
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Service

@Service
class ClustersRegistryService(
        clustersRepository: KafkaClustersRepository,
        userResolver: CurrentRequestUserResolver,
        eventPublisher: EventPublisher,
        private val clientProvider: KafkaClientProvider,
        private val topicsRepository: KafkaTopicsRepository,
        private val aclsRepository: KafkaAclsRepository,
        private val namingValidator: NamingValidator
) : AbstractRegistryService<KafkaClusterIdentifier, KafkaCluster, KafkaClustersRepository, ClusterRequest, ClusterChange>(
        clustersRepository, userResolver, eventPublisher
) {

    override fun preCreateCheck(entity: KafkaCluster) {
        namingValidator.validateClusterIdentifier(entity.identifier)
        checkDuplicateByClusterId(entity)
    }

    override fun preUpdateCheck(entity: KafkaCluster) {
        checkDuplicateByClusterId(entity)
    }

    override fun preDeleteCheck(id: KafkaClusterIdentifier) {
        val topicUsages = topicsRepository.findAll()
                .filter { topic ->
                    emptySet<String>()
                            .plus(topic.presence.kafkaClusterIdentifiers ?: emptyList())
                            .plus(topic.perClusterProperties.keys)
                            .plus(topic.perClusterConfigOverrides.keys)
                            .contains(id)
                }
                .map { it.name }
                .toList()
        val aclPrincipalUsages = aclsRepository.findAll()
                .filter { principalAcls ->
                    id in principalAcls.rules.flatMap { it.presence.kafkaClusterIdentifiers ?: emptyList() }
                }
                .map { it.principal }

        if (topicUsages.isNotEmpty() || aclPrincipalUsages.isNotEmpty()) {
            throw KafkistryIntegrityException(
                    "Can't remove cluster '$id', its being referenced in topics: $topicUsages and/or acls of principals: $aclPrincipalUsages"
            )
        }
    }

    override fun generateRepositoryEvent(id: KafkaClusterIdentifier?) = ClustersRepositoryEvent(id)

    override fun mapChangeRequest(id: KafkaClusterIdentifier, changeRequest: ChangeRequest<KafkaCluster>) = ClusterRequest(
            branch = changeRequest.branch,
            commitChanges = changeRequest.commitChanges,
            type = changeRequest.type,
            errorMsg = changeRequest.optionalEntity.errorMsg,
            identifier = id,
            cluster = changeRequest.optionalEntity.entity
    )

    override fun mapChange(change: EntityCommitChange<KafkaClusterIdentifier, KafkaCluster>) = ClusterChange (
            changeType = change.changeType,
            oldContent = change.fileChange.oldContent,
            newContent = change.fileChange.newContent,
            errorMsg = change.optionalEntity.errorMsg,
            identifier = change.id,
            cluster = change.optionalEntity.entity
    )

    override val KafkaCluster.id: KafkaClusterIdentifier get() = identifier
    override val type: Class<KafkaCluster> get() = KafkaCluster::class.java

    fun addCluster(cluster: KafkaCluster, updateContext: UpdateContext = UpdateContext("adding")) = create(cluster, updateContext)
    fun updateCluster(cluster: KafkaCluster, updateContext: UpdateContext = UpdateContext("updating")) = update(cluster, updateContext)
    fun updateClusters(clusters: List<KafkaCluster>, updateContext: UpdateContext = UpdateContext("updating")) = updateMulti(clusters, updateContext)
    fun removeCluster(clusterIdentifier: KafkaClusterIdentifier, updateContext: UpdateContext = UpdateContext("removing")) = delete(clusterIdentifier, updateContext)

    private fun checkDuplicateByClusterId(cluster: KafkaCluster) {
        listAll()
                .firstOrNull { it.identifier == cluster.identifier && it.clusterId != cluster.clusterId }
                ?.also {
                    throw KafkistryIntegrityException("Cluster with same cluster id already exist $it")
                }
    }

    fun removeAll(updateContext: UpdateContext = UpdateContext("removing all")) = deleteAll(updateContext)
    fun findCluster(clusterIdentifier: KafkaClusterIdentifier): KafkaCluster? = findOne(clusterIdentifier)
    fun getCluster(clusterIdentifier: KafkaClusterIdentifier): KafkaCluster = getOne(clusterIdentifier)
    fun listClusters(): List<KafkaCluster> = listAll()
    fun listClustersAt(branch: Branch): List<KafkaCluster> = listAllAt(branch)

    fun listClustersIdentifiers(): List<KafkaClusterIdentifier> = listClusters().map { it.identifier }
    fun listClustersRefs(): List<ClusterRef> = listClusters().map { it.ref() }

    fun testClusterConnectionReadInfo(connectionDefinition: ConnectionDefinition): ClusterInfo {
        return clientProvider.doWithNewClient(connectionDefinition) {
            it.clusterInfo("").get()
        }
    }

    fun listAllTagClusters(): List<TagClusters> {
        return listClustersRefs()
            .flatMap { ref ->
                ref.tags.map { it to ref.identifier }
            }
            .groupBy({ it.first }, { it.second })
            .map { (tag, clusters) -> TagClusters(tag, clusters) }
            .sortedBy { it.tag }
    }

}