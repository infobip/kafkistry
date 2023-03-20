package com.infobip.kafkistry.service.it

import com.nhaarman.mockitokotlin2.whenever
import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DEFAULT_CONFIG
import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.Presence
import com.infobip.kafkistry.model.PresenceType
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.existingvalues.ExistingValuesService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.service.topic.OperationSuggestionService
import com.infobip.kafkistry.TestDirsPathInitializer
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

@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("it", "dir")
class TopicConfigSuggestionTest {

    @Autowired
    private lateinit var suggestionService: OperationSuggestionService
    @Autowired
    private lateinit var existingValuesService: ExistingValuesService
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
    fun `test import one source topic`() {
        val cluster = newCluster()
        clusters.addCluster(cluster)
        val topic = newTopic()
        whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(topic.withEmptyOwnerDescriptionProducer())
    }

    @Test
    fun `test import presence on all`() {
        val topic = newTopic(
                presence = Presence(PresenceType.ALL_CLUSTERS, null)
        )
        repeat(3) {
            val cluster = newCluster(clusterId = "id_$it", identifier = "kfk_$it")
            clusters.addCluster(cluster)
            whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(topic))
        }
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(topic.withEmptyOwnerDescriptionProducer())
    }

    @Test
    fun `test import presence inclusion`() {
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("kfk_0"))
        )
        repeat(3) {
            val cluster = newCluster(clusterId = "id_$it", identifier = "kfk_$it")
            clusters.addCluster(cluster)
            val topics = if (it == 0) arrayOf(topic) else emptyArray()
            whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(*topics))
        }
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(topic.withEmptyOwnerDescriptionProducer())
    }

    @Test
    fun `test import presence exclusion`() {
        val topic = newTopic(
                presence = Presence(PresenceType.EXCLUDED_CLUSTERS, listOf("kfk_2"))
        )
        repeat(3) {
            val cluster = newCluster(clusterId = "id_$it", identifier = "kfk_$it")
            clusters.addCluster(cluster)
            val topics = if (it == 2) emptyArray() else arrayOf(topic)
            whenever(stateProvider.getLatestClusterState(cluster.identifier)).thenReturn(cluster.newState(*topics))
        }
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(topic.withEmptyOwnerDescriptionProducer())
    }

    @Test
    fun `test partitions&replication different for 1 of 3 clusters will result in per cluster override`() {
        val topic1 = newTopic(
                properties = TopicProperties(24, 2)
        )
        val topic2and3 = newTopic(
                properties = TopicProperties(3, 3)
        )
        val cluster1 = newCluster(clusterId = "id_1", identifier = "kfk_1").also { clusters.addCluster(it) }
        val cluster2 = newCluster(clusterId = "id_2", identifier = "kfk_2").also { clusters.addCluster(it) }
        val cluster3 = newCluster(clusterId = "id_3", identifier = "kfk_3").also { clusters.addCluster(it) }
        whenever(stateProvider.getLatestClusterState("kfk_1")).thenReturn(cluster1.newState(topic1))
        whenever(stateProvider.getLatestClusterState("kfk_2")).thenReturn(cluster2.newState(topic2and3))
        whenever(stateProvider.getLatestClusterState("kfk_3")).thenReturn(cluster3.newState(topic2and3))
        val importTopicDescription = suggestionService.suggestTopicImport(topic1.name)
        assertThat(importTopicDescription).isEqualTo(
            newTopic(
                properties = TopicProperties(3, 3),
                perClusterProperties = mapOf("kfk_1" to TopicProperties(24, 2))
            ).withEmptyOwnerDescriptionProducer()
        )
    }

    @Test
    fun `test common config goes to config, specific config goes to per cluster override`() {
        val cluster1 = newCluster(clusterId = "id_1", identifier = "kfk_1").also { clusters.addCluster(it) }
        val cluster2 = newCluster(clusterId = "id_2", identifier = "kfk_2").also { clusters.addCluster(it) }
        val cluster3 = newCluster(clusterId = "id_3", identifier = "kfk_3").also { clusters.addCluster(it) }
        val cluster4 = newCluster(clusterId = "id_4", identifier = "kfk_4").also { clusters.addCluster(it) }
        val cluster5 = newCluster(clusterId = "id_5", identifier = "kfk_5").also { clusters.addCluster(it) }
        val nonDefaultConfig = mapOf(
                "letter" to "c",
                "number" to "0",
                "retention" to "24h",
                "in-sync" to "2"
        )
        val state1 = cluster1.newState(
                newTopic(config = mapOf("letter" to "b", "number" to "2", "operator" to "-")),
                nonDefaultConfig = nonDefaultConfig.plus("retention" to "30d")
        )
        val state2 = cluster2.newState(
                newTopic(config = mapOf("letter" to "a", "number" to "3", "operator" to "+")),
                nonDefaultConfig = nonDefaultConfig.plus("foo" to "bar")
        )
        val state3 = cluster3.newState(
                newTopic(config = mapOf("letter" to "a", "number" to "2", "operator" to "+", "special" to "word")),
                nonDefaultConfig = nonDefaultConfig.plus("foo" to "baz")
        )
        val state4 = cluster4.newState(
                newTopic(config = mapOf("letter" to "a", "number" to "2", "operator" to "-")),
                nonDefaultConfig = nonDefaultConfig
        )
        val state5 = cluster5.newState(
                newTopic(config = mapOf("letter" to "c", "number" to "2", "operator" to "-")),
                nonDefaultConfig = nonDefaultConfig.plus("foo" to "baz")
        )
        whenever(stateProvider.getLatestClusterState("kfk_1")).thenReturn(state1)
        whenever(stateProvider.getLatestClusterState("kfk_2")).thenReturn(state2)
        whenever(stateProvider.getLatestClusterState("kfk_3")).thenReturn(state3)
        whenever(stateProvider.getLatestClusterState("kfk_4")).thenReturn(state4)
        whenever(stateProvider.getLatestClusterState("kfk_5")).thenReturn(state5)
        val importTopicDescription = suggestionService.suggestTopicImport(newTopic().name)
        assertThat(importTopicDescription).isEqualTo(
            newTopic(
                config = mapOf("letter" to "a", "number" to "2", "retention" to "24h", "in-sync" to "2", "operator" to "-"),
                perClusterConfigOverrides = mapOf(
                        "kfk_1" to mapOf("letter" to "b", "retention" to "30d"),
                        "kfk_2" to mapOf("number" to "3", "foo" to "bar", "operator" to "+"),
                        "kfk_3" to mapOf("foo" to "baz", "operator" to "+", "special" to "word"),
                        "kfk_5" to mapOf("letter" to "c", "foo" to "baz")    //letter needs to be overridden back to c
                )
            ).withEmptyOwnerDescriptionProducer()
        )
        whenever(stateProvider.listAllLatestClusterStates()).thenReturn(listOf(state1, state2, state3, state4, state5))
        assertThat(existingValuesService.allExistingValues().commonTopicConfig).containsAllEntriesOf(mapOf(
                "letter" to ConfigValue("a", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "number" to ConfigValue("2", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "retention" to ConfigValue("24h", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "in-sync" to ConfigValue("2", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "operator" to ConfigValue("-", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "special" to ConfigValue("word", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "foo" to ConfigValue("baz", default = false, readOnly = false, sensitive = false, DYNAMIC_TOPIC_CONFIG),
                "compression.type" to ConfigValue("producer", default = true, readOnly = false, sensitive = false, DEFAULT_CONFIG),
                "delete.retention.ms" to ConfigValue("86400000", default = true, readOnly = false, sensitive = false, DEFAULT_CONFIG)
        ))
    }

    @Test
    fun `test update for new cluster with small num brokers and violating replication factor rule`() {
        val topic = newTopic(
                properties = TopicProperties(6, 3),
                config = mapOf("min.insync.replicas" to "4")
        )
        val cluster1 = newCluster(clusterId = "id_1", identifier = "kfk_1").also { clusters.addCluster(it) }
        val cluster2 = newCluster(clusterId = "id_2", identifier = "kfk_2").also { clusters.addCluster(it) }
        val cluster3 = newCluster(clusterId = "id_3", identifier = "kfk_3").also { clusters.addCluster(it) }
        whenever(stateProvider.getLatestClusterState("kfk_1")).thenReturn(cluster1.newState(numBrokers = 3))
        whenever(stateProvider.getLatestClusterState("kfk_2")).thenReturn(cluster2.newState(numBrokers = 3))
        whenever(stateProvider.getLatestClusterState("kfk_3")).thenReturn(cluster3.newState(numBrokers = 2))
        topics.createTopic(topic)
        val fixedTopicDescription = suggestionService.suggestFixRuleViolations(topic.name)
        assertThat(fixedTopicDescription).isEqualTo(
            newTopic(
                properties = TopicProperties(6, 3),
                perClusterProperties = mapOf("kfk_3" to TopicProperties(6, 2)),
                config = mapOf("min.insync.replicas" to "2"),
                perClusterConfigOverrides = mapOf(
                        "kfk_3" to mapOf("min.insync.replicas" to "1")
                )
            )
        )
    }

    @Test
    fun `test don't include message-format if clusters config is non-default`() {
        val topic = newTopic(
                config = mapOf("retention.ms" to "1234")
        )
        val cluster = newCluster(clusterId = "id", identifier = "kfk_id").also { clusters.addCluster(it) }
        whenever(stateProvider.getLatestClusterState("kfk_id")).thenReturn(cluster.newState(
                topic.copy(config = topic.config.plus("message.format.version" to "2.1-IV")),
                clusterConfig = mapOf(
                        "log.message.format.version" to ConfigValue("2.1", default = false, readOnly = true, sensitive = false, DYNAMIC_TOPIC_CONFIG)
                )
        ))
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(topic.withEmptyOwnerDescriptionProducer())
    }

    @Test
    fun `test include message-format if clusters config is differs from actual`() {
        val topic = newTopic(
                config = mapOf("retention.ms" to "1234")
        )
        val cluster = newCluster(clusterId = "id", identifier = "kfk_id").also { clusters.addCluster(it) }
        whenever(stateProvider.getLatestClusterState("kfk_id")).thenReturn(cluster.newState(
                topic.copy(config = topic.config.plus("message.format.version" to "2.1-IV")),
                clusterConfig = mapOf(
                        "log.message.format.version" to ConfigValue("2.2", default = true, readOnly = true, sensitive = false, DYNAMIC_TOPIC_CONFIG)
                )
        ))
        val importTopicDescription = suggestionService.suggestTopicImport(topic.name)
        assertThat(importTopicDescription).isEqualTo(
            topic
                .copy(config = topic.config.plus("message.format.version" to "2.1-IV"))
                .withEmptyOwnerDescriptionProducer()
        )
    }

    @Test
    fun `test suggest update - no changes`() {
        val topic = newTopic(
                config = mapOf("retention.ms" to "1234")
        )
        val cluster = newCluster(clusterId = "id", identifier = "kfk_id").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("kfk_id")).thenReturn(cluster.newState(topic))
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(topic)
    }

    @Test
    fun `test suggest update - fix properties and config`() {
        val topic = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("retention.ms" to "1234")
        )
        val cluster = newCluster(clusterId = "id", identifier = "kfk_id").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("kfk_id")).thenReturn(cluster.newState(
                topic.copy(config = mapOf("retention.ms" to "5678"), properties = TopicProperties(3,3))
        ))
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(
            newTopic(
                properties = TopicProperties(3, 3),
                config = mapOf("retention.ms" to "5678")
            )
        )
    }

    @Test
    fun `test suggest update - no update - preserve config on disabled clusters`() {
        val topic = newTopic(
                config = mapOf("retention.ms" to "1234")
        )
        val clusterVisible = newCluster(clusterId = "id_v", identifier = "kfk_id_v").also { clusters.addCluster(it) }
        val clusterDisabled = newCluster(clusterId = "id_d", identifier = "kfk_id_d").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("kfk_id_v")).thenReturn(clusterVisible.newState(topic))
        whenever(stateProvider.getLatestClusterState("kfk_id_d")).thenReturn(clusterDisabled.newState(stateType = StateType.DISABLED))
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(topic)
    }

    @Test
    fun `test suggest update - preserve config on disabled clusters`() {
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("id_1", "id_2", "id_4", "id_d1")),
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555"),
                perClusterConfigOverrides = mapOf(
                        "id_d1" to mapOf("foo" to "bar"),
                        "id_d2" to mapOf("ping" to "pong"),
                        "id_4" to mapOf("x" to "y")
                )
        )
        val cluster1 = newCluster(clusterId = "id_1", identifier = "id_1").also { clusters.addCluster(it) }
        val cluster2 = newCluster(clusterId = "id_2", identifier = "id_2").also { clusters.addCluster(it) }
        val cluster3 = newCluster(clusterId = "id_3", identifier = "id_3").also { clusters.addCluster(it) }
        val cluster4 = newCluster(clusterId = "id_4", identifier = "id_4").also { clusters.addCluster(it) }
        val cluster5 = newCluster(clusterId = "id_5", identifier = "id_5").also { clusters.addCluster(it) }
        val cluster6 = newCluster(clusterId = "id_6", identifier = "id_6").also { clusters.addCluster(it) }
        val clusterDis1 = newCluster(clusterId = "id_d1", identifier = "id_d1").also { clusters.addCluster(it) }
        val clusterDis2 = newCluster(clusterId = "id_d2", identifier = "id_d2").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("id_1")).thenReturn(cluster1.newState(
            newTopic(
                config = mapOf("retention.ms" to "1111", "flush.ms" to "555")
            )
        ))
        whenever(stateProvider.getLatestClusterState("id_2")).thenReturn(cluster2.newState())
        whenever(stateProvider.getLatestClusterState("id_3")).thenReturn(cluster3.newState(
            newTopic(
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555")
            )
        ))
        whenever(stateProvider.getLatestClusterState("id_4")).thenReturn(cluster4.newState(
            newTopic(
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555", "x" to "y")
            )
        ))
        whenever(stateProvider.getLatestClusterState("id_d1")).thenReturn(clusterDis1.newState(stateType = StateType.DISABLED))
        whenever(stateProvider.getLatestClusterState("id_d2")).thenReturn(clusterDis2.newState(stateType = StateType.DISABLED))
        whenever(stateProvider.getLatestClusterState("id_5")).thenReturn(cluster5.newState())
        whenever(stateProvider.getLatestClusterState("id_6")).thenReturn(cluster6.newState())
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(
            newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("id_1", "id_3", "id_4", "id_d1")),
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555"),
                perClusterConfigOverrides = mapOf(
                        "id_1" to mapOf("retention.ms" to "1111"),
                        "id_d1" to mapOf("foo" to "bar"),
                        "id_4" to mapOf("x" to "y")
                )
            )
        )
    }

    @Test
    fun `test suggest update - add cluster with existing topic1`() {
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("id_d")),
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555")
        )
        val clusterDis = newCluster(clusterId = "id_d", identifier = "id_d").also { clusters.addCluster(it) }
        val clusterNew = newCluster(clusterId = "id_new", identifier = "id_new").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("id_d")).thenReturn(clusterDis.newState(stateType = StateType.DISABLED))
        whenever(stateProvider.getLatestClusterState("id_new")).thenReturn(clusterNew.newState(
            newTopic(
                config = mapOf("retention.ms" to "1234", "max.record.bytes" to "1024")
        )
        ))
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(
            newTopic(
                presence = Presence(PresenceType.ALL_CLUSTERS),
                config = mapOf("retention.ms" to "1234"),
                perClusterConfigOverrides = mapOf(
                        "id_d" to mapOf("flush.ms" to "555"),
                        "id_new" to mapOf("max.record.bytes" to "1024")
                )
            )
        )
    }

    @Test
    fun `test suggest update - add cluster with existing topic2`() {
        val topic = newTopic(
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("id_d")),
                config = mapOf("retention.ms" to "1234", "flush.ms" to "555")
        )
        val clusterDis = newCluster(clusterId = "id_d", identifier = "id_d").also { clusters.addCluster(it) }
        val clusterNew = newCluster(clusterId = "id_new", identifier = "id_new").also { clusters.addCluster(it) }
        topics.createTopic(topic)
        whenever(stateProvider.getLatestClusterState("id_d")).thenReturn(clusterDis.newState(stateType = StateType.DISABLED))
        whenever(stateProvider.getLatestClusterState("id_new")).thenReturn(clusterNew.newState(newTopic()))
        val updateTopicDescription = suggestionService.suggestTopicUpdate(topic.name)
        assertThat(updateTopicDescription).isEqualTo(
            newTopic(
                presence = Presence(PresenceType.ALL_CLUSTERS),
                config = mapOf(),
                perClusterConfigOverrides = mapOf(
                        "id_d" to mapOf("retention.ms" to "1234", "flush.ms" to "555")
                )
            )
        )
    }

}
