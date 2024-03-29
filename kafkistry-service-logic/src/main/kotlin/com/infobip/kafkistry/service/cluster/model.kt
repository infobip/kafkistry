package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.NamedTypeQuantity
import com.infobip.kafkistry.service.acl.AclInspectionResultType
import com.infobip.kafkistry.service.topic.TopicInspectionResultType
import com.infobip.kafkistry.service.OptionalValue
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType
import com.infobip.kafkistry.service.resources.ClusterDiskUsage
import com.infobip.kafkistry.service.resources.TopicClusterDiskUsage

data class ClusterStatus(
    val lastRefreshTime: Long,
    val cluster: KafkaCluster,
    val clusterInfo: ClusterInfo?,
    val clusterState: StateType,
)

data class ClusterDryRunInspect(
    val errors: List<String>,
    val clusterDiskUsageBefore: ClusterDiskUsage,
    val clusterDiskUsageAfter: ClusterDiskUsage,
    val clusterDiskUsageDiff: ClusterDiskUsage,
    val topicsDiff: TopicsDryRunDiff,
    val aclsDiff: AclsDryRunDiff,
    val quotasDiff: QuotasDryRunDiff,
)

data class CountDiff(
    val before: Int,
    val after: Int,
    val diff: Int,
)

infix fun Int.diff(after: Int) = CountDiff(this, after, after - this)

data class TopicsDryRunDiff(
    val problems: List<String>,
    val statusCounts: List<NamedTypeQuantity<TopicInspectionResultType, CountDiff>>,
    val affectedTopicsCount: Int,
    val topicsToCreate: List<TopicName>,
    val topicsToDelete: List<TopicName>,
    val topicsToReconfigure: List<TopicName>,
    val topicsToReScale: List<TopicName>,
    val topicDiskUsages: Map<TopicName, OptionalValue<TopicClusterDiskUsage>>,
)

data class AclsDryRunDiff(
    val statusCounts: List<NamedTypeQuantity<AclInspectionResultType, CountDiff>>,
    val aclsToCreate: List<KafkaAclRule>,
    val aclsToDelete: List<KafkaAclRule>,
)

data class QuotasDryRunDiff(
    val statusCounts: List<NamedTypeQuantity<QuotasInspectionResultType, CountDiff>>,
    val quotasToCreate: List<QuotaEntity>,
    val quotasToDelete: List<QuotaEntity>,
    val quotasToReconfigure: List<QuotaEntity>,
)

data class TagClusters(
    val tag: Tag,
    val clusters: List<KafkaClusterIdentifier>,
)