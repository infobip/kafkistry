package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue
import com.infobip.kafkistry.service.eachCountDescending
import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.ACLS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.CLUSTER_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.CONSUMER_GROUPS_STATS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.HOME
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.PENDING_REQUESTS
import com.infobip.kafkistry.webapp.url.MainUrls.Companion.QUOTAS_STATS
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
        val clustersStatuses = inspectApi.inspectClustersStatuses()
        val clustersStats = clustersStatuses.groupingBy { it.clusterState }.eachCountDescending()
        val issuesStats = clustersStatuses
            .filter { it.clusterState != StateType.DISABLED }
            .map { it.cluster.identifier }
            .flatMap { clusterIdentifier ->
                try {
                    inspectApi.inspectClusterIssues(clusterIdentifier).distinctBy { it.name }
                } catch (ex: KafkistryException) {
                    listOf(
                        ClusterInspectIssue(
                            name = "INSPECT_ERROR",
                            RuleViolation("", RuleViolation.Severity.ERROR, ex.deepToString())
                        )
                    )
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

    @GetMapping(TOPIC_STATS)
    fun showTopicsStats(): ModelAndView {
        val topicsStats = (inspectApi.inspectTopics() + inspectApi.inspectUnknownTopics())
            .asSequence()
            .flatMap { it.statusPerClusters }
            .flatMap { it.status.types }
            .groupingBy { it }
            .eachCountDescending()
        return ModelAndView(
            "home/topicsStats", mutableMapOf(
                "topicsStats" to topicsStats,
            )
        )
    }

    @GetMapping(CONSUMER_GROUPS_STATS)
    fun showConsumerGroupsStats(): ModelAndView {
        val consumersStats = consumersApi.allConsumersData().consumersStats
        return ModelAndView(
            "home/consumerGroupsStats", mutableMapOf(
                "consumersStats" to consumersStats,
            )
        )
    }

    @GetMapping(ACLS_STATS)
    fun showAclsStats(): ModelAndView {
        val aclsStats = inspectApi.inspectAllPrincipals()
            .asSequence()
            .flatMap { it.clusterInspections }
            .flatMap { it.statuses }
            .groupingBy { it.statusType }
            .eachCountDescending()
        return ModelAndView(
            "home/aclsStats", mutableMapOf(
                "aclsStats" to aclsStats,
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