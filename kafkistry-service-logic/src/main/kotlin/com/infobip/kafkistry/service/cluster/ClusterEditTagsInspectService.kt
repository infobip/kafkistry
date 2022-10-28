package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.acl.AclRuleStatus
import com.infobip.kafkistry.service.acl.AvailableAclOperation.*
import com.infobip.kafkistry.service.topic.AvailableAction.*
import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.acl.ClusterAclsInspection
import com.infobip.kafkistry.service.quotas.AvailableQuotasOperation.*
import com.infobip.kafkistry.service.quotas.ClusterQuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspectionService
import com.infobip.kafkistry.service.resources.*
import com.infobip.kafkistry.service.topic.ClusterTopicStatus
import com.infobip.kafkistry.service.topic.ClusterTopicsStatuses
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
        val topicsInspectBefore = topicsInspectionService.inspectClusterTopics(clusterRefBefore)
        val topicsInspectAfter = topicsInspectionService.inspectClusterTopics(clusterRefAfter)
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

        val topicsToCreate = topicsAfter.topicsWhich { CREATE_TOPIC in it.status.availableActions }
        val topicsToDelete = topicsAfter.topicsWhich { DELETE_TOPIC_ON_KAFKA in it.status.availableActions }
        val topicsToReconfigure = topicsAfter.topicsWhich { ALTER_TOPIC_CONFIG in it.status.availableActions }
        val topicsToReScale = topicsAfter.topicsWhich { ALTER_PARTITION_COUNT in it.status.availableActions }
        val topicToShrinkPartitions = topicsToReducePartitionCount(topicsAfter)
        return TopicsDryRunDiff(
            problems = sequence {
                topicToShrinkPartitions.forEach { (topic, partitionCount) ->
                    yield("Can't reduce partition count for topic '$topic' from ${partitionCount.first} to ${partitionCount.second}")
                }
            }.toList(),
            statusCounts = countsBefore diff countsAfter,
            affectedTopicsCount = sequenceOf(topicsToCreate, topicsToDelete, topicsToReconfigure, topicsToReScale)
                .flatten().distinct().count(),
            topicsToCreate = topicsToCreate,
            topicsToDelete = topicsToDelete,
            topicsToReconfigure = topicsToReconfigure,
            topicsToReScale = topicsToReScale,
            topicDiskUsages = clusterDiskUsageAfter.topicDiskUsages.subtractOptionals(clusterDiskUsageBefore.topicDiskUsages, TopicClusterDiskUsage::minus, TopicClusterDiskUsage::unaryMinus)
        )
    }

    private fun topicsToReducePartitionCount(topicsStatuses: List<ClusterTopicStatus>): Map<TopicName, Pair<Int, Int>> =
        topicsStatuses
            .filter { ALTER_PARTITION_COUNT in it.status.availableActions }
            .mapNotNull { topicStatus ->
                topicStatus.status.wrongValues
                    ?.find { it.key == "partition-count" }
                    ?.let {
                        val expected = it.expected?.toIntOrNull() ?: 0
                        val actual = it.actual?.toIntOrNull() ?: 0
                        actual to expected
                    }
                    ?.let { topicStatus.topicName to it }
            }
            .filter { (_, partitionCount) -> partitionCount.second < partitionCount.first }
            .toMap()

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

    private infix fun <E : NamedType> List<NamedTypeQuantity<E, Int>>.diff(
        countsAfter: List<NamedTypeQuantity<E, Int>>
    ): List<NamedTypeQuantity<E, CountDiff>> {
        val beforeMap = this.associate { it.type to it.quantity }
        val afterMap = countsAfter.associate { it.type to it.quantity }
        return (beforeMap.keys + afterMap.keys)
            .associateWith { (beforeMap[it] ?: 0) diff (afterMap[it] ?: 0) }
            .diffSorted()
            .map { NamedTypeQuantity(it.key, it.value) }
    }

    private fun <K> Map<K, CountDiff>.diffSorted(): Map<K, CountDiff> =
        sortedDescending { -abs(it.value.diff) } // descending by difference amplitude

}