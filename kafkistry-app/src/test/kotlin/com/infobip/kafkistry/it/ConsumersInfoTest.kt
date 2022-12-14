package com.infobip.kafkistry.it

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.ui.ApiClient
import com.infobip.kafkistry.kafka.ConsumerGroupStatus.EMPTY
import com.infobip.kafkistry.kafka.ConsumerGroupStatus.STABLE
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.kafkastate.KafkaTopicOffsetsProvider
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consumers.KafkaConsumerGroup
import com.infobip.kafkistry.service.poolAll
import com.infobip.kafkistry.service.toKafkaCluster
import com.infobip.kafkistry.TestDirsPathInitializer
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import jakarta.annotation.PostConstruct

@RunWith(SpringRunner::class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ["ALLOW_ACCESS_TO_CONSUMER_GROUPS_NO_OWNERS=true"],
)
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("defaults", "it", "dir")
class ConsumersInfoTest {

    companion object {
        @ClassRule
        @JvmField
        val kafka = EmbeddedKafkaRule(1, false, "test-topic")

        val log = LoggerFactory.getLogger(ConsumersInfoTest::class.java)!!
    }

    @LocalServerPort
    var port = 0

    lateinit var api: ApiClient

    @Autowired
    lateinit var consumerGroupsProvider: KafkaConsumerGroupsProvider

    @Autowired
    lateinit var topicOffsetsProvider: KafkaTopicOffsetsProvider

    @PostConstruct
    fun initialize() {
        api = ApiClient("localhost", port, "/kafkistry")
        val clusterInfo = api.testClusterConnection(kafka.embeddedKafka.brokersAsString)
        val cluster = clusterInfo.toKafkaCluster().copy(identifier = "kfk-test")
        api.addCluster(cluster)
    }

    private fun refreshClustersStates() {
        consumerGroupsProvider.refreshClustersStates()
        topicOffsetsProvider.refreshClustersStates()
    }

    @Test
    fun `test consumerGroup lifecycle - creation, consuming, deletion`() {
        //expecting no consumers
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .also {
                    assertThat(it.map { it.groupId to it.status }).isEmpty()
                    assertThat(it.flat()).isEmpty()
                    assertThat(it).isEmpty()
                }

        val consumer = createConsumerAndSubscribe()

        //expecting consumer with unknown offsets/lag
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).containsExactlyInAnyOrder("kafkistry-test" to STABLE)
                    assertThat(flat()).containsExactlyInAnyOrder(
                            tuple("kafkistry-test", "test-topic", null, 0, null, null),
                            tuple("kafkistry-test", "test-topic", null, 1, null, null)
                    )
                }

        consumer.commitSync()

        //expecting that consumer is now at end of empty topic
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).containsExactlyInAnyOrder("kafkistry-test" to STABLE)
                    assertThat(flat()).containsExactlyInAnyOrder(
                            tuple("kafkistry-test", "test-topic", 0L, 0, 0L, 0L),
                            tuple("kafkistry-test", "test-topic", 0L, 1, 0L, 0L)
                    )
                }

        produceToTopic("test-topic", 0)
        produceToTopic("test-topic", 1)
        produceToTopic("test-topic", 1)
        produceToTopic("test-topic", 1)

        //expecting that consumer has lag when new records are produced
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).containsExactlyInAnyOrder("kafkistry-test" to STABLE)
                    assertThat(flat()).containsExactlyInAnyOrder(
                            tuple("kafkistry-test", "test-topic", 4L, 0, 0L, 1L),
                            tuple("kafkistry-test", "test-topic", 4L, 1, 0L, 3L)
                    )
                }

        val records = consumer.poolAll()
        assertThat(records).hasSize(4)
        consumer.commitSync()

        //expecting that there is no lag because consumer "consumed" all records
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).containsExactlyInAnyOrder("kafkistry-test" to STABLE)
                    assertThat(flat()).containsExactlyInAnyOrder(
                            tuple("kafkistry-test", "test-topic", 0L, 0, 1L, 0L),
                            tuple("kafkistry-test", "test-topic", 0L, 1, 3L, 0L)
                    )
                }

        consumer.close()

        //expecting that lag is same but consumer is now empty because it's closed/disconnected
        //assignment information it's still visible because of caching
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).containsExactlyInAnyOrder("kafkistry-test" to EMPTY)
                    assertThat(flat()).containsExactlyInAnyOrder(
                            tuple("kafkistry-test", "test-topic", 0L, 0, 1L, 0L),
                            tuple("kafkistry-test", "test-topic", 0L, 1, 3L, 0L)
                    )
                }

        api.deleteClusterConsumerGroup("kfk-test", "kafkistry-test")

        //expecting no consumers because it(s deleted
        refreshClustersStates()
        api.listClusterConsumerGroups("kfk-test").consumerGroups
                .apply {
                    assertThat(map { it.groupId to it.status }).isEmpty()
                    assertThat(flat()).isEmpty()
                    assertThat(this).isEmpty()
                }
    }

    private fun List<KafkaConsumerGroup>.flat(): List<List<Any?>> {
        return this.flatMap { group ->
            group.topicMembers.flatMap { topicMembers ->
                topicMembers.partitionMembers.map { partitionMember ->
                    listOf(
                            group.groupId,
                            topicMembers.topicName, topicMembers.lag.amount,
                            partitionMember.partition, partitionMember.offset, partitionMember.lag.amount)
                }
            }
        }
    }

    private fun tuple(
        group: ConsumerGroupId,
        topic: TopicName, topicLag: Long?,
        partition: Partition, offset: Long?, partitionLag: Long?
    ) = listOf(group, topic, topicLag, partition, offset, partitionLag)

    private fun createConsumerAndSubscribe(): KafkaConsumer<String, ByteArray> {
        val props = Properties().also { props ->
            props[ConsumerConfig.GROUP_ID_CONFIG] = "kafkistry-test"
            props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.embeddedKafka.brokersAsString
            props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "10"
            props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            props[ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG] = "2000"
            props[ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "2000"
            props[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = "20000"
            props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = "30000"
        }
        val consumer = KafkaConsumer(props, StringDeserializer(), ByteArrayDeserializer())
        log.info("Subscribing to topic")
        consumer.subscribe(listOf("test-topic"))
        consumer.poll(Duration.ofSeconds(2))
        log.info("Subscribed to ${consumer.assignment()}")
        return consumer
    }

    private fun produceToTopic(topic: TopicName, partition: Partition) {
        val props = Properties().also { props ->
            props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.embeddedKafka.brokersAsString
        }
        KafkaProducer(props, StringSerializer(), StringSerializer()).use {
            it.send(ProducerRecord(
                    topic,
                    partition,
                    null,
                    null,
                    "value"
            )).get(2, TimeUnit.SECONDS)
        }
    }

}