package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.quotas.AvailableQuotasOperation
import com.infobip.kafkistry.service.quotas.QuotasInspection
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_CREATE_ENTITY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_DELETE_ENTITY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_EDIT_ENTITY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_EDIT_ENTITY_ON_BRANCH
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_ENTITY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_ENTITY_HISTORY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_IMPORT_ENTITY
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_BULK_CREATE_CLUSTER_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_BULK_CREATE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_BULK_DELETE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_BULK_UPDATE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_CREATE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_DELETE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_MANAGEMENT_UPDATE_ENTITY_QUOTAS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS_SUGGESTED_EDIT_ENTITY
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$QUOTAS")
class QuotasController(
    private val inspectApi: InspectApi,
    private val suggestionApi: SuggestionApi,
    private val quotasApi: QuotasApi,
    private val existingValuesApi: ExistingValuesApi,
) : BaseController() {

    @GetMapping
    fun showAll(): ModelAndView {
        val quotaEntitiesInspection = inspectApi.inspectAllQuotaEntities()
        val unknownQuotaEntitiesInspection = inspectApi.inspectUnknownQuotaEntities()
        val quotaEntities = quotaEntitiesInspection + unknownQuotaEntitiesInspection
        val pendingQuotaRequests = quotasApi.pendingQuotasRequests()
        return ModelAndView(
            "quotas/entities", mapOf(
                "quotaEntities" to quotaEntities,
                "pendingQuotaRequests" to pendingQuotaRequests
            )
        )
    }

    @GetMapping(QUOTAS_ENTITY)
    fun showEntity(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val entityInspection = inspectApi.inspectEntityOnClusters(quotaEntityID)
        val pendingEntityQuotasRequests = quotasApi.pendingQuotasRequests(quotaEntityID)
        return ModelAndView(
            "quotas/entity", mapOf(
                "entityInspection" to entityInspection,
                "pendingEntityQuotasRequests" to pendingEntityQuotasRequests,
            )
        )
    }

    @GetMapping(QUOTAS_ENTITY_HISTORY)
    fun showEntityHistory(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val historyChanges = quotasApi.getEntityQuotaChanges(quotaEntityID)
        return ModelAndView("git/entityHistory", mapOf("historyChangeRequests" to historyChanges))
    }

    @GetMapping(QUOTAS_CREATE_ENTITY)
    fun showCreateEntity(): ModelAndView {
        return ModelAndView(
            "quotas/modify/createEntityQuotas", mapOf(
                "existingValues" to existingValuesApi.all(),
                "entitySourceType" to "NEW"
            )
        )
    }

    @GetMapping(QUOTAS_EDIT_ENTITY)
    fun showEditEntity(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val entityQuotas = quotasApi.getQuotas(quotaEntityID)
        return ModelAndView(
            "quotas/modify/editEntityQuotas", mapOf(
                "title" to "Edit entity quotas",
                "existingValues" to existingValuesApi.all(),
                "entitySourceType" to "EDIT",
                "entityQuotas" to entityQuotas
            )
        )
    }

    @GetMapping(QUOTAS_EDIT_ENTITY_ON_BRANCH)
    fun showEditEntityOnBranch(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
        @RequestParam("branch") branch: String,
    ): ModelAndView {
        val quotaEntity = QuotaEntity.fromID(quotaEntityID)
        val entityQuotaRequest = quotasApi.pendingEntityQuotaRequest(quotaEntityID, branch)
        val quotaEntityExists = quotasApi.listQuotas().any { it.entity == quotaEntity }
        val existingValues = existingValuesApi.all()
        return ModelAndView(
            "quotas/modify/editEntityQuotasOnBranch", mutableMapOf(
                "title" to "Edit pending entity quotas request",
                "entityQuotaRequest" to entityQuotaRequest,
                "existingValues" to existingValues,
                "branch" to branch,
                "entitySourceType" to "BRANCH_EDIT",
                "quotaEntityExists" to quotaEntityExists
            )
        )
    }

    @GetMapping(QUOTAS_DELETE_ENTITY)
    fun showDeleteEntity(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val entityQuotas = quotasApi.getQuotas(quotaEntityID)
        return ModelAndView(
            "quotas/modify/deleteEntityQuotas", mapOf(
                "entityQuotas" to entityQuotas
            )
        )
    }

    @GetMapping(QUOTAS_IMPORT_ENTITY)
    fun showImportEntity(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val entityQuotas = suggestionApi.suggestEntityQuotasImport(quotaEntityID)
        return ModelAndView("quotas/modify/importEntityQuotas", mapOf(
            "existingValues" to existingValuesApi.all(),
            "entitySourceType" to "NEW",
            "entityQuotas" to entityQuotas
        ))
    }

    @GetMapping(QUOTAS_SUGGESTED_EDIT_ENTITY)
    fun showSuggestedEditEntity(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val entityQuotas = suggestionApi.suggestEntityQuotasEdit(quotaEntityID)
        return ModelAndView("quotas/modify/editEntityQuotas", mapOf(
            "title" to "Suggested edit to match current state on clusters",
            "existingValues" to existingValuesApi.all(),
            "entitySourceType" to "EDIT",
            "entityQuotas" to entityQuotas
        ))
    }

    private fun QuotasInspection.assertAvailableOperation(neededOperation: AvailableQuotasOperation, ) {
        if (neededOperation !in availableOperations) {
            throw KafkistryIllegalStateException(
                "Can't perform $neededOperation for entity $entity on cluster '$clusterIdentifier' because status is $statusType"
            )
        }
    }

    @GetMapping(QUOTAS_MANAGEMENT_CREATE_ENTITY_QUOTAS)
    fun showCreateEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityQuotasOnCluster(quotaEntityID, clusterIdentifier)
        inspection.assertAvailableOperation(AvailableQuotasOperation.CREATE_MISSING_QUOTAS)
        val entity = QuotaEntity.fromID(quotaEntityID)
        val quotaProperties = inspection.expectedQuota ?: throw KafkistryIllegalStateException(
            "There is no expected quota for entity $entity to create on cluster '$clusterIdentifier', status: ${inspection.statusType}"
        )
        return ModelAndView(
            "quotas/management/createEntityQuotas", mapOf(
                "entity" to entity,
                "quotaProperties" to quotaProperties,
                "clusterIdentifier" to clusterIdentifier,
            )
        )
    }

    @GetMapping(QUOTAS_MANAGEMENT_UPDATE_ENTITY_QUOTAS)
    fun showUpdateEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityQuotasOnCluster(quotaEntityID, clusterIdentifier)
        val entity = QuotaEntity.fromID(quotaEntityID)
        inspection.assertAvailableOperation(AvailableQuotasOperation.ALTER_WRONG_QUOTAS)
        val oldQuotaProperties = inspection.actualQuota ?: throw KafkistryIllegalStateException(
            "There is no current quota for entity $entity to update on cluster '$clusterIdentifier', status: ${inspection.statusType}"
        )
        val newQuotaProperties = inspection.expectedQuota ?: throw KafkistryIllegalStateException(
            "There is no expected quota for entity $entity to update on cluster '$clusterIdentifier', status: ${inspection.statusType}"
        )
        return ModelAndView(
            "quotas/management/updateEntityQuotas", mapOf(
                "entity" to entity,
                "oldQuotaProperties" to oldQuotaProperties,
                "newQuotaProperties" to newQuotaProperties,
                "clusterIdentifier" to clusterIdentifier,
            )
        )

    }

    @GetMapping(QUOTAS_MANAGEMENT_DELETE_ENTITY_QUOTAS)
    fun showDeleteEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityQuotasOnCluster(quotaEntityID, clusterIdentifier)
        inspection.assertAvailableOperation(AvailableQuotasOperation.DELETE_UNWANTED_QUOTAS)
        val entity = QuotaEntity.fromID(quotaEntityID)
        val quotaProperties = inspection.actualQuota ?: throw KafkistryIllegalStateException(
            "There is no current quota for entity $entity to delete on cluster '$clusterIdentifier'"
        )
        return ModelAndView(
            "quotas/management/deleteEntityQuotas", mapOf(
                "entity" to entity,
                "quotaProperties" to quotaProperties,
                "clusterIdentifier" to clusterIdentifier,
            )
        )
    }


    @GetMapping(QUOTAS_MANAGEMENT_BULK_CREATE_ENTITY_QUOTAS)
    fun showBulkCreateEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityOnClusters(quotaEntityID)
        val missingClusterQuotas = inspection.clusterInspections
            .filter { AvailableQuotasOperation.CREATE_MISSING_QUOTAS in it.availableOperations }
            .associateBy { it.clusterIdentifier }
        return ModelAndView("quotas/management/bulkCreateMissingEntityQuotas", mapOf(
            "entity" to inspection.entity,
            "missingClusterQuotas" to missingClusterQuotas
        ))
    }

    @GetMapping(QUOTAS_MANAGEMENT_BULK_UPDATE_ENTITY_QUOTAS)
    fun showBulkUpdateEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityOnClusters(quotaEntityID)
        val wrongClusterQuotas = inspection.clusterInspections
            .filter { AvailableQuotasOperation.ALTER_WRONG_QUOTAS in it.availableOperations }
            .associateBy { it.clusterIdentifier }
        return ModelAndView("quotas/management/bulkAlterWrongEntityQuotas", mapOf(
            "entity" to inspection.entity,
            "wrongClusterQuotas" to wrongClusterQuotas
        ))
    }

    @GetMapping(QUOTAS_MANAGEMENT_BULK_DELETE_ENTITY_QUOTAS)
    fun showBulkDeleteEntityQuotas(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID,
    ): ModelAndView {
        val inspection = inspectApi.inspectEntityOnClusters(quotaEntityID)
        val unwantedClusterQuotas = inspection.clusterInspections
            .filter { AvailableQuotasOperation.DELETE_UNWANTED_QUOTAS in it.availableOperations }
            .associateBy { it.clusterIdentifier }
        return ModelAndView("quotas/management/bulkDeleteUnwantedEntityQuotas", mapOf(
            "entity" to inspection.entity,
            "unwantedClusterQuotas" to unwantedClusterQuotas
        ))
    }


    @GetMapping(QUOTAS_MANAGEMENT_BULK_CREATE_CLUSTER_QUOTAS)
    fun showBulkCreateClusterQuotas(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ModelAndView {
        val inspection = inspectApi.inspectClusterQuotas(clusterIdentifier)
        val missingEntityQuotas = inspection.entityInspections
            .filter { AvailableQuotasOperation.CREATE_MISSING_QUOTAS in it.availableOperations }
            .associateBy { it.entity }
        return ModelAndView("quotas/management/bulkCreateClusterMissingEntityQuotas", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "missingEntityQuotas" to missingEntityQuotas
        ))
    }

}