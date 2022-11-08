package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.service.quotas.QuotasManagementService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/quotas-management")
class QuotasManagementApi(
    private val quotasManagementService: QuotasManagementService
) {

    @DeleteMapping("/delete-quotas")
    fun deleteClientEntityQuotasOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): Unit = quotasManagementService.deleteUnknownOrUnexpectedEntityQuotas(entityID, clusterIdentifier)

    @PostMapping("/create-quotas")
    fun createClientEntityQuotasOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): Unit = quotasManagementService.createMissingEntityQuotas(entityID, clusterIdentifier)

    @PostMapping("/update-quotas")
    fun updateClientEntityQuotasOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): Unit = quotasManagementService.updateEntityWrongQuotas(entityID, clusterIdentifier)

}