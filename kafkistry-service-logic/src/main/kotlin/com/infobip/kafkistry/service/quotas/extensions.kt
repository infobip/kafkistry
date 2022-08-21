package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.service.quotas.AvailableQuotasOperation.*
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.WRONG_VALUE

fun QuotaDescription.quotaForCluster(cluster: ClusterRef): QuotaProperties =
    clusterOverrides[cluster.identifier]
        ?: cluster.tags.firstNotNullOfOrNull { tagOverrides[it] }
        ?: properties

fun QuotasInspectionResultType.availableOperations(): List<AvailableQuotasOperation> =
    when (this) {
        OK, NOT_PRESENT_AS_EXPECTED, CLUSTER_UNREACHABLE, CLUSTER_DISABLED, UNAVAILABLE -> emptyList()
        MISSING -> listOf(CREATE_MISSING_QUOTAS, EDIT_CLIENT_QUOTAS)
        UNEXPECTED -> listOf(DELETE_UNWANTED_QUOTAS, EDIT_CLIENT_QUOTAS)
        UNKNOWN -> listOf(DELETE_UNWANTED_QUOTAS, IMPORT_CLIENT_QUOTAS)
        WRONG_VALUE -> listOf(EDIT_CLIENT_QUOTAS, ALTER_WRONG_QUOTAS)
        else -> emptyList()
    }

fun <T> Iterable<T>.mergeAvailableOps(extractor: (T) -> List<AvailableQuotasOperation>): List<AvailableQuotasOperation> =
    flatMap(extractor).distinct().sorted()

