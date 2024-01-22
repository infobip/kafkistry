package com.infobip.kafkistry

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.util.Files
import com.infobip.kafkistry.it.ui.ApiClient
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetric
import com.infobip.kafkistry.kafkastate.brokerdisk.BrokerDiskMetricsProvider
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.newQuota
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.acl.toAclRule
import com.infobip.kafkistry.service.toKafkaCluster
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.UserRole
import com.infobip.kafkistry.webapp.security.auth.preauth.PreAuthUserResolver
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.get
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.Duration
import java.util.*
import jakarta.servlet.http.HttpServletRequest
import org.apache.kafka.common.config.TopicConfig
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import kotlin.random.Random

/**
 * Start instance of kafkistry on `http://localhost:8081`
 * Username/password: admin/admin or user/user
 * An embedded kafka cluster is also started and added as cluster to KR which serves as play-ground.
 * Some dummy data (topics, acls, quotas) is also added to KR.
 */
fun main(args: Array<String>) {
    manualMain(Kafkistry::class.java, args)
}

fun manualMain(
    configClass: Class<*>,
    args: Array<String>,
    extraProfiles: List<String> = emptyList(),
    extraProperties: List<String> = emptyList(),
    extraInitializers: List<ApplicationContextInitializer<ConfigurableApplicationContext>> = emptyList()
) {
    SpringApplicationBuilder()
        .sources(configClass, DataStateInitializer::class.java)
        .profiles(*listOf("defaults", "it", "dev", "manual").plus(extraProfiles).toTypedArray())
        .properties(
            *listOf(
                "HTTP_PORT=8081",
                "HAZELCAST_DISCOVERY_TYPE=STATIC_IPS",
                "USERS_PASSWORDS=" + """
                        admin|admin|Admy|Adminsky|admin@kafkistry.local|ADMIN|
                        user|user|Usy|Usersky|user@kafkistry.local|USER|
                    """.trimIndent(),
                "OWNER_GROUPS=test-owner|user",
                "HTTP_ROOT_PATH=",
                "GIT_COMMIT_TO_MASTER_BY_DEFAULT=true",
                "DISABLED_CLUSTERS=local-disabled",
                "app.masking.rules.test.target.topics.included=topic-example-4",
                "app.masking.rules.test.value-json-paths=secret",
            ).plus(extraProperties).toTypedArray()
        )
        .initializers(
            *listOf(
                TestDirsPathInitializer(),
            ).plus(extraInitializers).toTypedArray()
        )
        .run(*args)
}

fun ConfigurableApplicationContext.localServerPort() = environment["local.server.port"]?.toInt()
    ?: throw RuntimeException("Missing local.server.port in environment")

fun ConfigurableApplicationContext.httpRootPath() = environment["app.http.root-path"].toString()

fun ConfigurableApplicationContext.createAdminApiClient(): ApiClient {
    val serverPort = localServerPort()
    val rootPath = httpRootPath()
    return ApiClient("localhost", serverPort, rootPath, "mock-role=ADMIN")
}

abstract class OrderedApplicationRunner(private val order: Int = 0) : ApplicationRunner, Ordered {
    override fun getOrder(): Int = order
}

@Configuration
@Order(0)
@Profile("manual")
class DataStateInitializer(
    private val ctx: ConfigurableApplicationContext,
    private val kafkaClientProvider: KafkaClientProvider,
    private val partitionsReplicasAssignor: PartitionsReplicasAssignor,
    private val consumerGroupsProvider: KafkaConsumerGroupsProvider
) : OrderedApplicationRunner() {

    private val log = LoggerFactory.getLogger("manual-init")
    private lateinit var api: ApiClient

    private val kafka = EmbeddedKafkaRule(6)
        .brokerProperty("log.retention.bytes", "123456789")
        .brokerProperty("log.segment.bytes", "12345678")
        .brokerProperty("authorizer.class.name", kafka.security.authorizer.AclAuthorizer::class.java.name)
        .brokerProperty("super.users", "User:ANONYMOUS")
        .also {
            log.info("EmbeddedKafka starting...")
            it.before()
            log.info("EmbeddedKafka started: {}", it.embeddedKafka.brokersAsString)
        }

    private fun <T> doRetrying(retries: Int = 5, operation: () -> T): T? {
        var attemptsLeft = retries
        while (true) {
            try {
                return operation()
            } catch (ex: Exception) {
                attemptsLeft--
                if (attemptsLeft <= 0) {
                    log.warn("exhausted re-try attempts", ex)
                    return null
                }
                Thread.sleep(500)
            }
        }
    }

    private fun reAssign(
        cluster: KafkaCluster,
        topicName: TopicName,
        partitions: Int,
        replication: Int,
        brokers: List<BrokerId>
    ) {
        val assignments = assignRoundRobin(brokers, partitions, replication)
        reAssign(cluster, topicName, assignments)
    }

    private fun assignRoundRobin(
        brokers: List<BrokerId>,
        partitions: Int,
        replication: Int
    ): Map<Partition, List<BrokerId>> {
        return partitionsReplicasAssignor.assignNewPartitionReplicas(
            existingAssignments = emptyMap(),
            allBrokers = brokers,
            numberOfNewPartitions = partitions,
            replicationFactor = replication,
            existingPartitionLoads = emptyMap(),
            clusterBrokersLoad = emptyMap()
        ).newAssignments
    }

    private fun reAssign(cluster: KafkaCluster, topicName: TopicName, assignments: Map<Partition, List<BrokerId>>) {
        kafkaClientProvider.doWithClient(cluster) {
            it.reAssignPartitions(topicName, assignments, 666_666).get()
            while (true) {
                val result = it.verifyReAssignPartitions(topicName, assignments)
                if ("Throttle was removed" in result) {
                    break
                }
            }
            try {
                api.electPreferredLeaders(topicName, cluster.identifier)
            } catch (_: Exception) {

            }
        }
    }

    @Bean
    fun mockRolePreAuthentication() = object : PreAuthUserResolver {
        override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): User? {
            return request.cookies
                ?.firstOrNull { it.name == "mock-role" }
                ?.value
                ?.let { User("Test", "Test", "Test", "Test", UserRole.valueOf(it)) }
        }
    }

    @Bean
    fun mockBrokerDiskMetrics() = object : BrokerDiskMetricsProvider {
        override fun brokersDisk(
            clusterIdentifier: KafkaClusterIdentifier, brokers: List<ClusterBroker>
        ): Map<BrokerId, BrokerDiskMetric> {
            val (total, free) = File(Files.temporaryFolderPath()).let {
                it.totalSpace to it.freeSpace
            }
            return brokers.associate { it.brokerId to BrokerDiskMetric(total, free) }
        }
    }

    class PartitionSelector(partitions: Int, min: Double) {
        val scores = run {
            val step = (1 - min) / (partitions - 1)
            generateSequence(1.0) { it - step }
                .take(partitions)
                .runningReduce(Double::plus)
                .mapIndexed { index, score -> score to index }
                .toMap(TreeMap())
        }

        fun nextPartition(): Partition {
            val value = Random.nextDouble(scores.lastKey())
            return scores.ceilingEntry(value).value
        }
    }

    override fun run(args: ApplicationArguments?) {
        api = ctx.createAdminApiClient()
        val serverPort = ctx.localServerPort()
        val rootPath = ctx.httpRootPath()
        log.info("KR started on http://localhost:$serverPort$rootPath")
        val clusterIdentifier = "kafka-${System.getProperty("user.name")}-pc"
        val cluster = api.testClusterConnection(kafka.embeddedKafka.brokersAsString)
            .copy(identifier = clusterIdentifier)
            .toKafkaCluster()
            .copy(tags = listOf("home", "test"))

        log.info("Cluster adding... {}", cluster)
        api.addCluster(cluster)
        log.info("Cluster added, refreshing... {}", cluster)
        api.refreshClusters()
        log.info("Clusters refreshed")

        with(
            newQuota(
                entity = QuotaEntity.user("bob"),
                properties = QuotaProperties(2345, 6543, 333.3),
            )
        ) {
            log.info("Adding entity quotas {}", this)
            api.createEntityQuotas(this)
            log.info("Creating entity quotas {}", entity.asID())
            api.createMissingEntityQuotas(entity.asID(), clusterIdentifier)
            log.info("Done with entity quotas {}", entity.asID())
        }

        with(
            newQuota(
                entity = QuotaEntity.user("alice"),
                properties = QuotaProperties(consumerByteRate = 666_666),
            )
        ) {
            log.info("Adding entity quotas {}", this)
            api.createEntityQuotas(this)
            log.info("Done with entity quotas {}", entity.asID())
        }

        with(
            newQuota(
                entity = QuotaEntity.userClient("wrong", "wrong-instance-1"),
                properties = QuotaProperties(producerByteRate = 1024_000, requestPercentage = 150.0),
            )
        ) {
            log.info("Adding entity quotas {}", this)
            api.createEntityQuotas(this)
            log.info("Setting wrong quotas for {}", entity)
            kafkaClientProvider.doWithClient(cluster) {
                it.setClientQuotas(listOf(ClientQuota(entity, QuotaProperties(producerByteRate = 512_000))))
            }
            log.info("Done with entity quotas {}", entity.asID())
        }

        with(
            newQuota(
                entity = QuotaEntity.userDefault(),
                properties = QuotaProperties(producerByteRate = 1L shl 21, consumerByteRate = 1L shl 22),
            )
        ) {
            log.info("Setting unknown quotas for {}", entity)
            kafkaClientProvider.doWithClient(cluster) {
                it.setClientQuotas(listOf(ClientQuota(entity, properties)))
            }
            log.info("Done with entity quotas {}", entity.asID())
        }

        with("topic-unknown-1") {
            log.info("Creating unknown topic {}", this)
            kafkaClientProvider.doWithClient(cluster) {
                val assignments = partitionsReplicasAssignor.assignNewPartitionReplicas(
                    emptyMap(), listOf(1, 2, 3), 10, 2, emptyMap()
                )
                it.createTopic(KafkaTopicConfiguration(this, assignments.newAssignments, emptyMap()))
            }
            log.info("Done with topic {}", this)
        }

        with(
            newTopic(
                name = "topic-example-1",
                properties = TopicProperties(1, 3),
                description = "Task: add 5 more partitions to this topic",
                labels = listOf(
                    Label("area", "51"),
                    Label("product", "military"),
                ),
                freezeDirectives = listOf(
                    FreezeDirective(
                        "Captain's direct orders",
                        partitionCount = true,
                        configProperties = listOf(
                            TopicConfig.MAX_MESSAGE_BYTES_CONFIG,
                            TopicConfig.RETENTION_MS_CONFIG,
                        ),
                    )
                )
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            log.info("Done with topic {}", name)
        }

        with(
            newTopic(
                name = "topic-example-2",
                properties = TopicProperties(6, 1),
                description = "Task: increase replication factor from 1 to 3"
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            log.info("Done with topic {}", name)
        }

        with(
            newTopic(
                name = "topic-example-3",
                properties = TopicProperties(4, 3),
                description = "Task: rebalance leaders of this topic"
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            val assignments = assignRoundRobin((0 until 6).toList(), 4, 3)
                .asSequence()
                .mapIndexed { index, entry ->
                    if (index % 2 == 0) {
                        entry.key to entry.value.sorted()
                    } else {
                        entry.key to entry.value.sorted().reversed()
                    }
                }
                .associate { it }
            log.info("Reassigning topic {}: {}", name, assignments)
            doRetrying {
                reAssign(cluster, name, assignments)
            }
            log.info("Done with topic {}", name)
        }

        with(
            newTopic(
                name = "topic-example-4",
                properties = TopicProperties(6, 3),
                description = "Task: rebalance this topic (assume it was created while cluster had 4 nodes and now there is 6 nodes)"
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            log.info("Reassigning topic {}", name)
            doRetrying {
                reAssign(cluster, name, 6, 3, (0..3).toList())
            }
            val producerConfig = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.embeddedKafka.brokersAsString
            )
            log.info("Producing to topic {}", name)
            KafkaProducer(producerConfig, StringSerializer(), StringSerializer()).use { producer ->
                (0..5_000)
                    .map {
                        val value = if (it % 10 != 0) {
                            """{"id":$it,"msg":"Dummy message $it","secret":$it}"""
                        } else {
                            "this is not a json $it"
                        }
                        ProducerRecord(name, "key_$it", value)
                    }
                    .map { producer.send(it) }
                    .also { producer.flush() }
                    .forEach { it.get() }
            }
            log.info("Done with topic {}", name)
        }

        with(
            newTopic(
                name = "topic-example-5-whatever",
                properties = TopicProperties(1, 1),
                description = "Task: do whatever you feel like doing :D\n" +
                        "- increase partition count by bit, and after that increase replication factor, then again increase partition count..."
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            log.info("Done with topic {}", name)
        }

        with(
            newTopic(
                name = "topic-with-records",
                properties = TopicProperties(6, 4),
                description = "Topic populated with some records"
            )
        ) {
            log.info("Adding topic {}", this)
            api.addTopic(this)
            log.info("Creating missing topic {}", name)
            doRetrying {
                api.createMissingTopic(name, clusterIdentifier)
            }
            val producerConfig = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.embeddedKafka.brokersAsString
            )
            log.info("Producing to topic {}", name)
            KafkaProducer(producerConfig, StringSerializer(), StringSerializer()).use { producer ->
                val selector = PartitionSelector(6, 0.5)
                (0..10_000)
                    .map {
                        val partition = selector.nextPartition()
                        ProducerRecord(
                            name, partition, null,
                            if (it % 2 == 0) "key_$it" else """{"index":$it}""",
                            """
                                {
                                    "id":$it,
                                    "time":${System.currentTimeMillis()},
                                    "msg":"Dummy message $it",
                                    "map":{"$it":$it}
                                }
                            """.trimIndent(),
                            RecordHeaders(
                                listOf(
                                    RecordHeader("INDEX", IntegerSerializer().serialize(name, it)),
                                    RecordHeader("SOME_STRING", "$it".toByteArray()),
                                    RecordHeader("SOME_NULL", null),
                                    RecordHeader("4BYTES_STRING", "test".toByteArray()),
                                    RecordHeader("SIZE", "XL".toByteArray()),
                                    RecordHeader(
                                        "BINARY",
                                        byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
                                    ),
                                    RecordHeader("SOME_ZERO_LENGTH", ByteArray(0)),
                                )
                            )
                        )
                    }
                    .map { producer.send(it) }
                    .also { producer.flush() }
                    .forEach { it.get() }
            }
            val consumerConfig = mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.embeddedKafka.brokersAsString,
                ConsumerConfig.GROUP_ID_CONFIG to "test-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest"
            )
            log.info("Consuming from topic {}", name)
            KafkaConsumer(consumerConfig, StringDeserializer(), StringDeserializer()).use { consumer ->
                consumer.subscribe(listOf(name))
                consumer.poll(Duration.ofSeconds(1))
                consumer.commitSync()
                consumerGroupsProvider.refreshClusterState(clusterIdentifier)
            }
            log.info("Done with topic {}", name)
        }

        try {
            api.createPrincipalAcls(
                PrincipalAclRules(
                    principal = "User:bob",
                    description = "for testing",
                    owner = "Team_Test",
                    rules = listOf(
                        "User:bob * TOPIC:topic-example-4 WRITE ALLOW".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS)),
                        "User:bob 10.11.12.13 TOPIC:topic* ALL DENY".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS)),
                        "User:bob * GROUP:group* READ ALLOW".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS)),
                    )
                ).also {
                    log.info("Adding principal ACLs: {}", it)
                }
            )
            api.createPrincipalAcls(
                PrincipalAclRules(
                    principal = "User:jon",
                    description = "for testing",
                    owner = "Team_Test",
                    rules = listOf(
                        "User:jon * TOPIC:topic-example-2 READ ALLOW".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS))
                    )
                ).also {
                    log.info("Adding principal ACLs: {}", it)
                }
            )
            api.createPrincipalAcls(
                PrincipalAclRules(
                    principal = "User:mark",
                    description = "for testing",
                    owner = "Team_Test",
                    rules = listOf(
                        "User:mark * TOPIC:topic-example-3 ALL ALLOW".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS)),
                        "User:mark * GROUP:group ALL ALLOW".parseAcl()
                            .toAclRule(Presence(PresenceType.ALL_CLUSTERS)),
                    )
                ).also {
                    log.info("Adding principal ACLs: {}", it)
                }
            )
        } catch (ex: Exception) {
            log.error("Exception while creating principal acls, ignoring...", ex)
        }
        kafkaClientProvider.doWithClient(cluster) {
            val acls = listOf(
                "User:bob * TOPIC:topic-example-4 WRITE ALLOW".parseAcl(),
                "User:bob * GROUP:group* READ ALLOW".parseAcl(),
                "User:alice * TOPIC:topic-example-1 ALL ALLOW".parseAcl(),
                "User:mark * TOPIC:topic-example-3 ALL ALLOW".parseAcl(),
            )
            log.info("Creating ACLs on kafka: {}", acls)
            it.createAcls(acls).get()
        }

        log.info("Completed initialization")
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .plus("localhost")
            .forEach { log.info("server: http://$it:$serverPort$rootPath/topics/") }

    }

}

