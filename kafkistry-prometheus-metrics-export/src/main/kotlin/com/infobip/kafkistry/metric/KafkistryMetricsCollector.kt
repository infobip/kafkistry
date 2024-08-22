package com.infobip.kafkistry.metric

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.ClusterTopicOffsets
import com.infobip.kafkistry.kafkastate.TopicReplicaInfos
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.acl.PrincipalAclsInspection
import com.infobip.kafkistry.service.cluster.ClusterStatusIssues
import com.infobip.kafkistry.service.consumers.ClusterConsumerGroup
import com.infobip.kafkistry.service.topic.TopicStatuses
import io.prometheus.client.Collector.MetricFamilySamples

interface KafkistryMetricsCollector {

    fun expose(context: MetricsDataContext): List<MetricFamilySamples>
}

data class MetricsDataContext(
    val clusters: Map<KafkaClusterIdentifier, ClusterRef>,
    val clusterStatuses: List<ClusterStatusIssues>,
    val topicInspections: List<TopicStatuses>,
    val clustersGroups: List<ClusterConsumerGroup>,
    val allClustersTopicsOffsets: Map<ClusterRef, ClusterTopicOffsets>,
    val allClustersTopicOldestAges: Map<KafkaClusterIdentifier, Map<TopicName, Map<Partition, Long>>?>,
    val allClustersTopicReplicaInfos: Map<KafkaClusterIdentifier, Map<TopicName, TopicReplicaInfos>>,
    val aclPrincipalInspections: List<PrincipalAclsInspection>,
)