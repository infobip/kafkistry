package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.compare.ComparingRequest
import com.infobip.kafkistry.service.topic.compare.ComparingResult
import com.infobip.kafkistry.service.topic.compare.TopicConfigComparatorService
import com.infobip.kafkistry.service.quotas.ClusterQuotasInspection
import com.infobip.kafkistry.service.quotas.EntityQuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspectionService
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.service.acl.*
import com.infobip.kafkistry.service.cluster.ClusterDryRunInspect
import com.infobip.kafkistry.service.cluster.ClusterEditTagsInspectService
import com.infobip.kafkistry.service.cluster.ClusterStatus
import com.infobip.kafkistry.service.cluster.ClusterStatusService
import com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue
import com.infobip.kafkistry.service.cluster.inspect.ClusterIssuesInspectorService
import com.infobip.kafkistry.service.topic.*
import org.springframework.web.bind.annotation.*

/**
 * Inspection operations.
 * All inspection statuses are computed using "wanted" state written in topics repository
 * against "actual" state on actual kafka clusters
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/inspect")
class InspectApi(
    private val clustersStatusService: ClusterStatusService,
    private val topicsInspectionService: TopicsInspectionService,
    private val topicComparator: TopicConfigComparatorService,
    private val aclsInspectionService: AclsInspectionService,
    private val quotasInspectionService: QuotasInspectionService,
    private val clusterEditTagsInspectService: ClusterEditTagsInspectService,
    private val clusterIssuesInspectorService: ClusterIssuesInspectorService,
) {

    @GetMapping("/topics")
    fun inspectTopics(): List<TopicStatuses> = topicsInspectionService.inspectAllTopics()

    @GetMapping("/topic")
    fun inspectTopic(
        @RequestParam("topicName") topicName: TopicName
    ): TopicStatuses = topicsInspectionService.inspectTopic(topicName)

    @GetMapping("/topic-cluster")
    fun inspectTopicOnCluster(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): TopicClusterStatus = topicsInspectionService.inspectTopicOnCluster(topicName, clusterIdentifier)

    @GetMapping("/topic-needed-config-changes")
    fun inspectTopicNeededConfigChangesOnCluster(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): List<ConfigValueChange> = topicsInspectionService.topicConfigNeededChanges(topicName, clusterIdentifier)

    @GetMapping("/topics-needed-config-changes")
    fun inspectTopicsNeededConfigChangesOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): Map<TopicName, List<ConfigValueChange>> = topicsInspectionService.inspectTopicsConfigsNeededChanges(clusterIdentifier)

    @PostMapping("/topic-inspect-dry-run")
    fun inspectTopicUpdateDryRun(
        @RequestBody topicDescription: TopicDescription,
    ): TopicStatuses = topicsInspectionService.inspectTopicDryRun(topicDescription)

    @GetMapping("/topics/clusters")
    fun inspectTopicsOnClusters(): List<ClusterTopicsStatuses> = topicsInspectionService.inspectAllClustersTopics()

    @GetMapping("/topics/cluster")
    fun inspectTopicsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ClusterTopicsStatuses = topicsInspectionService.inspectClusterTopics(clusterIdentifier)

    @GetMapping("/unknown-topics")
    fun inspectUnknownTopics(): List<TopicStatuses> = topicsInspectionService.inspectUnknownTopics()

    @GetMapping("/missing-topics")
    fun listMissingTopics(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): List<TopicDescription> = topicsInspectionService.listMissingTopics(clusterIdentifier)

    @GetMapping("/leader-re-election-topics")
    fun listTopicsForLeaderReElection(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): List<ExistingTopicInfo> = topicsInspectionService.listTopicsForLeaderReElection(clusterIdentifier)

    @PostMapping("/compare")
    fun compareTopics(
        @RequestBody compareRequest: ComparingRequest
    ): ComparingResult = topicComparator.compareTopicConfigurations(compareRequest)

    @GetMapping("/acls/principals")
    fun inspectAllPrincipals(): List<PrincipalAclsInspection> = aclsInspectionService.inspectAllPrincipals()

    @GetMapping("/acls/unknown-principals")
    fun inspectUnknownPrincipals(): List<PrincipalAclsInspection> = aclsInspectionService.inspectUnknownPrincipals()

    @GetMapping("/acls/clusters")
    fun inspectAllClustersAcls(): List<ClusterAclsInspection> = aclsInspectionService.inspectAllClusters()

    @GetMapping("/acls/principal-clusters-rules")
    fun inspectPrincipalAcls(
        @RequestParam("principal") principal: PrincipalId
    ): PrincipalAclsInspection = aclsInspectionService.inspectPrincipalAcls(principal)

    @GetMapping("/acls/principal-rules-clusters")
    fun inspectPrincipalAclsClusterPerRule(
        @RequestParam("principal") principal: PrincipalId
    ): PrincipalAclsClustersPerRuleInspection = aclsInspectionService.inspectPrincipalAclsPerRule(principal)

    @GetMapping("/acls/cluster")
    fun inspectClusterAcls(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ClusterAclsInspection = aclsInspectionService.inspectClusterAcls(clusterIdentifier)

    @GetMapping("/acls/principal-cluster")
    fun inspectPrincipalAclsOnCluster(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): PrincipalAclsClusterInspection =
        aclsInspectionService.inspectPrincipalAclsOnCluster(principal, clusterIdentifier)

    @GetMapping("/acls/rule")
    fun inspectRuleOnCluster(
        @RequestParam("rule") rule: String,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): AclRuleStatus = aclsInspectionService.inspectRuleOnCluster(rule.parseAcl(), clusterIdentifier)

    @GetMapping("/acls/principal-inspect-dry-run")
    fun inspectPrincipalUpdateDryRun(
        @RequestBody principalAcls: PrincipalAclRules,
    ): PrincipalAclsInspection = aclsInspectionService.inspectPrincipalAcls(principalAcls)

    @GetMapping("/quotas/entity-cluster")
    fun inspectEntityQuotasOnCluster(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): QuotasInspection = quotasInspectionService.inspectEntityQuotasOnCluster(entityID, clusterIdentifier)

    @GetMapping("/quotas/cluster")
    fun inspectClusterQuotas(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ClusterQuotasInspection = quotasInspectionService.inspectClusterQuotas(clusterIdentifier)

    @GetMapping("/quotas/entity")
    fun inspectEntityOnClusters(
        @RequestParam("quotaEntityID") entityID: QuotaEntityID,
    ): EntityQuotasInspection = quotasInspectionService.inspectEntityOnClusters(entityID)

    @GetMapping("/quotas/entities")
    fun inspectAllQuotaEntities(): List<EntityQuotasInspection> = quotasInspectionService.inspectAllClientEntities()

    @GetMapping("/quotas/unknown-entities")
    fun inspectUnknownQuotaEntities(): List<EntityQuotasInspection> = quotasInspectionService.inspectUnknownClientEntities()

    @GetMapping("/cluster/status")
    fun inspectClusterStatus(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ClusterStatus = clustersStatusService.clusterState(clusterIdentifier)

    @GetMapping("/clusters/statuses")
    fun inspectClustersStatuses(): List<ClusterStatus> = clustersStatusService.clustersState()

    @PostMapping("/clusters/edit-inspect")
    fun inspectClusterEditTagsDryRun(
        @RequestBody cluster: KafkaCluster,
    ): ClusterDryRunInspect = clusterEditTagsInspectService.inspectTagsEdit(cluster)

    @GetMapping("/clusters/issues")
    fun inspectClusterIssues(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): List<ClusterInspectIssue> = clusterIssuesInspectorService.inspectClusterIssues(clusterIdentifier)

}