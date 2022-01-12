@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.quotas.EntityQuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType
import com.infobip.kafkistry.service.quotas.QuotasInspectionService
import com.infobip.kafkistry.sql.SqlDataSource
import com.infobip.kafkistry.model.*
import org.springframework.stereotype.Component
import java.io.Serializable
import javax.persistence.*

@Component
class EntityQuotasDataSource(
    private val clustersRegistry: ClustersRegistryService,
    private val quotasInspectionService: QuotasInspectionService,
) : SqlDataSource<EntityQuota> {

    override fun modelAnnotatedClass(): Class<EntityQuota> = EntityQuota::class.java

    override fun supplyEntities(): List<EntityQuota> {
        val allClustersRefs = clustersRegistry.listClustersRefs()
        val clientEntities = quotasInspectionService.inspectAllClientEntities()
        val unknownClientEntities = quotasInspectionService.inspectUnknownClientEntities()
        return (clientEntities + unknownClientEntities).flatMap {
            mapEntityClusterQuotas(it, allClustersRefs)
        }
    }

    private fun mapEntityClusterQuotas(
        entityQuotasInspection: EntityQuotasInspection, allClusters: List<ClusterRef>
    ): List<EntityQuota> {
        val shouldExistMap = entityQuotasInspection.quotaDescription?.presence?.let { presence ->
            allClusters.associate { it.identifier to presence.needToBeOnCluster(it) }
        }
        return entityQuotasInspection.clusterInspections.map { clusterInspection ->
            EntityQuota().apply {
                id = ClusterQuotaEntityId().apply {
                    cluster = clusterInspection.clusterIdentifier
                    quotaEntityID = entityQuotasInspection.entity.asID()
                }
                user = entityQuotasInspection.entity.user ?: "<all>"
                clientId = entityQuotasInspection.entity.clientId ?: "<all>"
                statusType = clusterInspection.statusType
                exist = when (clusterInspection.statusType) {
                    QuotasInspectionResultType.OK, QuotasInspectionResultType.UNEXPECTED, QuotasInspectionResultType.UNKNOWN -> true
                    QuotasInspectionResultType.MISSING, QuotasInspectionResultType.NOT_PRESENT_AS_EXPECTED, QuotasInspectionResultType.UNAVAILABLE, QuotasInspectionResultType.WRONG_VALUE -> false
                    QuotasInspectionResultType.CLUSTER_DISABLED, QuotasInspectionResultType.CLUSTER_UNREACHABLE -> null
                }
                shouldExist = shouldExistMap?.get(clusterInspection.clusterIdentifier) ?: false
                affectedPrincipals = clusterInspection.affectedPrincipals

                expectedProducerByteRate = clusterInspection.expectedQuota?.producerByteRate
                actualProducerByteRate = clusterInspection.actualQuota?.producerByteRate
                producerByteRateOk = clusterInspection.valuesInspection.producerByteRateOk

                expectedConsumerByteRate = clusterInspection.expectedQuota?.consumerByteRate
                actualConsumerByteRate = clusterInspection.actualQuota?.consumerByteRate
                consumerByteRateOk = clusterInspection.valuesInspection.consumerByteRateOk

                expectedRequestPercentage = clusterInspection.expectedQuota?.requestPercentage
                actualRequestPercentage = clusterInspection.actualQuota?.requestPercentage
                requestPercentageOk = clusterInspection.valuesInspection.requestPercentageOk
            }
        }
    }

}

@Embeddable
class ClusterQuotaEntityId : Serializable {

    lateinit var cluster: KafkaClusterIdentifier
    lateinit var quotaEntityID: QuotaEntityID
}

@Entity
@Table(name = "EntityQuotas")
class EntityQuota {

    @EmbeddedId
    lateinit var id: ClusterQuotaEntityId

    lateinit var user: KafkaUser
    lateinit var clientId: KafkaUser

    var exist: Boolean? = null

    @Column(nullable = false)
    var shouldExist: Boolean? = null

    @Enumerated(EnumType.STRING)
    lateinit var statusType: QuotasInspectionResultType

    var expectedProducerByteRate: Long? = null
    var actualProducerByteRate: Long? = null
    var producerByteRateOk: Boolean? = null

    var expectedConsumerByteRate: Long? = null
    var actualConsumerByteRate: Long? = null
    var consumerByteRateOk: Boolean? = null

    var expectedRequestPercentage: Double? = null
    var actualRequestPercentage: Double? = null
    var requestPercentageOk: Boolean? = null

    @ElementCollection
    @Column(name = "principal")
    @JoinTable(name = "EntityQuotas_AffectedPrincipals")
    lateinit var affectedPrincipals: List<PrincipalId>

}

