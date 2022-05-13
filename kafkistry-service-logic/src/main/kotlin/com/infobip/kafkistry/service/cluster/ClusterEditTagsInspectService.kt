package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.AvailableAclOperation.*
import com.infobip.kafkistry.service.AvailableAction.*
import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.quotas.AvailableQuotasOperation.*
import com.infobip.kafkistry.service.quotas.ClusterQuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspectionService
import com.infobip.kafkistry.service.resources.*
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import org.springframework.stereotype.Service
import kotlin.math.abs

@Service
class ClusterEditTagsInspectService(
    private val clustersRegistryService: ClustersRegistryService,
    private val clusterResourcesAnalyzer: ClusterResourcesAnalyzer,
    private val topicsInspectionService: TopicsInspectionService,
    private val aclsInspectionService: AclsInspectionService,
    private val quotasInspectionService: QuotasInspectionService,
) {

    fun inspectTagsEdit(cluster: KafkaCluster): ClusterDryRunInspect {
        val clusterCurrent = clustersRegistryService.getCluster(cluster.identifier)
        return inspectTagsEdit(
            clusterIdentifier = cluster.identifier,
            tagsBefore = clusterCurrent.tags,
            tagsAfter = cluster.tags,
        )
    }

    fun inspectTagsEdit(
        clusterIdentifier: KafkaClusterIdentifier,
        tagsBefore: List<Tag>,
        tagsAfter: List<Tag>,
    ): ClusterDryRunInspect {
        val clusterRefBefore = ClusterRef(clusterIdentifier, tagsBefore)
        val clusterRefAfter = ClusterRef(clusterIdentifier, tagsAfter)
        val clusterDiskUsageBefore = clusterResourcesAnalyzer.clusterDiskUsage(clusterIdentifier)
        val clusterDiskUsageAfter = clusterResourcesAnalyzer.dryRunClusterDiskUsage(clusterRefAfter)
        val topicsInspectBefore = topicsInspectionService.inspectCluster(clusterRefBefore)
        val topicsInspectAfter = topicsInspectionService.inspectCluster(clusterRefAfter)
        val aclsInspectBefore = aclsInspectionService.inspectClusterAcls(clusterRefBefore)
        val aclsInspectAfter = aclsInspectionService.inspectClusterAcls(clusterRefAfter)
        val quotasInspectBefore = quotasInspectionService.inspectClusterQuotas(clusterRefBefore)
        val quotasInspectAfter = quotasInspectionService.inspectClusterQuotas(clusterRefAfter)
        return ClusterDryRunInspect(
            errors = (clusterDiskUsageBefore.errors + clusterDiskUsageAfter.errors).distinct(),
            clusterDiskUsageBefore = clusterDiskUsageBefore,
            clusterDiskUsageAfter = clusterDiskUsageAfter,
            clusterDiskUsageDiff = clusterDiskUsageAfter - clusterDiskUsageBefore,
            topicsDiff = computeTopicsDiff(topicsInspectBefore, topicsInspectAfter, clusterDiskUsageBefore, clusterDiskUsageAfter),
            aclsDiff = computeAclsDiff(aclsInspectBefore, aclsInspectAfter),
            quotasDiff = computeQuotasDiff(quotasInspectBefore, quotasInspectAfter),
        )
    }

    private fun computeTopicsDiff(
        inspectBefore: ClusterTopicsStatuses,
        inspectAfter: ClusterTopicsStatuses,
        clusterDiskUsageBefore: ClusterDiskUsage,
        clusterDiskUsageAfter: ClusterDiskUsage,
    ): TopicsDryRunDiff {
        val countsBefore = inspectBefore.topicsStatusCounts
        val countsAfter = inspectAfter.topicsStatusCounts
        val topicsBefore = inspectBefore.statusPerTopics
        val topicsAfter = inspectAfter.statusPerTopics
        if (countsBefore == null || countsAfter == null || topicsBefore == null || topicsAfter == null) {
            val statusBefore = inspectBefore.clusterState
            val statusAfter = inspectAfter.clusterState
            illegalStateError(statusBefore, statusAfter)
        }

        fun List<ClusterTopicStatus>.topicsWhich(
            filter: (ClusterTopicStatus) -> Boolean
        ): List<TopicName> = filter(filter).map { it.topicName }

        return TopicsDryRunDiff(
            statusCounts = countsBefore diff countsAfter,
            topicsToCreate = topicsAfter.topicsWhich { CREATE_TOPIC in it.status.availableActions },
            topicsToDelete = topicsAfter.topicsWhich { DELETE_TOPIC_ON_KAFKA in it.status.availableActions },
            topicsToReconfigure = topicsAfter.topicsWhich { ALTER_TOPIC_CONFIG in it.status.availableActions },
            topicsToReScale = topicsAfter.topicsWhich { ALTER_PARTITION_COUNT in it.status.availableActions },
            topicDiskUsages = clusterDiskUsageAfter.topicDiskUsages.subtractOptionals(clusterDiskUsageBefore.topicDiskUsages, TopicClusterDiskUsage::minus, TopicClusterDiskUsage::unaryMinus)
        )
    }

    private fun computeAclsDiff(
        inspectBefore: ClusterAclsInspection,
        inspectAfter: ClusterAclsInspection,
    ): AclsDryRunDiff {
        val countsBefore = inspectBefore.status.statusCounts
        val countsAfter = inspectAfter.status.statusCounts
        val aclsAfter = inspectAfter.principalAclsInspections
            .flatMap { principalStatuses -> principalStatuses.statuses.map { it } }

        fun List<AclRuleStatus>.topicsWhich(
            filter: (AclRuleStatus) -> Boolean
        ): List<KafkaAclRule> = filter(filter).map { it.rule }

        return AclsDryRunDiff(
            statusCounts = countsBefore diff countsAfter,
            aclsToCreate = aclsAfter.topicsWhich { CREATE_MISSING_ACLS in it.availableOperations },
            aclsToDelete = aclsAfter.topicsWhich { DELETE_UNWANTED_ACLS in it.availableOperations },
        )
    }

    private fun computeQuotasDiff(
        inspectBefore: ClusterQuotasInspection,
        inspectAfter: ClusterQuotasInspection,
    ): QuotasDryRunDiff {
        val countsBefore = inspectBefore.status.statusCounts
        val countsAfter = inspectAfter.status.statusCounts
        val quotasAfter = inspectAfter.entityInspections


        fun List<QuotasInspection>.topicsWhich(
            filter: (QuotasInspection) -> Boolean
        ): List<QuotaEntity> = filter(filter).map { it.entity }

        return QuotasDryRunDiff(
            statusCounts = countsBefore diff countsAfter,
            quotasToCreate = quotasAfter.topicsWhich { CREATE_MISSING_QUOTAS in it.availableOperations },
            quotasToDelete = quotasAfter.topicsWhich { DELETE_UNWANTED_QUOTAS in it.availableOperations },
            quotasToReconfigure = quotasAfter.topicsWhich { ALTER_WRONG_QUOTAS in it.availableOperations },
        )
    }

    private fun illegalStateError(statusBefore: StateType, statusAfter: StateType): Nothing {
        if (statusBefore == statusAfter) {
            throw KafkistryIllegalStateException("Can't analyze cluster dry run diff, cluster status is: $statusBefore")
        } else {
            throw KafkistryIllegalStateException("Can't analyze cluster dry run diff, cluster statuses are before=$statusBefore after=$statusAfter")
        }
    }

    private infix fun <E: Enum<E>> Map<E, Int>.diff(countsAfter: Map<E, Int>): Map<E, CountDiff> =
        (this.keys + countsAfter.keys)
            .associateWith { (this[it] ?: 0) diff (countsAfter[it] ?: 0) }
            .diffSorted()

    private fun <K> Map<K, CountDiff>.diffSorted(): Map<K, CountDiff> = entries
        .sortedBy { -abs(it.value.diff) }   // descending by difference amplitude
        .associate { it.toPair() }
}