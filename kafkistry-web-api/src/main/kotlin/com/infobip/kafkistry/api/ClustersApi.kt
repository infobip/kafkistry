package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.ConnectionDefinition
import com.infobip.kafkistry.kafkastate.BaseKafkaStateProvider
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.KafkaProfile
import com.infobip.kafkistry.repository.storage.Branch
import com.infobip.kafkistry.service.history.ClusterRequest
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.service.cluster.TagClusters
import com.infobip.kafkistry.service.history.BranchRequests
import org.springframework.web.bind.annotation.*

/**
 * CRUD operations on tracked kafka clusters repository
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/clusters")
class ClustersApi(
    private val clustersRegistryService: ClustersRegistryService,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val stateProviders: List<BaseKafkaStateProvider>
) {

    @PostMapping
    fun addCluster(
        @RequestBody cluster: KafkaCluster,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = clustersRegistryService.addCluster(cluster, UpdateContext(message, targetBranch))

    @DeleteMapping
    fun removeCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = clustersRegistryService.removeCluster(clusterIdentifier, UpdateContext(message, targetBranch))

    @PutMapping
    fun updateCluster(
        @RequestBody cluster: KafkaCluster,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = clustersRegistryService.updateCluster(cluster, UpdateContext(message, targetBranch))

    @PutMapping("/bulk")
    fun updateClusters(
        @RequestBody clusters: List<KafkaCluster>,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = clustersRegistryService.updateClusters(clusters, UpdateContext(message, targetBranch))

    @GetMapping
    fun listClusters(): List<KafkaCluster> = clustersRegistryService.listClusters()

    @GetMapping("/single")
    fun getCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): KafkaCluster = clustersRegistryService.getCluster(clusterIdentifier)

    @GetMapping("/tags")
    fun allTags(): List<TagClusters> = clustersRegistryService.listAllTagClusters()

    @GetMapping("/single/cluster-state")
    fun getClusterState(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): StateData<KafkaClusterState> = kafkaStateProvider.getLatestClusterState(clusterIdentifier)

    @GetMapping("/test-connection")
    fun testClusterConnection(
        @RequestParam("connectionString") connectionString: String,
        @RequestParam("ssl") ssl: Boolean,
        @RequestParam("sasl") sasl: Boolean,
        @RequestParam("profiles") profiles: List<KafkaProfile>,
    ): ClusterInfo = clustersRegistryService.testClusterConnectionReadInfo(
        ConnectionDefinition(connectionString, ssl, sasl, profiles)
    )

    @PostMapping("/refresh")
    fun refreshClusters() {
        stateProviders.forEach { it.refreshClustersStates() }
    }

    @PostMapping("/refresh/cluster")
    fun refreshCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ) {
        stateProviders.forEach { it.refreshClusterState(clusterIdentifier) }
    }

    @GetMapping("/pending-requests")
    fun pendingClustersRequests(): Map<KafkaClusterIdentifier, List<ClusterRequest>> =
        clustersRegistryService.findAllPendingRequests()

    @GetMapping("/pending-requests/branch")
    fun pendingClustersBranchRequests(
        @RequestParam("branch") branch: Branch,
    ): List<ClusterRequest> = clustersRegistryService.pendingBranchRequests(branch)

    @GetMapping("/pending-requests/branches")
    fun pendingClustersBranchesRequests(): List<BranchRequests<ClusterRequest>> =
        clustersRegistryService.pendingBranches()

    @GetMapping("/single/pending-requests")
    fun pendingClustersRequests(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): List<ClusterRequest> = clustersRegistryService.findPendingRequests(clusterIdentifier)

    @GetMapping("/single/pending-requests/branch")
    fun pendingClusterRequest(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("branch") branch: Branch,
    ): ClusterRequest = clustersRegistryService.pendingRequest(clusterIdentifier, branch)

}