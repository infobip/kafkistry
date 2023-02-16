package com.infobip.kafkistry.it.ui

import com.nhaarman.mockitokotlin2.whenever
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.assertj.core.api.ObjectAssert
import org.assertj.core.groups.Tuple
import org.awaitility.Awaitility
import com.infobip.kafkistry.service.consume.*
import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.toKafkaCluster
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafka.KafkaTopicConfiguration
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.Presence
import com.infobip.kafkistry.model.PresenceType
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.repository.ChangeRequest
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.KafkaTopicsRepository
import com.infobip.kafkistry.repository.OptionalEntity
import com.infobip.kafkistry.repository.storage.Commit
import com.infobip.kafkistry.repository.storage.CommitChange
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.topic.propertiesForCluster
import org.jsoup.nodes.Document
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Function
import jakarta.annotation.PostConstruct

@RunWith(SpringRunner::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.topic-validation.disabled-rules=com.infobip.kafkistry.service.topic.validation.rules.ReplicationFactorOneRule",
        "app.topic-inspect.disabled-inspectors[0]=com.infobip.kafkistry.service.topic.inspectors.TopicNoConsumersInspector",
    ],
)
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("defaults", "it", "dir")
class UiRenderTest {

    @LocalServerPort
    var port = 0
    @Autowired
    lateinit var kafkaClientsProvider: KafkaClientProvider
    @SpyBean
    lateinit var topicsRepository: KafkaTopicsRepository

    companion object {

        @ClassRule
        @JvmField
        val kafka1 = EmbeddedKafkaRule(1).apply {
            brokerProperty("log.retention.bytes", "102400")
            brokerProperty("log.segment.bytes", "10240")
        }

        @ClassRule
        @JvmField
        val kafka2 = EmbeddedKafkaRule(3).apply {
            brokerProperty("log.retention.bytes", "102400")
            brokerProperty("log.segment.bytes", "10240")
        }

        val topic1 = newTopic(
                name = "topic_ok"
        )
        val topic2 = newTopic(
                name = "topic_missing"
        )
        val topic3 = newTopic(
                name = "topic_expected_missing",
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("c_1"))
        )
        val topic4 = newTopic(
                name = "topic_wrong_config",
                config = mapOf("retention.bytes" to "123456")
        )
        var topicsCreated = false

    }

    lateinit var api: ApiClient
    lateinit var cluster1: KafkaCluster
    lateinit var cluster2: KafkaCluster

    private fun ensureTopicsCreated() {
        if (topicsCreated) {
            return
        }
        topicsCreated = true
        with(cluster1) {
            createTopic(topic1)
            createTopic(topic3)
            createTopic(topic4.copy(config = emptyMap()))
        }
        with(cluster2) {
            createTopic(topic1)
            createTopic(topic4)
        }
        cluster1.awaitTopicsCreated(topic1, topic3, topic4)
        cluster2.awaitTopicsCreated(topic1, topic4)
    }

    @PostConstruct
    fun initialize() {
        api = ApiClient("localhost", port, "/kafkistry")
        val clusterInfo1 = api.testClusterConnection(kafka1.embeddedKafka.brokersAsString)
        val clusterInfo2 = api.testClusterConnection(kafka2.embeddedKafka.brokersAsString)
        cluster1 = clusterInfo1.toKafkaCluster().copy(identifier = "c_1", sslEnabled = false, saslEnabled = false)
        cluster2 = clusterInfo2.toKafkaCluster().copy(identifier = "c_2", sslEnabled = false, saslEnabled = false)
        ensureTopicsCreated()
    }

    private fun KafkaCluster.awaitTopicsCreated(vararg topics: TopicDescription) {
        Awaitility.await().timeout(10, TimeUnit.SECONDS).until {
            kafkaClientsProvider.doWithClient(this) { client ->
                client.listAllTopics().get().map { it.name }.containsAll(
                        topics.map { it.name }
                )
            }
        }
    }

    private fun KafkaCluster.getBrokerIds(): List<BrokerId> {
        return kafkaClientsProvider.doWithClient(this) { it.clusterInfo(identifier).get().nodeIds }
    }

    private fun KafkaCluster.createTopic(topic: TopicDescription) {
        val brokerIds = getBrokerIds()
        kafkaClientsProvider.doWithClient(this) {
            val topicProperties = topic.propertiesForCluster(ref())
            it.createTopic(KafkaTopicConfiguration(
                    name = topic.name,
                    partitionsReplicas = PartitionsReplicasAssignor().assignNewPartitionReplicas(
                            existingAssignments = emptyMap(),
                            allBrokers = brokerIds,
                            numberOfNewPartitions = topicProperties.partitionCount,
                            replicationFactor = topicProperties.replicationFactor,
                            existingPartitionLoads = emptyMap()
                    ).newAssignments,
                    config = topic.configForCluster(ref())
            )).get()
        }
    }

    private fun addAllClustersAndTopics() {
        api.addCluster(cluster1)
        api.addCluster(cluster2)
        api.addTopic(topic1)
        api.addTopic(topic2)
        api.addTopic(topic3)
        api.addTopic(topic4)
        api.refreshClusters()
    }

    private fun String.wordsSet() = Regex("\\w+").findAll(this).map { it.value }.toSet()

    @Before
    fun clearRepos() {
        val topics = api.listAllTopics()
        topics.forEach { api.deleteTopic(it.name) }
        val clusters = api.listAllClusters()
        clusters.forEach { api.deleteCluster(it.identifier) }
    }

    @Before
    fun resetMocks() {
        reset(topicsRepository)
    }

    fun assertNavMenuContents(path: String) {
        val page = api.getPage(path)
        val links = page.select("nav a").map { it.attr("href") }
        assertThat(links).containsOnly(
                "/kafkistry/home",
                "/kafkistry/topics",
                //"/kafkistry/history",    //not expected because regular directory is used instead git for storage
                "/kafkistry/acls",
                "/kafkistry/quotas",
                "/kafkistry/clusters",
                "/kafkistry/autopilot",
                "/kafkistry/consumer-groups",
                "/kafkistry/consume",
                "/kafkistry/records-structure",
                "/kafkistry/kstream-apps",
                "/kafkistry/sql",
                "/kafkistry/about",
        )
    }

    @Test
    fun `homePage - nav menu 1`() {
        assertNavMenuContents("/")
    }

    @Test
    fun `homePage - nav menu 2`() {
        assertNavMenuContents("/home")
    }

    @Test
    fun `homePage - render empty home page`() {
        val homePage = api.getPage("/")
        assertThat(homePage.select(".container").text()).contains(
            "Clusters", "Topics", "Consumers", "ACLs", "Quotas"
        )
    }

    @Test
    fun `allTopics - nav menu`() {
        assertNavMenuContents("/topics")
    }

    @Test
    fun `allTopics - no topics`() {
        val page = api.getPage("/topics")
        assertThat(page.select(".topic-row")).isEmpty()
    }

    @Test
    fun `allTopics - all added`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics")
        assertThat(page.select(".topic-row")).extracting(
                Function { it.selectFirst("a")?.text() },
                Function { it.selectFirst("a")?.attr("href") },
                Function { it.selectFirst("td:nth-child(4)")?.text()?.wordsSet() }
        ).containsOnly(
                tuple("topic_ok", "/kafkistry/topics/inspect?topicName=topic_ok", setOf("OK", "EMPTY")),
                tuple("topic_expected_missing", "/kafkistry/topics/inspect?topicName=topic_expected_missing", setOf("NOT_PRESENT_AS_EXPECTED", "OK", "EMPTY")),
                tuple("topic_missing", "/kafkistry/topics/inspect?topicName=topic_missing", setOf("MISSING")),
                tuple("topic_wrong_config", "/kafkistry/topics/inspect?topicName=topic_wrong_config", setOf("WRONG_CONFIG", "EMPTY", "OK")),
        )
    }

    private fun Document.topicPageClusterStatusesAssertion(): AbstractListAssert<*, MutableList<out Tuple>, Tuple, ObjectAssert<Tuple>> {
        return assertThat(select(".per-cluster-status-row")).extracting(
                Function { it.selectFirst("td:nth-child(1)")?.text()?.removeSuffix(" \uD83D\uDD0D") },   //remove magnifier symbol
                Function { it.selectFirst("td:nth-child(1) a")?.attr("href") },
                Function { it.select("td:nth-child(2) div.alert").text().wordsSet() }
        )
    }

    @Test
    fun `topicPage - no clusters`() {
        api.addTopic(newTopic("rogue_topic"))
        val page = api.getPage("/topics?topicName=rogue_topic")
        page.topicPageClusterStatusesAssertion().isEmpty()
    }

    @Test
    fun `topicPage - ok missing as expected`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/inspect?topicName=topic_expected_missing")
        page.topicPageClusterStatusesAssertion().containsOnly(
                tuple("c_1", "/kafkistry/topics/cluster-inspect?topicName=topic_expected_missing&clusterIdentifier=c_1", setOf("OK", "EMPTY")),
                tuple("c_2", "/kafkistry/topics/cluster-inspect?topicName=topic_expected_missing&clusterIdentifier=c_2", setOf("NOT_PRESENT_AS_EXPECTED"))
        )
    }

    @Test
    fun `topicPage - missing`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/inspect?topicName=topic_missing")
        page.topicPageClusterStatusesAssertion().containsOnly(
            tuple("c_1", "/kafkistry/topics/cluster-inspect?topicName=topic_missing&clusterIdentifier=c_1", setOf("MISSING")),
            tuple("c_2", "/kafkistry/topics/cluster-inspect?topicName=topic_missing&clusterIdentifier=c_2", setOf("MISSING"))
        )
    }

    @Test
    fun `topicPage - wrong config`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/inspect?topicName=topic_wrong_config")
        page.topicPageClusterStatusesAssertion().containsOnly(
            tuple("c_1", "/kafkistry/topics/cluster-inspect?topicName=topic_wrong_config&clusterIdentifier=c_1", setOf("WRONG_CONFIG", "EMPTY")),
            tuple("c_2", "/kafkistry/topics/cluster-inspect?topicName=topic_wrong_config&clusterIdentifier=c_2",  setOf("OK", "EMPTY"))
        )
    }

    @Test
    fun `topicPage - unknown topic`() {
        api.addCluster(cluster1)
        api.addCluster(cluster2)
        api.refreshClusters()
        val page = api.getPage("/topics/inspect?topicName=topic_wrong_config")
        page.topicPageClusterStatusesAssertion().containsOnly(
                tuple("c_1", "/kafkistry/topics/cluster-inspect?topicName=topic_wrong_config&clusterIdentifier=c_1", setOf("UNKNOWN", "EMPTY")),
                tuple("c_2", "/kafkistry/topics/cluster-inspect?topicName=topic_wrong_config&clusterIdentifier=c_2", setOf("UNKNOWN", "EMPTY"))
        )
    }

    @Test
    fun `topicsPage - having all unknown - no topics in registry`() {
        api.addCluster(cluster1)
        api.addCluster(cluster2)
        api.refreshClusters()
        val page = api.getPage("/topics")
        assertThat(page.select(".topic-row")).extracting(
            Function { it.selectFirst("a")?.text() },
            Function { it.selectFirst("a")?.attr("href") },
            Function { it.selectFirst("td:nth-child(4)")?.text()?.wordsSet() }
        ).containsOnly(
            tuple("topic_ok", "/kafkistry/topics/inspect?topicName=topic_ok", setOf("UNKNOWN", "EMPTY")),
            tuple("topic_expected_missing", "/kafkistry/topics/inspect?topicName=topic_expected_missing", setOf("UNKNOWN", "EMPTY")),
            tuple("topic_wrong_config", "/kafkistry/topics/inspect?topicName=topic_wrong_config", setOf("UNKNOWN", "EMPTY")),
        )
    }

    @Test
    fun `allClustersPage - no clusters`() {
        val page = api.getPage("/clusters")
        assertThat(page.select(".cluster-row")).isEmpty()
    }

    @Test
    fun `allClustersPage - 2 clusters`() {
        addAllClustersAndTopics()
        val page = api.getPage("/clusters")
        assertThat(page.select(".cluster-row")).extracting(
                Function { it.selectFirst("td:nth-child(1) a")?.text() },
                Function { it.selectFirst("td:nth-child(1) a")?.attr("href") },
                Function { it.selectFirst("td:nth-child(3) div.alert")?.text()?.wordsSet() },
        ).containsOnly(
                tuple("c_1", "/kafkistry/clusters/inspect?clusterIdentifier=c_1", setOf("VISIBLE")),
                tuple("c_2", "/kafkistry/clusters/inspect?clusterIdentifier=c_2", setOf("VISIBLE")),
        )
    }

    private fun Document.clusterPageTopicStatusesAssertion(): AbstractListAssert<*, MutableList<out Tuple>, Tuple, ObjectAssert<Tuple>> {
        return assertThat(select(".topic-row")).extracting(
                Function { it.select("td:nth-child(1)").text().removeSuffix(" \uD83D\uDD0D") },   //remove magnifier symbol
                Function { it.select("td:nth-child(2) div.alert").text().wordsSet() }
        )
    }

    @Test
    fun `clusterPage - c1`() {
        addAllClustersAndTopics()
        val page = api.getPage("/clusters/inspect/topics?clusterIdentifier=c_1")
        page.clusterPageTopicStatusesAssertion().containsOnly(
            tuple(topic1.name, setOf("OK", "EMPTY")),
            tuple(topic2.name, setOf("MISSING")),
            tuple(topic3.name, setOf("OK", "EMPTY")),
            tuple(topic4.name, setOf("WRONG_CONFIG", "EMPTY")),
        )
    }

    @Test
    fun `clusterPage - c1, no topics in registry`() {
        api.addCluster(cluster1)
        api.refreshClusters()
        val page = api.getPage("/clusters/inspect/topics?clusterIdentifier=c_1")
        page.clusterPageTopicStatusesAssertion().containsOnly(
            tuple(topic1.name, setOf("UNKNOWN", "EMPTY")),
            tuple(topic3.name, setOf("UNKNOWN", "EMPTY")),
            tuple(topic4.name, setOf("UNKNOWN", "EMPTY"))
        )
    }

    @Test
    fun `clusterPage - c2`() {
        addAllClustersAndTopics()
        val page = api.getPage("/clusters/inspect/topics?clusterIdentifier=c_2")
        page.clusterPageTopicStatusesAssertion().containsOnly(
            tuple(topic1.name, setOf("OK", "EMPTY")),
            tuple(topic2.name, setOf("MISSING")),
            tuple(topic3.name, setOf("NOT_PRESENT_AS_EXPECTED")),
            tuple(topic4.name, setOf("OK", "EMPTY"))
        )
    }

    @Test
    fun `inspect topic on cluster page - test all load`() {
        addAllClustersAndTopics()
        for (topic in listOf(topic1, topic2, topic3, topic4)) {
            for (cluster in listOf(cluster1, cluster2)) {
                val page = api.getPage("/topics/inspect?topicName=${topic.name}&clusterIdentifier=${cluster.identifier}")
                assertThat(page.text()).contains(
                        topic.name, cluster.identifier
                )
            }
        }
    }

    @Test
    fun `create new topic page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/create")
        assertThat(page.text()).contains("Create Topic")
    }

    @Test
    fun `clone create new topic page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/clone-add-new?topicName=${topic1.name}")
        assertThat(page.text()).contains("Create new topic by clone")
    }

    @Test
    fun `manual edit topic page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/edit?topicName=${topic1.name}")
        assertThat(page.text()).contains("Edit topic")
    }

    @Test
    fun `suggest edit topic page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/suggest-edit?topicName=${topic1.name}")
        assertThat(page.text()).contains("Suggested edit to match current state on clusters")
    }

    @Test
    fun `fix-violations-edit topic page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/fix-violations-edit?topicName=${topic1.name}")
        assertThat(page.text()).contains("Edit with generated fixes for validation rules")
    }

    @Test
    fun `import topic page`() {
        api.addCluster(cluster1)
        api.addCluster(cluster2)
        api.refreshClusters()
        val page = api.getPage("/topics/import?topicName=${topic1.name}")
        assertThat(page.text()).contains("Import topic suggestion")
    }

    @Test
    fun `pending change edit topic page`() {
        whenever(topicsRepository.findPendingRequestsById(topic1.name)).thenReturn(listOf(
                ChangeRequest(
                        type = ChangeType.ADD,
                        commitChanges = listOf(CommitChange(Commit("abcdef1234567890", false, "atest", 150000000, "msg"), ChangeType.UPDATE, "old", "new")),
                        branch = "test-branch",
                        optionalEntity = OptionalEntity.of(topic1)
                )
        ))
        val page = api.getPage("/topics/edit-on-branch?topicName=${topic1.name}&branch=test-branch")
        assertThat(page.text()).contains("Edit pending topic request")
    }

    @Test
    fun `topic wizard page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/topics/wizard")
        assertThat(page.text()).contains("Create Topic Wizard")
        assertThat(page.text()).contains("Topic name")
        assertThat(page.text()).contains("Presence")

        assertThat(page.select("#presence select[name=selectedClusters] option").eachAttr("value"))
                .containsExactly("c_1", "c_2")
    }

    @Test
    fun `consume page`() {
        addAllClustersAndTopics()
        val page = api.getPage("/consume")
        assertThat(page.text()).contains("Start reading")
    }

    @Test
    fun `consume records ajax`() {
        addAllClustersAndTopics()
        KafkaProducer(
                Properties().also { props ->
                    props["bootstrap.servers"] = cluster1.connectionString
                },
                StringSerializer(),
                ByteArraySerializer()
        ).use { producer ->
            records(topic1.name)
                    .map { it.toProducerRecord() }
                    .map { producer.send(it) }
                    .also { producer.flush() }
                    .forEach { it.get(2, TimeUnit.SECONDS) }
        }
        val page = api.postPage(
                "/consume/read-topic?clusterIdentifier=${cluster1.identifier}&topicName=${topic1.name}",
                ReadConfig(
                        numRecords = 6,
                        partitions = null,
                        notPartitions = null,
                        fromOffset = Offset(OffsetType.EARLIEST, 0),
                        partitionFromOffset = null,
                        maxWaitMs = 2000,
                        waitStrategy = WaitStrategy.WAIT_NUM_RECORDS,
                        readOnlyCommitted = false,
                        recordDeserialization = RecordDeserialization.ANY,
                        readFilter = ReadFilter.EMPTY,
                )
        )
        assertThat(page.text()).startsWith("Got 6 record(s)")
        assertThat(page.select(".record")).hasSize(6)
    }

}
