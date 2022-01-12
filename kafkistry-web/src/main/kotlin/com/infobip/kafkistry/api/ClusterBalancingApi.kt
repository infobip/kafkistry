package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.generator.balance.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/clusters/balancing")
class ClusterBalancingApi(
    private val globalBalancerService: GlobalBalancerService
) {

    @GetMapping("/status")
    fun getStatus(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ClusterBalanceStatus = globalBalancerService.getCurrentBalanceStatus(clusterIdentifier)

    @PostMapping("/propose-migrations")
    fun getProposedMigrations(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestBody balanceSettings: BalanceSettings
    ): ProposedMigrations = globalBalancerService.proposeMigrations(clusterIdentifier, balanceSettings)

    @GetMapping("/global-state")
    fun getState(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): GlobalState = globalBalancerService.getCurrentGlobalState(clusterIdentifier)

}