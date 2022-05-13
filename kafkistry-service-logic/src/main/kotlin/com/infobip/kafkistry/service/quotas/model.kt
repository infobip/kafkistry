package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.storage.CommitChange
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


enum class QuotasInspectionResultType(
    val valid: Boolean
) {
    OK(true),
    MISSING(false),
    UNEXPECTED(false),
    NOT_PRESENT_AS_EXPECTED(true),
    UNKNOWN(false),
    WRONG_VALUE(false),
    CLUSTER_DISABLED(true),
    CLUSTER_UNREACHABLE(false),
    UNAVAILABLE(false),
}

enum class AvailableQuotasOperation {
    CREATE_MISSING_QUOTAS,
    DELETE_UNWANTED_QUOTAS,
    ALTER_WRONG_QUOTAS,
    EDIT_CLIENT_QUOTAS,
    IMPORT_CLIENT_QUOTAS,
}

data class QuotaStatus(
    val ok: Boolean,
    val statusCounts: Map<QuotasInspectionResultType, Int>
) {
    companion object {
        val EMPTY_OK = QuotaStatus(true, emptyMap())

        fun from(statuses: List<QuotasInspection>) = QuotaStatus(
            ok = statuses.map { it.statusType }.map { it.valid }.fold(true, Boolean::and),
            statusCounts = statuses.groupingBy { it.statusType }.eachCount()
        )
    }

    infix fun merge(other: QuotaStatus) = QuotaStatus(
        ok = ok && other.ok,
        statusCounts = (statusCounts.asSequence() + other.statusCounts.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, counts) -> counts.sum() }
    )
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



