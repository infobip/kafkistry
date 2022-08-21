package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.generator.OverridesMinimizer
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.WRONG_VALUE
import org.springframework.stereotype.Service

@Service
class QuotasSuggestionService(
    private val clustersRegistry: ClustersRegistryService,
    private val quotasInspectionService: QuotasInspectionService,
    private val overridesMinimizer: OverridesMinimizer,
) {

    fun suggestImport(quotaEntityID: QuotaEntityID): QuotaDescription {
        val inspection = quotasInspectionService.inspectEntityOnClusters(quotaEntityID)
        if (!inspection.status.statusCounts.any { it.type == UNKNOWN }) {
            throw KafkistryIllegalStateException(
                "Can't suggest import of quota entity ${inspection.entity} because it does not have status UNKNOWN, " +
                        "actual statuses: ${inspection.status.statusCounts.map { it.type.name }}"
            )
        }
        return inspection.suggestQuotaDescription()
    }

    fun suggestEdit(quotaEntityID: QuotaEntityID): QuotaDescription {
        val inspection = quotasInspectionService.inspectEntityOnClusters(quotaEntityID)
        if (inspection.quotaDescription == null) {
            throw KafkistryIllegalStateException(
                "Can't suggest edit of quota entity ${inspection.entity} because it does not exist in registry"
            )
        }
        return inspection.suggestQuotaDescription()
    }

    private fun EntityQuotasInspection.suggestQuotaDescription(): QuotaDescription {
        val allClusterRefs = clustersRegistry.listClustersRefs()
        val clusterQuotas = clusterInspections
            .filter {
                when (it.statusType) {
                    OK, UNEXPECTED, UNKNOWN, WRONG_VALUE -> true
                    MISSING, NOT_PRESENT_AS_EXPECTED, CLUSTER_DISABLED, UNAVAILABLE -> false
                    CLUSTER_UNREACHABLE -> throw KafkistryIllegalStateException(
                        "Can't suggest QuotaDescription because inspection of quota entity ${it.entity} " +
                                "on cluster '${it.clusterIdentifier}' is ${it.statusType}"
                    )
                    else -> true
                }
            }
            .mapNotNull { it.actualQuota?.let { quota -> it.clusterIdentifier to quota } }
            .toMap()
        val disabledClusterIdentifiers = clusterInspections
            .filter { it.statusType == CLUSTER_DISABLED }
            .map { it.clusterIdentifier }
        val presence = allClusterRefs.computePresence(clusterQuotas.keys.toList(), disabledClusterIdentifiers)
        val disabledClustersQuotas = disabledClusterIdentifiers
            .map { cluster -> allClusterRefs.first { it.identifier == cluster } }
            .filter { presence.needToBeOnCluster(it) }
            .mapNotNull { quotaDescription?.quotaForCluster(it)?.let { quotas -> it.identifier to quotas } }
            .toMap()
        return QuotaDescription(
            entity = entity,
            owner = quotaDescription?.owner ?: "",
            presence = presence,
            properties = quotaDescription?.properties ?: clusterQuotas.values.firstOrNull() ?: QuotaProperties.NONE,
            clusterOverrides = clusterQuotas + disabledClustersQuotas,
            tagOverrides = emptyMap(),
        ).minimize(allClusterRefs)
    }

    private fun QuotaDescription.minimize(allClusterRefs: List<ClusterRef>) =
        overridesMinimizer.minimizeOverrides(this, allClusterRefs)

}