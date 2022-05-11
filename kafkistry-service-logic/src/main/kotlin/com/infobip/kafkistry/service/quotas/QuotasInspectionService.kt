package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.kafkastate.KafkaQuotasProvider
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.computePresence
import org.springframework.stereotype.Service

@Service
class QuotasInspectionService(
    private val quotasRegistry: QuotasRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val quotasProvider: KafkaQuotasProvider,
    private val quotasIssuesInspector: QuotasIssuesInspector
) {

    fun inspectEntityQuotasOnCluster(
        entityID: QuotaEntityID,
        clusterIdentifier: KafkaClusterIdentifier
    ): QuotasInspection {
        val quotaDescription = quotasRegistry.findQuotas(entityID)
        val clusterQuotasState = quotasProvider.getLatestState(clusterIdentifier)
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return quotasIssuesInspector.inspectQuotas(
            QuotaEntity.fromID(entityID), quotaDescription, clusterRef, clusterQuotasState,
        )
    }

    fun inspectEntityOnClusters(entityID: QuotaEntityID): EntityQuotasInspection {
        val entity = QuotaEntity.fromID(entityID)
        val quotaDescription = quotasRegistry.findQuotas(entityID)
        return inspectClientEntity(entity, quotaDescription)
    }

    fun inspectClusterQuotas(clusterIdentifier: KafkaClusterIdentifier): ClusterQuotasInspection {
        val clusterRef = clustersRegistry.getCluster(clusterIdentifier).ref()
        return inspectClusterQuotas(clusterRef)
    }

    fun inspectClusterQuotas(clusterRef: ClusterRef): ClusterQuotasInspection {
        val entityQuotaDescriptions = quotasRegistry.listAllQuotas().associateBy { it.entity }
        val latestQuotasState = quotasProvider.getLatestState(clusterRef.identifier)
        val knownEntities = entityQuotaDescriptions.keys.toSet()
        val unknownEntities = latestQuotasState.valueOrNull()
            ?.quotas?.keys?.filter { it !in knownEntities }.orEmpty()
        val entityInspections = (knownEntities + unknownEntities).sortedBy { it.asID() }
            .map { entity ->
                quotasIssuesInspector.inspectQuotas(
                    entity, entityQuotaDescriptions[entity], clusterRef, latestQuotasState,
                )
            }
        return ClusterQuotasInspection(
            clusterIdentifier = clusterRef.identifier,
            entityInspections = entityInspections,
            status = QuotaStatus.from(entityInspections),
        )
    }

    fun inspectAllClientEntities(): List<EntityQuotasInspection> {
        return quotasRegistry.listAllQuotas().map { quotaDescription ->
            inspectClientEntity(quotaDescription.entity, quotaDescription)
        }
    }

    fun inspectUnknownClientEntities(): List<EntityQuotasInspection> {
        val knownEntities = quotasRegistry.listAllQuotas().map { it.entity }.toSet()
        val unknownEntities = clustersRegistry.listClustersIdentifiers()
            .map { quotasProvider.getLatestState(it) }
            .mapNotNull { it.valueOrNull()?.quotas }
            .flatMap { it.keys }
            .filter { it !in knownEntities }
            .distinct()
            .sortedBy { it.asID() }
        return unknownEntities.map { quotaEntity ->
            inspectClientEntity(quotaEntity, null)
        }
    }

    private fun inspectClientEntity(entity: QuotaEntity, quotaDescription: QuotaDescription?): EntityQuotasInspection {
        val clustersRefs = clustersRegistry.listClustersRefs()
        val clusterInspections = clustersRefs.map { clusterRef ->
            val clusterQuotasState = quotasProvider.getLatestState(clusterRef.identifier)
            quotasIssuesInspector.inspectQuotas(
                entity, quotaDescription, clusterRef, clusterQuotasState,
            )
        }
        val disabledClusters = clusterInspections
            .filter { it.statusType == QuotasInspectionResultType.CLUSTER_DISABLED }
            .map { it.clusterIdentifier }
        val affectedPrincipals = clusterInspections
            .flatMap { it.affectedPrincipals.map { principal -> principal to it.clusterIdentifier } }
            .groupBy ({ it.first }, { it.second })
            .mapValues { clustersRefs.computePresence(it.value, disabledClusters) }
        return EntityQuotasInspection(
            entity = entity,
            quotaDescription = quotaDescription,
            clusterInspections = clusterInspections,
            status = QuotaStatus.from(clusterInspections),
            availableOperations = clusterInspections.mergeAvailableOps { it.availableOperations },
            affectedPrincipals = affectedPrincipals,
        )
    }

}