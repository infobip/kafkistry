package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.ownership.UserOwnershipClassifier
import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.acl.PrincipalAclsInspection
import com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue
import com.infobip.kafkistry.service.consumers.ClusterConsumerGroup
import com.infobip.kafkistry.service.consumers.ConsumersStats
import com.infobip.kafkistry.service.consumers.computeStats
import com.infobip.kafkistry.service.eachCountDescending
import com.infobip.kafkistry.service.sortedByValueDescending
import com.infobip.kafkistry.service.topic.TopicStatuses
import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.ACLS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.CLUSTER_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.CONSUMER_GROUPS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.HOME
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.PENDING_REQUESTS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.QUOTAS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.TAGS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.TOPIC_STATS
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}")
class MainPageController(
    private val topicsApi: TopicsApi,
    private val clustersApi: ClustersApi,
    private val aclsApi: AclsApi,
    private val quotasApi: QuotasApi,
    private val inspectApi: InspectApi,
    private val consumersApi: ConsumersApi,
    private val ownershipClassifier: UserOwnershipClassifier,
) : BaseController() {

    @GetMapping("/", HOME)
    fun showRegistry(): ModelAndView {
        return ModelAndView("home/registry")
    }

    @GetMapping(PENDING_REQUESTS)
    fun showPendingRequests(): ModelAndView {
        return ModelAndView(
            "home/pendingRequests", mutableMapOf(
                "pendingTopicsRequests" to topicsApi.pendingTopicsRequests(),
                "pendingClustersRequests" to clustersApi.pendingClustersRequests(),
                "pendingPrincipalRequests" to aclsApi.pendingPrincipalRequests(),
                "pendingQuotasRequests" to quotasApi.pendingQuotasRequests(),
            )
        )
    }

    @GetMapping(CLUSTER_STATS)
    fun showClustersStats(): ModelAndView {
        fun newIssue(name: String, severity: RuleViolation.Severity, message: String = "", doc: String) = ClusterInspectIssue(
            name = name, violation = RuleViolation("", severity, message), doc = doc,
        )
        val clustersStatuses = inspectApi.inspectClustersStatuses()
        val clustersStats = clustersStatuses.groupingBy { it.clusterState }.eachCountDescending()
        val issuesStats = clustersStatuses
            .filter { it.clusterState != StateType.DISABLED }
            .map { it.cluster.identifier }
            .flatMap { clusterIdentifier ->
                try {
                    inspectApi.inspectClusterIssues(clusterIdentifier).distinctBy { it.name }
                        .takeIf { it.isNotEmpty() }
                        ?: listOf(newIssue("NO_ISSUES", RuleViolation.Severity.NONE, doc = "No cluster issues found"))
                } catch (ex: KafkistryException) {
                    listOf(newIssue(
                        "INSPECT_ERROR", RuleViolation.Severity.CRITICAL, ex.deepToString(),
                        doc = "Indicates runtime error during analyzing cluster issues."
                    ))
                }
            }
            .groupingBy { it.copy(violation = it.violation.copy(message = "", placeholders = emptyMap())) }
            .eachCountDescending()
        return ModelAndView(
            "home/clustersStats", mutableMapOf(
                "clustersStats" to clustersStats,
                "issuesStats" to issuesStats,
            )
        )
    }

    @GetMapping(TAGS_STATS)
    fun showTagsStats(): ModelAndView {
        val allTags = clustersApi.allTags()
        val tagsCounts = allTags
            .associate { (tag, clusters) -> tag to clusters.size }
            .sortedByValueDescending()
        return ModelAndView(
            "home/tagsStats", mutableMapOf(
                "tagsCounts" to tagsCounts,
            )
        )
    }

    @GetMapping(TOPIC_STATS)
    fun showTopicsStats(): ModelAndView {
        fun List<TopicStatuses>.computeStats() = asSequence()
            .flatMap { it.statusPerClusters }
            .flatMap { it.status.types }
            .groupingBy { it }
            .eachCountDescending()
        val allTopicsInspections = inspectApi.inspectTopics() + inspectApi.inspectUnknownTopics()
        val topicsStats = allTopicsInspections.computeStats()
        val ownedTopicsStats = allTopicsInspections
            .filter { ownershipClassifier.isOwnerOfTopic(it.topicDescription) }
            .computeStats()
        return ModelAndView(
            "home/topicsStats", mutableMapOf(
                "topicsStats" to topicsStats,
                "ownedTopicsStats" to ownedTopicsStats,
            )
        )
    }

    @GetMapping(CONSUMER_GROUPS_STATS)
    fun showConsumerGroupsStats(): ModelAndView {
        val allConsumersInspections = consumersApi.allConsumersData().clustersGroups
        val consumersStats = allConsumersInspections.computeStats()
        val ownedConsumersStats = allConsumersInspections
            .filter { ownershipClassifier.isOwnerOfConsumerGroup(it.consumerGroup.groupId) }
            .computeStats()
        return ModelAndView(
            "home/consumerGroupsStats", mutableMapOf(
                "consumersStats" to consumersStats,
                "ownedConsumersStats" to ownedConsumersStats,
            )
        )
    }

    @GetMapping(ACLS_STATS)
    fun showAclsStats(): ModelAndView {
        fun List<PrincipalAclsInspection>.computeStats() = asSequence()
            .flatMap { it.clusterInspections }
            .flatMap { it.statuses }
            .flatMap { it.statusTypes }
            .groupingBy { it }
            .eachCountDescending()
        val allAclsInspections = inspectApi.inspectAllPrincipals() + inspectApi.inspectUnknownPrincipals()
        val aclsStats = allAclsInspections.computeStats()
        val ownedAclsStats = allAclsInspections
            .filter { ownershipClassifier.isOwnerOfPrincipal(it.principalAcls) }
            .computeStats()
        return ModelAndView(
            "home/aclsStats", mutableMapOf(
                "aclsStats" to aclsStats,
                "ownedAclsStats" to ownedAclsStats,
            )
        )
    }

    @GetMapping(QUOTAS_STATS)
    fun showQuotasStats(): ModelAndView {
        val quotasStats = (inspectApi.inspectAllQuotaEntities() + inspectApi.inspectUnknownQuotaEntities())
            .asSequence()
            .flatMap { it.clusterInspections }
            .groupingBy { it.statusType }
            .eachCountDescending()
        return ModelAndView(
            "home/quotasStats", mutableMapOf(
                "quotasStats" to quotasStats,
            )
        )
    }

}