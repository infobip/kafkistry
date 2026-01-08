package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.kafkastate.ClusterQuotas
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.WRONG_VALUE
import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
class QuotasIssuesInspector(
    private val aclLinkResolver: AclLinkResolver
) {

    fun inspectQuotas(
        entity: QuotaEntity,
        quotaDescription: QuotaDescription?,
        clusterRef: ClusterRef,
        clusterQuotasState: StateData<ClusterQuotas>,
    ): QuotasInspection {
        val shouldExist = quotaDescription?.presence?.needToBeOnCluster(clusterRef) ?: false
        val expectedQuota = quotaDescription?.takeIf { shouldExist }?.quotaForCluster(clusterRef)
        val clusterQuotas = clusterQuotasState.valueOrNull()
        val actualQuota = clusterQuotas?.quotas?.get(entity.asID())
        val valuesInspection = inspectValues(expectedQuota, actualQuota)
        val statusType = when {
            clusterQuotasState.stateType == StateType.DISABLED -> CLUSTER_DISABLED
            clusterQuotasState.stateType != StateType.VISIBLE || clusterQuotas == null -> CLUSTER_UNREACHABLE
            else -> computeResultType(actualQuota, quotaDescription, shouldExist, valuesInspection)
        }
        return QuotasInspection(
            entity = entity,
            expectedQuota = expectedQuota,
            actualQuota = actualQuota,
            clusterIdentifier = clusterRef.identifier,
            statusType = statusType,
            valuesInspection = valuesInspection,
            availableOperations = statusType.availableOperations(),
            affectedPrincipals = when (statusType) {
                UNAVAILABLE, NOT_PRESENT_AS_EXPECTED -> emptyList()
                else -> aclLinkResolver.findQuotaAffectingPrincipals(entity, clusterRef.identifier)
            }
        )
    }

    private fun computeResultType(
        actualQuota: QuotaProperties?,
        quotaDescription: QuotaDescription?,
        shouldExist: Boolean,
        valuesInspection: ValuesInspection,
    ): QuotasInspectionResultType {
        val exists = actualQuota != null
        if (quotaDescription == null) {
            return if (exists) UNKNOWN else UNAVAILABLE
        }
        if (actualQuota != null) {
            return if (shouldExist) {
                if (valuesInspection.ok) OK else WRONG_VALUE
            } else UNEXPECTED
        }
        return if (shouldExist) MISSING else NOT_PRESENT_AS_EXPECTED
    }

    private fun inspectValues(
        expectedQuota: QuotaProperties?,
        actualQuota: QuotaProperties?,
    ): ValuesInspection {
        val producerByteRateOk = inspectValue(expectedQuota, actualQuota) { it.producerByteRate }
        val consumerByteRateOk = inspectValue(expectedQuota, actualQuota) { it.consumerByteRate }
        val requestPercentageOk = inspectValue(expectedQuota, actualQuota) { it.requestPercentage }
        return ValuesInspection(
            ok = producerByteRateOk && consumerByteRateOk && requestPercentageOk,
            producerByteRateOk = producerByteRateOk,
            consumerByteRateOk = consumerByteRateOk,
            requestPercentageOk = requestPercentageOk,
        )
    }

    private fun inspectValue(
        expectedQuota: QuotaProperties?,
        actualQuota: QuotaProperties?,
        valueExtractor: (QuotaProperties) -> Number?
    ): Boolean {
        val expected = expectedQuota?.let(valueExtractor)
        val actual = actualQuota?.let(valueExtractor)
        return actualQuota != null && expectedQuota != null && valueOk(actual, expected)
    }

    private fun valueOk(actual: Number?, expected: Number?): Boolean {
        if (actual is Double && expected is Double) {
            if (abs(actual - expected) < 10e-7) {
                return true
            }
        } else if (actual == expected) {
            return true
        }
        return false
    }

}