package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.generator.balance.BalanceSettings
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_ADD
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_BALANCE
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_DRY_RUN_INSPECT
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_EDIT
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_EDIT_ON_BRANCH
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INCREMENTAL_BALANCING
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INCREMENTAL_BALANCING_SUGGESTION
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT_ACLS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT_BRIEF
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT_CONSUMER_GROUPS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT_QUOTAS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_INSPECT_TOPICS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_ISSUES
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_REMOVE
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS_RESOURCES
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$CLUSTERS")
class ClustersController(
    private val clustersApi: ClustersApi,
    private val inspectApi: InspectApi,
    private val consumersApi: ConsumersApi,
    private val clusterBalancingApi: ClusterBalancingApi,
    private val resourcesAnalyzerApi: ResourceAnalyzerApi,
    private val existingValuesApi: ExistingValuesApi,
) : BaseController() {

    @GetMapping
    fun showClusters(): ModelAndView {
        val clustersStatuses = inspectApi.inspectClustersStatuses()
        val pendingClustersRequests = clustersApi.pendingClustersRequests()
        return ModelAndView("clusters/all", mutableMapOf(
                "clustersStatuses" to clustersStatuses,
                "pendingClustersUpdates" to pendingClustersRequests
        ))
    }

    @GetMapping(CLUSTERS_ADD)
    fun showAddCluster(): ModelAndView {
        val existingValues = existingValuesApi.all()
        return ModelAndView("clusters/add", mapOf(
            "existingValues" to existingValues
        ))
    }

    @GetMapping(CLUSTERS_EDIT)
    fun showEditCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val cluster = clustersApi.getCluster(clusterIdentifier)
        val existingValues = existingValuesApi.all()
        return ModelAndView("clusters/edit", mapOf(
            "cluster" to cluster,
            "existingValues" to existingValues
        ))
    }

    @GetMapping(CLUSTERS_EDIT_ON_BRANCH)
    fun showEditTopicOnBranch(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("branch") branch: String
    ): ModelAndView {
        val clusterRequest = clustersApi.pendingClusterRequest(clusterIdentifier, branch)
        val clusterExists = clustersApi.listClusters().any { it.identifier == clusterIdentifier }
        val existingValues = existingValuesApi.all()
        return ModelAndView("clusters/editOnBranch", mutableMapOf(
            "title" to "Edit pending cluster request",
            "clusterRequest" to clusterRequest,
            "existingValues" to existingValues,
            "branch" to branch,
            "clusterSourceType" to "BRANCH_EDIT",
            "clusterExists" to clusterExists,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT)
    fun showCluster(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterStatus = inspectApi.inspectClusterStatus(clusterIdentifier)
        val pendingClusterRequests = clustersApi.pendingClustersRequests(clusterIdentifier)
        return ModelAndView("clusters/cluster", mutableMapOf(
            "clusterStatus" to clusterStatus,
            "pendingClusterRequests" to pendingClusterRequests,
            "brokerConfigDoc" to existingValuesApi.all().brokerConfigDoc,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT_BRIEF)
    fun showClusterBriefInspect(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterTopics = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
        val clusterAcls = inspectApi.inspectClusterAcls(clusterIdentifier)
        val clusterQuotas = inspectApi.inspectClusterQuotas(clusterIdentifier)
        val clusterGroups = consumersApi.clusterConsumerGroups(clusterIdentifier)
        val clusterIssues = inspectApi.inspectClusterIssues(clusterIdentifier)
        return ModelAndView("clusters/clusterBriefInspect", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterTopics" to clusterTopics,
            "clusterAcls" to clusterAcls,
            "clusterQuotas" to clusterQuotas,
            "clusterGroups" to clusterGroups,
            "clusterIssues" to clusterIssues,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT_TOPICS)
    fun showClusterTopics(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterTopics = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
        return ModelAndView("clusters/clusterTopics", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterTopics" to clusterTopics,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT_ACLS)
    fun showClusterAcls(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterAcls = inspectApi.inspectClusterAcls(clusterIdentifier)
        return ModelAndView("clusters/clusterAcls", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterAcls" to clusterAcls,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT_QUOTAS)
    fun showClusterQuotas(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterQuotas = inspectApi.inspectClusterQuotas(clusterIdentifier)
        return ModelAndView("clusters/clusterQuotas", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterQuotas" to clusterQuotas,
        ))
    }

    @GetMapping(CLUSTERS_INSPECT_CONSUMER_GROUPS)
    fun showClusterConsumerGroups(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterGroups = consumersApi.clusterConsumerGroups(clusterIdentifier)
        return ModelAndView("clusters/clusterConsumerGroups", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterGroups" to clusterGroups,
        ))
    }

    @GetMapping(CLUSTERS_REMOVE)
    fun showRemoveCluster(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val cluster = clustersApi.getCluster(clusterIdentifier)
        return ModelAndView("clusters/remove", mutableMapOf(
                "cluster" to cluster
        ))
    }

    @GetMapping(CLUSTERS_BALANCE)
    fun showClusterBalance(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val balanceStatus = clusterBalancingApi.getStatus(clusterIdentifier)
        return ModelAndView("clusters/balance", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "balanceStatus" to balanceStatus,
        ))
    }

    @GetMapping(CLUSTERS_INCREMENTAL_BALANCING)
    fun showGlobalBalancing(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val balanceStatus = clusterBalancingApi.getStatus(clusterIdentifier)
        return ModelAndView("clusters/incrementalBalancing", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "balanceStatus" to balanceStatus,
        ))
    }

    @PostMapping(CLUSTERS_INCREMENTAL_BALANCING_SUGGESTION)
    fun showGlobalBalancingSuggestion(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestBody balanceSettings: BalanceSettings
    ): ModelAndView {
        val proposedMigrations = clusterBalancingApi.getProposedMigrations(clusterIdentifier, balanceSettings)
        return ModelAndView("clusters/incrementalProposedMigrations", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "proposedMigrations" to proposedMigrations,
        ))
    }

    @GetMapping(CLUSTERS_RESOURCES)
    fun showClusterResources(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterResources = resourcesAnalyzerApi.getClusterStatus(clusterIdentifier)
        return ModelAndView("clusters/resources", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterResources" to clusterResources,
        ))
    }

    @PostMapping(CLUSTERS_DRY_RUN_INSPECT)
    fun showClusterDryRunInspect(
        @RequestBody kafkaCluster: KafkaCluster,
    ): ModelAndView {
        val clusterDryRunInspect = inspectApi.inspectClusterEditTagsDryRun(kafkaCluster)
        return ModelAndView("clusters/dryRunInspect", mutableMapOf(
            "clusterIdentifier" to kafkaCluster.identifier,
            "clusterDryRunInspect" to clusterDryRunInspect,
        ))
    }

    @GetMapping(CLUSTERS_ISSUES)
    fun showClusterIssues(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterIssues = inspectApi.inspectClusterIssues(clusterIdentifier)
        return ModelAndView("clusters/issues", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterIssues" to clusterIssues,
        ))
    }

}