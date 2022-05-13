package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.service.history.ChangeCommit
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.service.quotas.QuotasChange
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.quotas.QuotasRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/quotas")
class QuotasApi(
    private val quotasRegistry: QuotasRegistryService
) {

    @PostMapping
    fun createQuotas(
        @RequestBody quotaDescription: QuotaDescription,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = quotasRegistry.createQuotas(quotaDescription, UpdateContext(message, targetBranch))

    @DeleteMapping
    fun deleteQuotas(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = quotasRegistry.deleteQuotas(entityID, UpdateContext(message, targetBranch))

    @PutMapping
    fun updateQuotas(
        @RequestBody quotaDescription: QuotaDescription,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = quotasRegistry.updateQuotas(quotaDescription, UpdateContext(message, targetBranch))

    @GetMapping("/single")
    fun getQuotas(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID
    ): QuotaDescription = quotasRegistry.getQuotas(entityID)

    @GetMapping("/single/changes")
    fun getEntityQuotaChanges(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): List<QuotasRequest> = quotasRegistry.getChanges(entityID)

    @GetMapping
    fun listQuotas(): List<QuotaDescription> = quotasRegistry.listAllQuotas()

    @GetMapping("/pending-requests")
    fun pendingQuotasRequests(): Map<QuotaEntityID, List<QuotasRequest>> = quotasRegistry.findAllPendingRequests()

    @GetMapping("/single/pending-requests")
    fun pendingQuotasRequests(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): List<QuotasRequest> = quotasRegistry.findPendingRequests(entityID)

    @GetMapping("/single/pending-requests/branch")
    fun pendingEntityQuotaRequest(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
        @RequestParam("branch") branch: String,
    ): QuotasRequest = quotasRegistry.pendingRequest(entityID, branch)

    @GetMapping("/history")
    fun commitsHistory(): List<ChangeCommit<QuotasChange>> = quotasRegistry.getCommitsHistory()

}