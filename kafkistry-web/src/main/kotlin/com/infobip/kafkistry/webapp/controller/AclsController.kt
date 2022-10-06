package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.AclsApi
import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.api.SuggestionApi
import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AvailableAclOperation
import com.infobip.kafkistry.service.acl.transpose
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_BULK_CREATE_CLUSTER_RULES
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_BULK_CREATE_PRINCIPAL_RULES
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_BULK_DELETE_RULES
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_CREATE_PRINCIPAL
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_CREATE_RULES
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_DELETE_PRINCIPAL
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_DELETE_RULES
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_DRY_RUN_INSPECT
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_EDIT_PRINCIPAL
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_EDIT_PRINCIPAL_ON_BRANCH
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_IMPORT_PRINCIPAL
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_PRINCIPAL
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_PRINCIPAL_HISTORY
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS_SUGGESTED_EDIT_PRINCIPAL
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$ACLS")
class AclsController(
        private val inspectApi: InspectApi,
        private val suggestionApi: SuggestionApi,
        private val aclsApi: AclsApi,
        private val existingValuesApi: ExistingValuesApi
) : BaseController() {

    @GetMapping
    fun showAll(): ModelAndView {
        val principalsInspection = inspectApi.inspectAllPrincipals()
        val unknownPrincipalsInspection = inspectApi.inspectUnknownPrincipals()
        val principals = (principalsInspection + unknownPrincipalsInspection).map { it.transpose() }
        val pendingPrincipalRequests = aclsApi.pendingPrincipalRequests()
        return ModelAndView("acls/principals", mapOf(
                "principals" to principals,
                "pendingPrincipalRequests" to pendingPrincipalRequests
        ))
    }

    @GetMapping(ACLS_PRINCIPAL)
    fun showAllPrincipalAcls(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam(name = "rule", required = false) selectedRule: String?,
        @RequestParam(name = "clusterIdentifier", required = false) selectedClusterIdentifier: KafkaClusterIdentifier?
    ): ModelAndView {
        val principalInspectionClusterRules = inspectApi.inspectPrincipalAcls(principal)
        val principalInspectionRuleClusters = principalInspectionClusterRules.transpose()
        val pendingPrincipalAclsRequests = aclsApi.pendingPrincipalRequests(principal)
        return ModelAndView("acls/principal", mapOf(
                "principalRuleClusters" to principalInspectionRuleClusters,
                "principalClusterRules" to principalInspectionClusterRules,
                "selectedRule" to selectedRule,
                "selectedCluster" to selectedClusterIdentifier,
                "pendingPrincipalRequests" to pendingPrincipalAclsRequests
        ))
    }

    @PostMapping(ACLS_DRY_RUN_INSPECT)
    fun showDryRunInspect(
        @RequestBody principalAcls: PrincipalAclRules
    ): ModelAndView {
        val principalInspectionClusterRules = inspectApi.inspectPrincipalUpdateDryRun(principalAcls)
        val principalInspectionRuleClusters = principalInspectionClusterRules.transpose()
        return ModelAndView("acls/dryRunInspect", mutableMapOf(
            "principalClusterRules" to principalInspectionClusterRules,
            "principalRuleClusters" to principalInspectionRuleClusters,
        ))
    }

    @GetMapping(ACLS_PRINCIPAL_HISTORY)
    fun showEntityHistory(
        @RequestParam("principal") principal: PrincipalId,
    ): ModelAndView {
        val historyChanges = aclsApi.getPrincipalChanges(principal)
        return ModelAndView("git/entityHistory", mapOf("historyChangeRequests" to historyChanges))
    }

    @GetMapping(ACLS_CREATE_PRINCIPAL)
    fun showCreatePrincipal(): ModelAndView {
        return ModelAndView("acls/modify/createPrincipalAcls", mapOf(
                "existingValues" to existingValuesApi.all(),
                "principalSourceType" to "NEW"
        ))
    }

    @GetMapping(ACLS_EDIT_PRINCIPAL)
    fun showEditPrincipal(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val principalAcls = aclsApi.getPrincipalAcls(principal)
        return ModelAndView("acls/modify/editPrincipalAcls", mapOf(
                "title" to "Edit principal ACLs",
                "existingValues" to existingValuesApi.all(),
                "principalSourceType" to "EDIT",
                "principalAcls" to principalAcls
        ))
    }

    @GetMapping(ACLS_EDIT_PRINCIPAL_ON_BRANCH)
    fun showEditPrincipal(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("branch") branch: String
    ): ModelAndView {
        val aclsRequest = aclsApi.pendingPrincipalRequest(principal, branch)
        val principalExists = aclsApi.listPrincipalAcls().any { it.principal == principal }
        val existingValues = existingValuesApi.all()
        return ModelAndView("acls/modify/editPrincipalAclsOnBranch", mutableMapOf(
                "title" to "Edit pending principal ACLs request",
                "aclsRequest" to aclsRequest,
                "existingValues" to existingValues,
                "branch" to branch,
                "principalSourceType" to "BRANCH_EDIT",
                "principalExists" to principalExists
        ))
    }

    @GetMapping(ACLS_SUGGESTED_EDIT_PRINCIPAL)
    fun showSuggestedEditPrincipal(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val principalAcls = suggestionApi.suggestPrincipalAclsUpdate(principal)
        return ModelAndView("acls/modify/editPrincipalAcls", mapOf(
                "title" to "Suggested edit to match current state on clusters",
                "existingValues" to existingValuesApi.all(),
                "principalSourceType" to "EDIT",
                "principalAcls" to principalAcls
        ))
    }

    @GetMapping(ACLS_IMPORT_PRINCIPAL)
    fun showImportPrincipal(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val principalAcls = suggestionApi.suggestPrincipalAclsImport(principal)
        return ModelAndView("acls/modify/importPrincipalAcls", mapOf(
                "existingValues" to existingValuesApi.all(),
                "principalSourceType" to "NEW",
                "principalAcls" to principalAcls
        ))
    }

    @GetMapping(ACLS_DELETE_PRINCIPAL)
    fun showDeletePrincipal(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val principalAcls = aclsApi.getPrincipalAcls(principal)
        return ModelAndView("acls/modify/deletePrincipal", mapOf(
                "principalAcls" to principalAcls
        ))
    }

    @GetMapping(ACLS_BULK_CREATE_PRINCIPAL_RULES)
    fun showBulkCreatePrincipalRules(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val missingClusterRules = inspectApi.inspectPrincipalAcls(principal)
                .clusterInspections
                .filter { AvailableAclOperation.CREATE_MISSING_ACLS in it.availableOperations }
                .associateBy { it.clusterIdentifier }
                .mapValues { (_, inspection) ->
                    inspection.statuses
                            .filter { AvailableAclOperation.CREATE_MISSING_ACLS in it.availableOperations }
                            .map { it.rule }
                }
        return ModelAndView("acls/management/bulkCreatePrincipalMissingRules", mapOf(
                "principal" to principal,
                "missingClusterRules" to missingClusterRules
        ))
    }

    @GetMapping(ACLS_BULK_CREATE_CLUSTER_RULES)
    fun showBulkCreateClusterRules(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val missingPrincipalRules = inspectApi.inspectClusterAcls(clusterIdentifier)
                .principalAclsInspections
                .filter { AvailableAclOperation.CREATE_MISSING_ACLS in it.availableOperations }
                .associateBy { it.principal }
                .mapValues { (_, inspection) ->
                    inspection.statuses
                            .filter { AvailableAclOperation.CREATE_MISSING_ACLS in it.availableOperations }
                            .map { it.rule }
                }
        return ModelAndView("acls/management/bulkCreateClusterMissingRules", mapOf(
                "clusterIdentifier" to clusterIdentifier,
                "missingPrincipalRules" to missingPrincipalRules
        ))
    }

    @GetMapping(ACLS_CREATE_RULES)
    fun showCreatePrincipalRules(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam(name = "rule", required = false) rule: String?
    ): ModelAndView {
        val missingRules = inspectApi.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
                .statuses
                .filter { AvailableAclOperation.CREATE_MISSING_ACLS in it.availableOperations }
                .map { it.rule }
        val (rulesToCreate, needsForceCreation) = if (rule == null) {
            missingRules to false
        } else {
            val aclRule = rule.parseAcl()
            listOf(aclRule) to (aclRule !in missingRules)
        }
        return ModelAndView("acls/management/createMissingRules", mapOf(
                "principal" to principal,
                "clusterIdentifier" to clusterIdentifier,
                "rules" to rulesToCreate,
                "rule" to rule,
                "needsForceCreation" to needsForceCreation
        ))
    }

    @GetMapping(ACLS_DELETE_RULES)
    fun showDeletePrincipalRules(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam(name = "rule", required = false) rule: String?
    ): ModelAndView {
        val unwantedRules = inspectApi.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)
                .statuses
                .filter { AvailableAclOperation.DELETE_UNWANTED_ACLS in it.availableOperations }
                .map { it.rule }
        val (rulesToDelete, needsForceDeletion) = if (rule == null) {
            unwantedRules to false
        } else {
            val aclRule = rule.parseAcl()
            listOf(aclRule) to (aclRule !in unwantedRules)
        }
        return ModelAndView("acls/management/deleteUnwantedRules", mapOf(
                "principal" to principal,
                "clusterIdentifier" to clusterIdentifier,
                "rules" to rulesToDelete,
                "rule" to rule,
                "needsForceDeletion" to needsForceDeletion
        ))
    }

    @GetMapping(ACLS_BULK_DELETE_RULES)
    fun showBulkDeletePrincipalRules(
            @RequestParam("principal") principal: PrincipalId
    ): ModelAndView {
        val unwantedRules = inspectApi.inspectPrincipalAcls(principal)
                .clusterInspections
                .associate { principalAclsClusterInspection ->
                    principalAclsClusterInspection.clusterIdentifier to principalAclsClusterInspection
                            .statuses
                            .filter { AvailableAclOperation.DELETE_UNWANTED_ACLS in it.availableOperations }
                            .map { it.rule }
                }
                .filterValues { it.isNotEmpty() }
        return ModelAndView("acls/management/bulkDeleteUnwantedRules", mapOf(
                "principal" to principal,
                "unwantedClusterRules" to unwantedRules
        ))
    }


}