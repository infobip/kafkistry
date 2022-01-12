package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.events.*
import com.infobip.kafkistry.kafka.ClientQuota
import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.KafkaQuotasProvider
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntityID
import org.springframework.stereotype.Service

@Service
class QuotasManagementService(
    private val quotasRegistry: QuotasRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaClientProvider: KafkaClientProvider,
    private val quotasProvider: KafkaQuotasProvider,
    private val inspectionService: QuotasInspectionService,
    private val eventPublisher: EventPublisher,
) {

    fun createMissingEntityQuotas(entityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val clientQuota = quotasRegistry.getQuotas(entityID).toClientQuotaFor(kafkaCluster.ref())
        val quotasInspection = inspectionService.inspectEntityQuotasOnCluster(entityID, clusterIdentifier)
        when (quotasInspection.statusType) {
            QuotasInspectionResultType.MISSING -> Unit
            else -> throw KafkistryIllegalStateException(
                "Entity's '$entityID' status is not missing, status is ${quotasInspection.statusType}"
            )
        }
        kafkaClientProvider.doWithClient(kafkaCluster) {
            it.setClientQuotas(listOf(clientQuota)).get()
        }
        quotasProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(QuotasCreatedEvent(clusterIdentifier, entityID))
    }

    fun deleteUnknownOrUnexpectedEntityQuotas(entityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val quotasInspection = inspectionService.inspectEntityQuotasOnCluster(entityID, clusterIdentifier)
        when (quotasInspection.statusType) {
            QuotasInspectionResultType.UNKNOWN, QuotasInspectionResultType.UNEXPECTED -> Unit
            else -> throw KafkistryIllegalStateException(
                "Entity's '$entityID' status is not unknown or unexpected, status is ${quotasInspection.statusType}"
            )
        }
        kafkaClientProvider.doWithClient(kafkaCluster) {
            it.removeClientQuotas(listOf(quotasInspection.entity)).get()
        }
        quotasProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(QuotasRemovedEvent(clusterIdentifier, entityID))
    }

    fun updateEntityWrongQuotas(entityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val clientQuota = quotasRegistry.getQuotas(entityID).toClientQuotaFor(kafkaCluster.ref())
        val quotasInspection = inspectionService.inspectEntityQuotasOnCluster(entityID, clusterIdentifier)
        when (quotasInspection.statusType) {
            QuotasInspectionResultType.WRONG_VALUE -> Unit
            else -> throw KafkistryIllegalStateException(
                "Entity's '$entityID' status is not wrong value, status is ${quotasInspection.statusType}"
            )
        }
        kafkaClientProvider.doWithClient(kafkaCluster) {
            it.setClientQuotas(listOf(clientQuota)).get()
        }
        quotasProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(QuotasAlteredEvent(clusterIdentifier, entityID))
    }

    private fun QuotaDescription.toClientQuotaFor(clusterRef: ClusterRef) = ClientQuota(entity, quotaForCluster(clusterRef))
}