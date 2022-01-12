@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.sql.SqlDataSource
import com.infobip.kafkistry.model.*
import org.springframework.stereotype.Component
import javax.persistence.*

@Component
class RegistryQuotaEntitiesDataSource(
    private val clustersRegistry: ClustersRegistryService,
    private val quotasRegistry: QuotasRegistryService,
) : SqlDataSource<RegistryQuotaEntity> {

    override fun modelAnnotatedClass(): Class<RegistryQuotaEntity> = RegistryQuotaEntity::class.java

    override fun supplyEntities(): List<RegistryQuotaEntity> {
        val allClustersRefs = clustersRegistry.listClustersRefs()
        val allQuotas = quotasRegistry.listAllQuotas()
        return allQuotas.map { mapQuotaEntity(it, allClustersRefs) }
    }

    private fun mapQuotaEntity(quotaDescription: QuotaDescription, allClusters: List<ClusterRef>): RegistryQuotaEntity {
        return RegistryQuotaEntity().apply {
            quotaEntityID = quotaDescription.entity.asID()
            owner = quotaDescription.owner
            user = quotaDescription.entity.user ?: "<all>"
            clientId = quotaDescription.entity.clientId ?: "<all>"
            presenceType = quotaDescription.presence.type
            presenceClusters = allClusters
                .filter { quotaDescription.presence.needToBeOnCluster(it) }
                .map { PresenceCluster().apply { cluster = it.identifier } }
            presenceTag = quotaDescription.presence.tag
        }
    }

}

@Entity
@Table(name = "RegistryQuotaEntities")
class RegistryQuotaEntity {

    @Id
    lateinit var quotaEntityID: QuotaEntityID

    lateinit var user: KafkaUser
    lateinit var clientId: KafkaUser

    lateinit var owner: String

    @Enumerated(EnumType.STRING)
    lateinit var presenceType: PresenceType

    @ElementCollection
    @JoinTable(name = "RegistryQuotaEntities_PresenceClusters")
    lateinit var presenceClusters: List<PresenceCluster>

    var presenceTag: Tag? = null
}

