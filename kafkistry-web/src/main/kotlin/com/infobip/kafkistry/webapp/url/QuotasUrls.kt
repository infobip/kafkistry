package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntityID

class QuotasUrls(base: String) : BaseUrls() {

    companion object {
        const val QUOTAS = "/quotas"

        const val QUOTAS_ENTITY = "/entity"
        const val QUOTAS_ENTITY_HISTORY = "/entity/history"
        const val QUOTAS_CREATE_ENTITY = "/create-entity"
        const val QUOTAS_EDIT_ENTITY = "/edit-entity"
        const val QUOTAS_EDIT_ENTITY_ON_BRANCH = "/edit-entity-on-branch"
        const val QUOTAS_DELETE_ENTITY = "/delete-entity"
        const val QUOTAS_IMPORT_ENTITY = "/import-entity"
        const val QUOTAS_SUGGESTED_EDIT_ENTITY = "/suggested-edit-entity"

        const val QUOTAS_MANAGEMENT_CREATE_ENTITY_QUOTAS = "/management/create-entity-quotas"
        const val QUOTAS_MANAGEMENT_UPDATE_ENTITY_QUOTAS = "/management/update-entity-quotas"
        const val QUOTAS_MANAGEMENT_DELETE_ENTITY_QUOTAS = "/management/delete-entity-quotas"
        const val QUOTAS_MANAGEMENT_BULK_CREATE_ENTITY_QUOTAS = "/management/bulk-create-entity-quotas"
        const val QUOTAS_MANAGEMENT_BULK_UPDATE_ENTITY_QUOTAS = "/management/bulk-update-entity-quotas"
        const val QUOTAS_MANAGEMENT_BULK_DELETE_ENTITY_QUOTAS = "/management/bulk-delete-entity-quotas"

        const val QUOTAS_MANAGEMENT_BULK_CREATE_CLUSTER_QUOTAS = "/management/bulk-create-cluster-quotas"
    }

    private val showAll = Url(base)
    private val showEntity = Url("$base$QUOTAS_ENTITY", listOf("quotaEntityID"))
    private val showEntityHistory = Url("$base$QUOTAS_ENTITY_HISTORY", listOf("quotaEntityID"))
    private val showCreateEntity = Url("$base$QUOTAS_CREATE_ENTITY")
    private val showEditEntity = Url("$base$QUOTAS_EDIT_ENTITY", listOf("quotaEntityID"))
    private val showEditEntityOnBranch = Url("$base$QUOTAS_EDIT_ENTITY_ON_BRANCH", listOf("quotaEntityID", "branch"))
    private val showDeleteEntity = Url("$base$QUOTAS_DELETE_ENTITY", listOf("quotaEntityID"))
    private val showImportEntity = Url("$base$QUOTAS_IMPORT_ENTITY", listOf("quotaEntityID"))
    private val showSuggestedEditEntity = Url("$base$QUOTAS_SUGGESTED_EDIT_ENTITY", listOf("quotaEntityID"))

    private val showCreateEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_CREATE_ENTITY_QUOTAS", listOf("quotaEntityID", "clusterIdentifier"))
    private val showUpdateEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_UPDATE_ENTITY_QUOTAS", listOf("quotaEntityID", "clusterIdentifier"))
    private val showDeleteEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_DELETE_ENTITY_QUOTAS", listOf("quotaEntityID", "clusterIdentifier"))
    private val showBulkCreateEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_BULK_CREATE_ENTITY_QUOTAS", listOf("quotaEntityID"))
    private val showBulkUpdateEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_BULK_UPDATE_ENTITY_QUOTAS", listOf("quotaEntityID"))
    private val showBulkDeleteEntityQuotas = Url("$base$QUOTAS_MANAGEMENT_BULK_DELETE_ENTITY_QUOTAS", listOf("quotaEntityID"))

    private val showBulkCreateClusterQuotas = Url("$base$QUOTAS_MANAGEMENT_BULK_CREATE_CLUSTER_QUOTAS", listOf("clusterIdentifier"))

    fun showAll() = showAll.render()

    fun showEntity(quotaEntityID: QuotaEntityID) = showEntity.render("quotaEntityID" to quotaEntityID)
    fun showEntityHistory(quotaEntityID: QuotaEntityID) = showEntityHistory.render("quotaEntityID" to quotaEntityID)

    fun showCreateEntity() = showCreateEntity.render()

    fun showEditEntity(quotaEntityID: QuotaEntityID) = showEditEntity.render("quotaEntityID" to quotaEntityID)

    fun showEditEntityOnBranch(
        quotaEntityID: QuotaEntityID, branch: String
    ) = showEditEntityOnBranch.render("quotaEntityID" to quotaEntityID, "branch" to branch)

    fun showDeleteEntity(quotaEntityID: QuotaEntityID) = showDeleteEntity.render("quotaEntityID" to quotaEntityID)

    fun showImportEntity(quotaEntityID: QuotaEntityID) = showImportEntity.render("quotaEntityID" to quotaEntityID)

    fun showSuggestedEditEntity(quotaEntityID: QuotaEntityID) =
        showSuggestedEditEntity.render("quotaEntityID" to quotaEntityID)

    fun showCreateEntityQuotas(
        quotaEntityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier
    ) = showCreateEntityQuotas.render("quotaEntityID" to quotaEntityID, "clusterIdentifier" to clusterIdentifier)

    fun showUpdateEntityQuotas(
        quotaEntityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier
    ) = showUpdateEntityQuotas.render("quotaEntityID" to quotaEntityID, "clusterIdentifier" to clusterIdentifier)

    fun showDeleteEntityQuotas(
        quotaEntityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier
    ) = showDeleteEntityQuotas.render("quotaEntityID" to quotaEntityID, "clusterIdentifier" to clusterIdentifier)

    fun showBulkCreateEntityQuotas(quotaEntityID: QuotaEntityID) =
        showBulkCreateEntityQuotas.render("quotaEntityID" to quotaEntityID)

    fun showBulkUpdateEntityQuotas(quotaEntityID: QuotaEntityID) =
        showBulkUpdateEntityQuotas.render("quotaEntityID" to quotaEntityID)

    fun showBulkDeleteEntityQuotas(quotaEntityID: QuotaEntityID) =
        showBulkDeleteEntityQuotas.render("quotaEntityID" to quotaEntityID)

    fun showBulkCreateClusterQuotas(clusterIdentifier: KafkaClusterIdentifier) =
        showBulkCreateClusterQuotas.render("clusterIdentifier" to clusterIdentifier)

}