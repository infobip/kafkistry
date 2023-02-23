package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.repository.storage.Branch
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
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.TAGS
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.TAGS_ON_BRANCH
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$CLUSTERS")
class ClustersController(
    private val clustersApi: ClustersApi,
    private val topicsApi: TopicsApi,
    private val inspectApi: InspectApi,
    private val consumersApi: ConsumersApi,
    private val autopilotApi: AutopilotApi,
    private val clusterBalancingApi: ClusterBalancingApi,
    private val resourcesAnalyzerApi: ResourceAnalyzerApi,
    private val existingValuesApi: ExistingValuesApi,
    private val clusterEnabledFilter: ClusterEnabledFilter,
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
        @RequestParam("branch") branch: Branch
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
        val autopilotActions = autopilotApi.findClusterActions(clusterIdentifier)
        return ModelAndView("clusters/cluster", mutableMapOf(
            "clusterStatus" to clusterStatus,
            "pendingClusterRequests" to pendingClusterRequests,
            "brokerConfigDoc" to existingValuesApi.all().brokerConfigDoc,
            "autopilotActions" to autopilotActions,
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
        val topicNames =  inspectApi.inspectTopicsOnCluster(clusterIdentifier)
            .statusPerTopics
            ?.filter { it.existingTopicInfo != null }
            ?.map { it.topicName }
            .orEmpty()
        return ModelAndView("clusters/incrementalBalancing", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "balanceStatus" to balanceStatus,
                "topicNames" to topicNames,
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

    @GetMapping(TAGS)
    fun showTagsPage(): ModelAndView = tagsPage()

    @GetMapping(TAGS_ON_BRANCH)
    fun showTagsOnBranchPage(
        @RequestParam("branch") branch: Branch
    ): ModelAndView {
        val branchChangedClusterRefs = clustersApi.pendingClustersBranchRequests(branch)
            .mapNotNull { it.cluster?.ref() }
        val currentClusterRefs = clustersApi.listClusters().map { it.ref() }
        val branchClusterRefs = branchChangedClusterRefs + currentClusterRefs.filterNot { ref ->
            branchChangedClusterRefs.any { it.identifier == ref.identifier }
        }
        val branchTagClusters = branchClusterRefs
            .flatMap { it.tags.map { tag -> tag to it.identifier } }
            .groupBy({ it.first }, { it.second })
        return tagsPage(mapOf(
            "branchTagClusters" to branchTagClusters,
            "branch" to branch,
        ))
    }

    private fun tagsPage(extraModel: Map<String, Any?> = emptyMap()): ModelAndView {
        val allTags = clustersApi.allTags()
        val tagTopics = topicsApi.listTopics()
            .mapNotNull { topic ->
                topic.presence.tag?.let { it to topic.name }
            }
            .groupBy({ it.first }, { it.second })
        val clusters = clustersApi.listClusters()
        val enabledClusterIdentifiers = clusters.asSequence()
            .map { it.ref() }
            .filter(clusterEnabledFilter)
            .map { it.identifier }
            .toList()
        val pendingClustersRequests = clustersApi.pendingClustersRequests()
        val pendingBranches = clustersApi.pendingClustersBranchesRequests()
        return ModelAndView("clusters/tags", mapOf(
            "allTags" to allTags,
            "tagTopics" to tagTopics,
            "clusters" to clusters,
            "enabledClusterIdentifiers" to enabledClusterIdentifiers,
            "pendingClustersRequests" to pendingClustersRequests,
            "pendingBranches" to pendingBranches,
        ) + extraModel)
    }


}
