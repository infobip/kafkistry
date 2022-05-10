package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.KafkaExistingTopic
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.TopicPartitionReplica
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.asTopicConfigValue
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.newState
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.toOkPartitionAssignments
import com.infobip.kafkistry.service.topic.ConfigValueInspector
import com.infobip.kafkistry.service.topic.TopicIssuesInspector
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.service.topic.validation.TopicConfigurationValidator
import com.infobip.kafkistry.service.topic.validation.TopicValidationProperties
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ClusterResourcesAnalyzerTest {

    private val replicasInfoProvider: KafkaReplicasInfoProvider = mock()
    private val brokerDiskMetricsProvider: BrokerDiskMetricsStateProvider = mock()
    private val usageLevelClassifier = DefaultUsageLevelClassifier(UsageLevelThresholds())
    private val clustersRegistry: ClustersRegistryService = mock()
    private val topicsRegistry: TopicsRegistryService = mock()
    private val clusterStateProvider: KafkaClustersStateProvider = mock()

    private val topicsInspectionService = TopicsInspectionService(
        topicsRegistry = topicsRegistry,
        clustersRegistry = clustersRegistry,
        kafkaClustersStateProvider = clusterStateProvider,
        topicIssuesInspector = TopicIssuesInspector(
            ConfigValueInspector(),
            TopicConfigurationValidator(emptyList(), TopicValidationProperties()),
            PartitionsReplicasAssignor(),
            RequiredResourcesInspector(),
            AclLinkResolver(mock()),
        ),
        partitionsReplicasAssignor = PartitionsReplicasAssignor(),
        configValueInspector = ConfigValueInspector(),
        replicaDirsService = ReplicaDirsService(replicasInfoProvider),
        reAssignmentsMonitorService = mock(),
    )

    private val topicDiskAnalyzer = TopicDiskAnalyzer(
        clustersStateProvider = clusterStateProvider,
        topicsInspectionService = topicsInspectionService,
        replicaDirsService = ReplicaDirsService(replicasInfoProvider),
        assignor = PartitionsReplicasAssignor(),
    )

    private val analyzer = ClusterResourcesAnalyzer(
        replicasInfoProvider, brokerDiskMetricsProvider, topicsInspectionService, usageLevelClassifier, topicsRegistry, topicDiskAnalyzer
    )

    private fun newCluster(
        identifier: KafkaClusterIdentifier,
        tags: List<Tag> = emptyList(),
    ) = KafkaCluster(
        identifier = identifier, clusterId = "xyz", connectionString = "xyz:12345",
        sslEnabled = false, saslEnabled = false,
        tags = tags,
    )

    private fun newReplica(
        topic: TopicName,
        brokerId: BrokerId,
        partition: Partition,
        sizeBytes: Long = 0L,
    ) = TopicPartitionReplica(
        rootDir = "/mock-dir",
        brokerId = brokerId,
        topic = topic,
        partition = partition,
        sizeBytes = sizeBytes,
        offsetLag = 0,
        isFuture = false
    )

    private fun mockClusterRegistry(cluster: KafkaCluster) {
        reset(clustersRegistry)
        whenever(clustersRegistry.findCluster(cluster.identifier)).thenReturn(cluster)
        whenever(clustersRegistry.getCluster(cluster.identifier)).thenReturn(cluster)
    }

    private fun mockTopicsRegistry(topics: List<TopicDescription>) {
        reset(topicsRegistry)
        whenever(topicsRegistry.listTopics()).thenReturn(topics)
        topics.forEach {
            whenever(topicsRegistry.getTopic(it.name)).thenReturn(it)
            whenever(topicsRegistry.findTopic(it.name)).thenReturn(it)
        }
    }

    private fun mockClusterState(state: StateData<KafkaClusterState>) {
        reset(clusterStateProvider)
        whenever(clusterStateProvider.getLatestClusterState(state.clusterIdentifier)).thenReturn(state)
        whenever(clusterStateProvider.getLatestClusterStateValue(state.clusterIdentifier)).thenReturn(state.value())
    }

    private fun mockReplicas(identifier: KafkaClusterIdentifier, topicReplicas: List<TopicPartitionReplica>) {
        reset(replicasInfoProvider)
        whenever(replicasInfoProvider.getLatestStateValue(any())).thenReturn(ReplicaDirs(topicReplicas))
        whenever(replicasInfoProvider.getLatestState(any())).thenReturn(
            StateData(
                StateType.VISIBLE, identifier, "mock_replicas_state",
                System.currentTimeMillis(), ReplicaDirs(topicReplicas),
            )
        )
    }

    private fun mockDiskMetrics(
        identifier: KafkaClusterIdentifier,
        numBrokers: Int, total: Long, free: Long,
    ) {
        mockDiskMetrics(identifier, (1..numBrokers).associateWith { BrokerDiskMetric(total, free) })
    }

    private fun mockDiskMetrics(
        identifier: KafkaClusterIdentifier,
        brokersMetrics: Map<BrokerId, BrokerDiskMetric>,
    ) {
        reset(brokerDiskMetricsProvider)
        whenever(brokerDiskMetricsProvider.getLatestStateValue(any())).thenReturn(ClusterBrokerMetrics(brokersMetrics))
        whenever(brokerDiskMetricsProvider.getLatestState(any())).thenReturn(
            StateData(
                StateType.VISIBLE, identifier, "mock_disk_metrics",
                System.currentTimeMillis(), ClusterBrokerMetrics(brokersMetrics),
            )
        )
    }

    @Test
    fun `test empty`() {
        val cluster = newCluster("empty")
        mockClusterRegistry(cluster)
        mockTopicsRegistry(emptyList())
        mockClusterState(cluster.newState(numBrokers = 6))
        mockReplicas(cluster.identifier, emptyList())
        mockDiskMetrics(cluster.identifier, 6, 111_000_000, 11_000_000)
        val usage = analyzer.clusterDiskUsage("empty")
        assertThat(usage.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(totalCapacityBytes = 666_000_000, freeCapacityBytes = 66_000_000)
        )
    }

    @Test
    fun `test having topics`() {
        val cluster = newCluster("myCluster")
        val topics = listOf(
            newTopic("myTopic1", properties = TopicProperties(3, 2)),
            newTopic("myTopic2", properties = TopicProperties(1, 3)),
        )
        mockClusterRegistry(cluster)
        mockTopicsRegistry(topics)
        mockClusterState(
            cluster.newState(*topics.toTypedArray(), numBrokers = 3) {
                when (it.name) {
                    "myTopic1" -> KafkaExistingTopic(
                        it.name, false,
                        config = mapOf("retention.bytes" to 3_000_000.asTopicConfigValue()),
                        partitionsAssignments = mapOf(
                            0 to listOf(1, 2),
                            1 to listOf(3, 1),
                            2 to listOf(2, 3),
                        ).toOkPartitionAssignments(),
                    )
                    "myTopic2" -> KafkaExistingTopic(
                        it.name, false,
                        config = mapOf("retention.bytes" to 10_000.asTopicConfigValue()),
                        partitionsAssignments = mapOf(0 to listOf(1, 2, 3)).toOkPartitionAssignments(),
                    )
                    else -> fail { "topic ${it.name}?" }
                }
            }
        )
        mockReplicas(
            cluster.identifier, listOf(
                newReplica("myTopic1", 1, 0, 200_000),
                newReplica("myTopic1", 2, 0, 300_000),
                newReplica("myTopic1", 3, 1, 400_000),
                newReplica("myTopic1", 1, 1, 500_000),
                newReplica("myTopic1", 2, 2, 600_000),
                newReplica("myTopic1", 3, 2, 700_000),
                newReplica("myTopic2", 1, 0, 1_000),
                newReplica("myTopic2", 2, 0, 3_000),
                newReplica("myTopic2", 3, 0, 5_000),
                newReplica("orphan", 2, 0, 11_000),
                newReplica("orphan", 3, 2, 22_000),
            )
        )
        mockDiskMetrics(cluster.identifier, 3, 10_000_000, 5_000_000)
        val usage = analyzer.clusterDiskUsage("myCluster")
        assertThat(usage.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(
                replicasCount = 11,
                totalUsedBytes = 2_742_000,
                boundedReplicasCount = 9,
                orphanedReplicasCount = 2,
                orphanedReplicasSizeUsedBytes = 33_000,
                boundedSizePossibleUsedBytes = 6 * 3_000_000L + 3 * 10_000L,
                totalCapacityBytes = 30_000_000,
                freeCapacityBytes = 15_000_000,
            )
        )
    }

    @Test
    fun `test dry run adding tag which implies new topic`() {
        val cluster = newCluster("myCluster")
        val topic = newTopic(
            name = "myTopic",
            properties = TopicProperties(10, 2),
            config = mapOf(
                "retention.bytes" to "10000"
            ),
            presence = Presence(PresenceType.TAGGED_CLUSTERS, tag = "test-tag")
        )
        mockTopicsRegistry(listOf(topic))
        mockClusterRegistry(cluster)
        mockClusterState(cluster.newState(numBrokers = 2))
        mockDiskMetrics(cluster.identifier, 2, 1_000_000, 900_000)
        mockReplicas(cluster.identifier, emptyList())
        val usageEmpty = analyzer.dryRunClusterDiskUsage(cluster)
        assertThat(usageEmpty.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(totalCapacityBytes = 2_000_000, freeCapacityBytes = 1_800_000)
        )
        val usage = analyzer.dryRunClusterDiskUsage(
            cluster.copy(tags = listOf("test-tag"))
        )
        assertThat(usage.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(
                replicasCount = 20,
                totalUsedBytes = 20 * 10_000L,
                boundedReplicasCount = 20,
                boundedSizePossibleUsedBytes = 20 * 10_000L,
                totalCapacityBytes = 2_000_000,
                freeCapacityBytes = 1_800_000,
            )
        )
    }

    @Test
    fun `test dry run adding tag which implies bigger topic config`() {
        val cluster = newCluster("myCluster")
        val topic = newTopic(
            name = "myTopic",
            properties = TopicProperties(1, 2),
            config = mapOf("retention.bytes" to "10000"),
            perTagProperties = mapOf("test-tag" to TopicProperties(5, 3)),
            perTagConfigOverrides = mapOf("test-tag" to mapOf("retention.bytes" to "30000")),
        )
        mockTopicsRegistry(listOf(topic))
        mockClusterRegistry(cluster)
        mockClusterState(cluster.newState(topic, numBrokers = 3))
        mockDiskMetrics(cluster.identifier, 3, 1_000_000, 900_000)
        mockReplicas(cluster.identifier, listOf(
            newReplica("myTopic", 1, 0, 9_000),
            newReplica("myTopic", 2, 0, 9_000),
        ))
        val usageBeforeTag = analyzer.dryRunClusterDiskUsage(cluster)
        assertThat(usageBeforeTag.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(
                replicasCount = 2,
                totalUsedBytes = 18_000,
                boundedReplicasCount = 2,
                boundedSizePossibleUsedBytes = 2 * 10_000L,
                totalCapacityBytes = 3_000_000,
                freeCapacityBytes = 2_700_000,
            )
        )
        val usageAfterTag = analyzer.dryRunClusterDiskUsage(
            cluster.copy(tags = listOf("test-tag"))
        )
        assertThat(usageAfterTag.combined.usage).isEqualTo(
            BrokerDiskUsage.ZERO.copy(
                replicasCount = 15,
                totalUsedBytes = 18_000,
                boundedReplicasCount = 15,
                boundedSizePossibleUsedBytes = 15 * 30_000L,
                totalCapacityBytes = 3_000_000,
                freeCapacityBytes = 2_700_000,
            )
        )
    }


}