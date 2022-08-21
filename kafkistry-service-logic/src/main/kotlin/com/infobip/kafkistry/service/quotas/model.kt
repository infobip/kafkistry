package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.storage.CommitChange
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.StatusLevel.*
import com.infobip.kafkistry.service.history.Change
import com.infobip.kafkistry.service.history.PendingRequest

data class QuotasRequest(
    override val branch: String,
    override val commitChanges: List<CommitChange>,
    override val type: ChangeType,
    override val errorMsg: String?,
    val entityID: QuotaEntityID,
    val quota: QuotaDescription?
) : PendingRequest

data class QuotasChange(
    override val changeType: ChangeType,
    override val oldContent: String?,
    override val newContent: String?,
    override val errorMsg: String?,
    val entityID: QuotaEntityID,
    val quota: QuotaDescription?
) : Change


data class QuotasInspectionResultType(
    override val name: String,
    override val level: StatusLevel,
    override val valid: Boolean,
) : NamedType {
    companion object {
        val OK = QuotasInspectionResultType("OK", SUCCESS, true)
        val MISSING = QuotasInspectionResultType("MISSING", ERROR, false)
        val UNEXPECTED =  QuotasInspectionResultType("UNEXPECTED", ERROR, false)
        val NOT_PRESENT_AS_EXPECTED = QuotasInspectionResultType("NOT_PRESENT_AS_EXPECTED", IGNORE, true)
        val UNKNOWN = QuotasInspectionResultType("UNKNOWN", WARNING, false)
        val WRONG_VALUE = QuotasInspectionResultType("WRONG_VALUE", ERROR, false)
        val CLUSTER_DISABLED = QuotasInspectionResultType("CLUSTER_DISABLED", IGNORE, true)
        val CLUSTER_UNREACHABLE = QuotasInspectionResultType("CLUSTER_UNREACHABLE", ERROR, false)
        val UNAVAILABLE = QuotasInspectionResultType("UNAVAILABLE", IGNORE, false)
    }
}

enum class AvailableQuotasOperation {
    CREATE_MISSING_QUOTAS,
    DELETE_UNWANTED_QUOTAS,
    ALTER_WRONG_QUOTAS,
    EDIT_CLIENT_QUOTAS,
    IMPORT_CLIENT_QUOTAS,
}

data class QuotaStatus(
    override val ok: Boolean,
    override val statusCounts: List<NamedTypeQuantity<QuotasInspectionResultType, Int>>
) : SubjectStatus<QuotasInspectionResultType> {
    companion object {
        val EMPTY_OK = QuotaStatus(true, emptyList())

        fun from(statuses: List<QuotasInspection>) = SubjectStatus.from(statuses.map { it.statusType }) { ok, statusCounts ->
            QuotaStatus(ok, statusCounts)
        }

    }
}

data class QuotasInspection(
    val entity: QuotaEntity,
    val expectedQuota: QuotaProperties?,
    val actualQuota: QuotaProperties?,
    val valuesInspection: ValuesInspection,
    val clusterIdentifier: KafkaClusterIdentifier,
    val statusType: QuotasInspectionResultType,
    val availableOperations: List<AvailableQuotasOperation>,
    val affectedPrincipals: List<PrincipalId>,
)

data class ValuesInspection(
    val ok: Boolean,
    val producerByteRateOk: Boolean,
    val consumerByteRateOk: Boolean,
    val requestPercentageOk: Boolean,
)

data class EntityQuotasInspection(
    val entity: QuotaEntity,
    val quotaDescription: QuotaDescription?,
    val clusterInspections: List<QuotasInspection>,
    val status: QuotaStatus,
    val availableOperations: List<AvailableQuotasOperation>,
    val affectedPrincipals: Map<PrincipalId, Presence>,
)

data class ClusterQuotasInspection(
    val clusterIdentifier: KafkaClusterIdentifier,
    val entityInspections: List<QuotasInspection>,
    val status: QuotaStatus,
)



