package com.infobip.kafkistry.service.it

import com.nhaarman.mockitokotlin2.whenever
import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.STATIC_BROKER_CONFIG
import org.assertj.core.groups.Tuple.tuple
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CURRENT_CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_CONFIG
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_PARTITION_COUNT
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_REPLICATION_FACTOR
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import java.util.function.Function

@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("it", "dir")
class InspectionTest {

    @Autowired
    private lateinit var inspection: TopicsInspectionService
    @MockBean
    private lateinit var stateProvider: KafkaClustersStateProvider
    @Autowired
    private lateinit var clusters: ClustersRegistryService
    @Autowired
    private lateinit var topics: TopicsRegistryService

    @Before
    fun before() {
        reset(stateProvider)
        topics.deleteAll(UpdateContext("test msg"))
        clusters.removeAll()
    }

    @After
    fun after() {
        clusters.removeAll()
    }

    @Test
    fun `test ok topic no clusters`() {
        val topic = newTopic()
        topics.createTopic(topic)
        val status = inspection.inspectTopic(topic.name)
        val statuses = inspection.inspectAllTopics()
        assertAll {
            assertThat(status.aggStatusFlags.allOk).`as`("fully ok").isEqualTo(true)
            assertThat(status.statusPerClusters).`as`("status per clusters").isEmpty()
            assertThat(statuses).`as`("all topic statuses list").containsExactly(status)
        }
    }

    @Test
    fun `test ok topic`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `test ok topic with properties and config overrides`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                perClusterProperties = mapOf(cluster.identifier to TopicProperties(2, 2)),
                perClusterConfigOverrides = mapOf(cluster.identifier to mapOf("retention.ms" to "10000"))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `test unknown topic`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        val status = inspection.inspectClusterTopics(cluster.identifier)
        val statuses = inspection.inspectAllClustersTopics()
        val unknownTopics = inspection.inspectUnknownTopics()
        assertAll {
            assertThat(status.aggStatusFlags.allOk).`as`("fully ok").isEqualTo(false)
            assertThat(status.statusPerTopics)
                    .extracting(
                            Function { it.topicName },
                            Function { it.status.types }
                    )
                    .containsExactly(tuple(topic.name, listOf(UNKNOWN)))
            assertThat(statuses).`as`("all clusters statuses").containsExactly(status)
            assertThat(unknownTopics)
                    .extracting(Function { it.topicName })
                    .containsExactly(topic.name)
        }
    }

    @Test
    fun `test wrong config topic`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf("retention.bytes" to "1112220"))
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic.copy(config = mapOf("retention.bytes" to "2221110"))))
        assertTopicState(topic.name, cluster.identifier, WRONG_CONFIG, wrongValue("retention.bytes", "2221110", "1112220"))
    }

    @Test
    fun `test wrong config override`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(1, 3),
                config = mapOf("min.insync.replicas" to "1"),
                perClusterConfigOverrides = mapOf(cluster.identifier to mapOf("min.insync.replicas" to "2"))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic.copy(perClusterConfigOverrides = emptyMap())))
        assertTopicState(topic.name, cluster.identifier, WRONG_CONFIG, wrongValue("min.insync.replicas", "1", "2"))
    }

    @Test
    fun `message-format test ok config message format version defined in cluster config`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                nonDefaultConfig = mapOf("message.format.version" to "2.1-IV"),
                clusterConfig = mapOf("log.message.format.version" to ConfigValue("2.1", default = false, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `message-format test ok config message format version defined in cluster config, cluster config is irrelevantly default`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                nonDefaultConfig = mapOf("message.format.version" to "2.1-IV"),
                clusterConfig = mapOf("log.message.format.version" to ConfigValue("2.1", default = true, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `message-format test wrong config message format version different defined in cluster config`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                nonDefaultConfig = mapOf("message.format.version" to "2.1-IV"),
                clusterConfig = mapOf("log.message.format.version" to ConfigValue("2.2", default = false, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, WRONG_CONFIG, wrongValueDefaultExpected("message.format.version", "2.1-IV", "2.2"))
    }

    @Test
    fun `test wrong config, config val not defined in topic and cluster has non default which is different than actual`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                nonDefaultConfig = mapOf("retention.bytes" to "1112220"),
                clusterConfig = mapOf("retention.bytes" to ConfigValue("2221110", default = false, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, WRONG_CONFIG, wrongValueDefaultExpected("retention.bytes", "1112220", "2221110"))
    }

    @Test
    fun `test ok config, config val not defined in topic and actual value is default cluster config irrelevant`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                defaultConfig = mapOf("retention.bytes" to "1112220"),
                clusterConfig = mapOf("retention.bytes" to ConfigValue(null, default = true, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `test ok config, config val not defined in topic and actual value is default`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                defaultConfig = mapOf("retention.bytes" to "1112220")
        ))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `test ok config, config val not defined in topic and cluster has non default which is equal to actual`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(config = mapOf())
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic,
                nonDefaultConfig = mapOf("retention.bytes" to "1112220"),
                clusterConfig = mapOf("retention.bytes" to ConfigValue("1112220", default = false, readOnly = true, sensitive = false, STATIC_BROKER_CONFIG))
        ))
        assertTopicState(topic.name, cluster.identifier, OK)
    }

    @Test
    fun `test wrong partition count`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(3, 1)
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic.copy(properties = TopicProperties(2, 1))))
        assertTopicState(topic.name, cluster.identifier, WRONG_PARTITION_COUNT, wrongValue("partition-count", "2", "3"))
    }

    @Test
    fun `test wrong replication factor`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(2, 2)
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic.copy(properties = TopicProperties(2, 1))))
        assertTopicState(topic.name, cluster.identifier, WRONG_REPLICATION_FACTOR,
                wrongValue("replication-factor", "1", "2")
        )
    }

    @Test
    fun `test wrong partition count override`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(3, 2),
                perClusterProperties = mapOf(cluster.identifier to TopicProperties(6, 2))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic.copy(perClusterProperties = emptyMap()),
                numBrokers = 3
        ))
        assertTopicState(topic.name, cluster.identifier, WRONG_PARTITION_COUNT, wrongValue("partition-count", "3", "6"))
    }

    @Test
    fun `test wrong replication factor override`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(3, 1),
                perClusterProperties = mapOf(cluster.identifier to TopicProperties(3, 4))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic.copy(perClusterProperties = emptyMap())))
        assertTopicState(topic.name, cluster.identifier, WRONG_REPLICATION_FACTOR,
                wrongValue("replication-factor", "1", "4")
        )
    }

    @Test
    fun `test unexpected topic in case include other`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("some-other-cluster-id"))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        assertTopicState(topic.name, cluster.identifier, UNEXPECTED)
    }

    @Test
    fun `test unexpected topic in case exclude cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.EXCLUDED_CLUSTERS, listOf(cluster.identifier))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        assertTopicState(topic.name, cluster.identifier, UNEXPECTED)
    }

    @Test
    fun `test missing topic when expected on all`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.ALL_CLUSTERS, null)
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState())
        assertTopicState(topic.name, cluster.identifier, MISSING)
    }

    @Test
    fun `test missing topic when expected on cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf(cluster.identifier))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState())
        assertTopicState(topic.name, cluster.identifier, MISSING)
    }

    @Test
    fun `test missing topic when excluded from other cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.EXCLUDED_CLUSTERS, listOf("some-other-cluster-id"))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState())
        assertTopicState(topic.name, cluster.identifier, MISSING)
    }

    @Test
    fun `test missing as expected topic when included only on other cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("some-other-cluster-id"))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState())
        assertTopicState(topic.name, cluster.identifier, NOT_PRESENT_AS_EXPECTED)
    }

    @Test
    fun `test missing as expected topic when excluded from cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                presence = Presence(PresenceType.EXCLUDED_CLUSTERS, listOf(cluster.identifier))
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState())
        assertTopicState(topic.name, cluster.identifier, NOT_PRESENT_AS_EXPECTED)
    }

    @Test
    fun `test cluster unreachable`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(stateType = StateType.UNREACHABLE))
        assertTopicState(topic.name, cluster.identifier, CLUSTER_UNREACHABLE)
    }

    @Test
    fun `test cluster unreachable not yet refreshed`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(stateType = StateType.UNKNOWN))
        assertTopicState(topic.name, cluster.identifier, CLUSTER_UNREACHABLE)
    }

    @Test
    fun `test cluster unreachable invalid id`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(stateType = StateType.INVALID_ID))
        assertTopicState(topic.name, cluster.identifier, CLUSTER_UNREACHABLE)
    }

    @Test
    fun `test cluster disabled`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(stateType = StateType.DISABLED))
        assertTopicState(topic.name, cluster.identifier, CLUSTER_DISABLED)
    }

    @Test
    fun `test min-insync-replicas rule violation`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("min.insync.replicas" to "3")
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        assertTopicState(topic.name, cluster.identifier, listOf(CONFIG_RULE_VIOLATIONS, CURRENT_CONFIG_RULE_VIOLATIONS))
    }

    @Test
    fun `test min-insync-replicas rule violation only in config`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("min.insync.replicas" to "2")
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic.copy(properties = TopicProperties(2, 3)),
                numBrokers = 3
        ))
        assertTopicState(topic.name, cluster.identifier, listOf(CONFIG_RULE_VIOLATIONS, WRONG_REPLICATION_FACTOR),
                wrongValue("replication-factor", "3", "2")
        )
    }

    @Test
    fun `test min-insync-replicas rule violation only on cluster`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("min.insync.replicas" to "2")
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(
                topic.copy(properties = TopicProperties(2, 2))
        ))
        assertTopicState(topic.name, cluster.identifier, listOf(WRONG_REPLICATION_FACTOR, CURRENT_CONFIG_RULE_VIOLATIONS),
                wrongValue("replication-factor", "2", "3")
        )
    }

    @Test
    fun `test less brokers than replication factor violation`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic(
                properties = TopicProperties(1, 3)
        )
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(numBrokers = 2))
        assertTopicState(topic.name, cluster.identifier, listOf(CONFIG_RULE_VIOLATIONS, MISSING))
    }

    private fun assertTopicState(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        type: TopicInspectionResultType,
        vararg expectedAssertions: WrongValueAssertion
    ) = assertTopicState(topicName, clusterIdentifier, listOf(type), *expectedAssertions)

    private fun assertTopicState(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        types: List<TopicInspectionResultType>,
        vararg expectedAssertions: WrongValueAssertion
    ) {
        val expectingFullyOk = types.any { it in setOf(OK, NOT_PRESENT_AS_EXPECTED, CLUSTER_DISABLED) }
        val clusterStatus = inspection.inspectClusterTopics(clusterIdentifier)
        val clusterStatuses = inspection.inspectAllClustersTopics()
        val topicStatus = inspection.inspectTopic(topicName)
        val topicsStatuses = inspection.inspectAllTopics()
        val unknownTopics = inspection.inspectUnknownTopics()
        assertAll {
            assertThat(clusterStatus.aggStatusFlags.allOk).`as`("cluster fully ok").isEqualTo(expectingFullyOk)
            if (types.none { it in setOf(CLUSTER_UNREACHABLE, CLUSTER_DISABLED) }) {
                assertThat(clusterStatus.clusterState).isEqualTo(StateType.VISIBLE)
                assertThat(clusterStatus.statusPerTopics)
                        .`as`(clusterStatus.statusPerTopics.toString())
                        .extracting(
                            Function { it.topicName },
                            Function { it.status.types }
                        )
                        .containsExactly(tuple(topicName, types))
            } else {
                assertThat(clusterStatus.statusPerTopics).isNull()
            }
            assertThat(clusterStatuses).`as`("all clusters statuses").containsExactly(clusterStatus)
            assertThat(topicStatus.aggStatusFlags.allOk).`as`("topic fully ok").isEqualTo(expectingFullyOk)
            assertThat(topicStatus.statusPerClusters)
                    .extracting(
                        Function { it.clusterIdentifier },
                        Function { it.status.types }
                    )
                    .containsExactly(tuple(clusterIdentifier, types))
            assertThat(topicsStatuses).`as`("all topics statuses").containsExactly(topicStatus)
            assertThat(topicStatus.statusPerClusters)
                    .flatExtracting(Function<TopicClusterStatus, List<WrongValueAssertion>> {
                        it.status.wrongValues
                                ?.map { assertion -> assertion.copy(message = null) }   //do not validate message
                                ?: emptyList()
                    })
                    .containsExactly(*expectedAssertions)
            assertThat(unknownTopics).isEmpty()
        }
    }

}
