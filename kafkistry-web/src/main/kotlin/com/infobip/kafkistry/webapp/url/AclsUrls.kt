package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId

class AclsUrls(base: String) : BaseUrls() {

    companion object {
        const val ACLS = "/acls"
        const val ACLS_PRINCIPALS_TABLE = "/principals-table"
        const val ACLS_PRINCIPAL = "/principal"
        const val ACLS_PRINCIPAL_HISTORY = "/principal/history"
        const val ACLS_DRY_RUN_INSPECT = "/dry-run-inspect"
        const val ACLS_CREATE_PRINCIPAL = "/create-principal"
        const val ACLS_EDIT_PRINCIPAL = "/edit-principal"
        const val ACLS_EDIT_PRINCIPAL_ON_BRANCH = "/edit-principal-on-branch"
        const val ACLS_SUGGESTED_EDIT_PRINCIPAL = "/suggested-edit-principal"
        const val ACLS_IMPORT_PRINCIPAL = "/import-principal"
        const val ACLS_DELETE_PRINCIPAL = "/delete-principal"
        const val ACLS_BULK_CREATE_PRINCIPAL_RULES = "/management/bulk-create-principal-rules"
        const val ACLS_BULK_CREATE_CLUSTER_RULES = "/management/bulk-create-cluster-rules"
        const val ACLS_CREATE_RULES = "/management/create-rules"
        const val ACLS_DELETE_RULES = "/management/delete-rules"
        const val ACLS_BULK_DELETE_RULES = "/management/bulk-delete-rules"
    }

    private val showAll = Url(base)
    private val showPrincipalsTable = Url("$base$ACLS_PRINCIPALS_TABLE")
    private val showAllPrincipalAcls = Url("$base$ACLS_PRINCIPAL", listOf("principal", "rule", "clusterIdentifier"))
    private val showPrincipalHistory = Url("$base$ACLS_PRINCIPAL_HISTORY", listOf("principal"))
    private val showDryRunInspect = Url("$base$ACLS_DRY_RUN_INSPECT")
    private val showCreatePrincipal = Url("$base$ACLS_CREATE_PRINCIPAL")
    private val showEditPrincipal = Url("$base$ACLS_EDIT_PRINCIPAL", listOf("principal"))
    private val showEditPrincipalOnBranch = Url("$base$ACLS_EDIT_PRINCIPAL_ON_BRANCH", listOf("principal", "branch"))
    private val showSuggestedEditPrincipal = Url("$base$ACLS_SUGGESTED_EDIT_PRINCIPAL", listOf("principal"))
    private val showImportPrincipal = Url("$base$ACLS_IMPORT_PRINCIPAL", listOf("principal"))
    private val showDeletePrincipal = Url("$base$ACLS_DELETE_PRINCIPAL", listOf("principal"))
    private val showBulkCreatePrincipalRules = Url("$base$ACLS_BULK_CREATE_PRINCIPAL_RULES", listOf("principal"))
    private val showBulkCreateClusterRules = Url("$base$ACLS_BULK_CREATE_CLUSTER_RULES", listOf("clusterIdentifier"))
    private val showCreatePrincipalRules = Url("$base$ACLS_CREATE_RULES", listOf("principal", "clusterIdentifier", "rule"))
    private val showDeletePrincipalRules = Url("$base$ACLS_DELETE_RULES", listOf("principal", "clusterIdentifier", "rule"))
    private val showBulkDeletePrincipalRules = Url("$base$ACLS_BULK_DELETE_RULES", listOf("principal"))

    fun showAll() = showAll.render()

    fun showPrincipalsTable() = showPrincipalsTable.render()

    fun showAllPrincipalAcls(principal: PrincipalId) = showAllPrincipalAcls(principal, null, null)
    fun showAllPrincipalAclsRule(principal: PrincipalId, selectedRule: String) = showAllPrincipalAcls(principal, selectedRule, null)
    fun showAllPrincipalAclsCluster(principal: PrincipalId, selectedClusterIdentifier: KafkaClusterIdentifier) = showAllPrincipalAcls(principal, null, selectedClusterIdentifier)

    fun showAllPrincipalAcls(
        principal: PrincipalId,
        selectedRule: String?,
        selectedClusterIdentifier: KafkaClusterIdentifier?
    ) = showAllPrincipalAcls.render(
            "principal" to principal,
            "rule" to selectedRule,
            "clusterIdentifier" to selectedClusterIdentifier
    )

    fun showPrincipalHistory(principal: PrincipalId) = showPrincipalHistory.render("principal" to principal)

    fun showDryRunInspect() = showDryRunInspect.render()

    fun showCreatePrincipal() = showCreatePrincipal.render()

    fun showEditPrincipal(principal: PrincipalId) = showEditPrincipal.render("principal" to principal)

    fun showEditPrincipalOnBranch(
        principal: PrincipalId, branch: String
    ) = showEditPrincipalOnBranch.render("principal" to principal, "branch" to branch)

    fun showSuggestedEditPrincipal(principal: PrincipalId) = showSuggestedEditPrincipal.render("principal" to principal)

    fun showImportPrincipal(principal: PrincipalId) = showImportPrincipal.render("principal" to principal)

    fun showDeletePrincipal(principal: PrincipalId) = showDeletePrincipal.render("principal" to principal)

    fun showBulkCreatePrincipalRules(principal: PrincipalId) = showBulkCreatePrincipalRules.render("principal" to principal)

    fun showBulkCreateClusterRules(clusterIdentifier: KafkaClusterIdentifier) = showBulkCreateClusterRules.render("clusterIdentifier" to clusterIdentifier)

    @JvmOverloads
    fun showCreatePrincipalRules(
        principal: PrincipalId,
        clusterIdentifier: KafkaClusterIdentifier,
        rule: String? = null
    ) = showCreatePrincipalRules.render(
            "principal" to principal,
            "clusterIdentifier" to clusterIdentifier,
            "rule" to rule
    )

    @JvmOverloads
    fun showDeletePrincipalRules(
        principal: PrincipalId,
        clusterIdentifier: KafkaClusterIdentifier,
        rule: String? = null
    ) = showDeletePrincipalRules.render(
            "principal" to principal,
            "clusterIdentifier" to clusterIdentifier,
            "rule" to rule
    )

    fun showBulkDeletePrincipalRules(principal: PrincipalId) = showBulkDeletePrincipalRules.render("principal" to principal)

}