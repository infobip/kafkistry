package com.infobip.kafkistry.it

import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.it.ui.ApiClient
import com.infobip.kafkistry.model.*
import jakarta.annotation.PostConstruct
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.util.concurrent.TimeUnit
import org.apache.kafka.common.config.TopicConfig as KafkaConfigKey

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("defaults", "defaults", "it", "dir")
class TopicRegistryRestApiItTest {

    @LocalServerPort
    var port = 0

    lateinit var apiClient: ApiClient

    @PostConstruct
    fun init() {
        apiClient = ApiClient("localhost", port, "/kafkistry")
    }

    @Test
    fun `test topic description creation and fetch`() {
        val topicDescription = TopicDescription(
                name = "my-test-topic",
                owner = "test_team",
                producer = "TestProducer",
                description = "Random text describing what is in topic",
                properties = TopicProperties(6, 2),
                resourceRequirements = ResourceRequirements(
                        avgMessageSize = MsgSize(10, BytesUnit.KB),
                        retention = DataRetention(3, TimeUnit.DAYS),
                        messagesRate = MessagesRate(20, ScaleFactor.M, RateUnit.MSG_PER_DAY),
                        messagesRateOverrides = mapOf("kc_1" to MessagesRate(500, ScaleFactor.K, RateUnit.MSG_PER_DAY)),
                        messagesRateTagOverrides = mapOf("tag1" to MessagesRate(1, ScaleFactor.G, RateUnit.MSG_PER_MINUTE)),
                ),
                presence = Presence(
                        type = PresenceType.INCLUDED_CLUSTERS,
                        kafkaClusterIdentifiers = listOf("kc_1", "kc_2", "kc_3", "kc_4")
                ),
                perClusterProperties = mapOf(
                        "kc_1" to TopicProperties(10, 1),
                        "kc_2" to TopicProperties(3, 4)
                ),
                config = mapOf(
                        KafkaConfigKey.RETENTION_BYTES_CONFIG to "1024000"
                ),
                perClusterConfigOverrides = emptyMap(),
                perTagProperties = mapOf("tag1" to TopicProperties(30, 2)),
                perTagConfigOverrides = emptyMap(),
        )

        apiClient.addTopic(topicDescription)
        val requestedTopicDescription = apiClient.getTopic(topicDescription.name)
        assertThat(requestedTopicDescription).isEqualTo(topicDescription)
    }

    @Test
    fun `test create and get principal acl rules`() {
        val principalAcls = PrincipalAclRules(
                principal = "bob",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        AclRule(
                                presence = Presence(PresenceType.ALL_CLUSTERS),
                                host = "*",
                                resource = AclResource(
                                        type = AclResource.Type.TOPIC,
                                        name = "KF.foo",
                                        namePattern = AclResource.NamePattern.LITERAL
                                ),
                                operation = AclOperation(
                                        type = AclOperation.Type.READ,
                                        policy = AclOperation.Policy.ALLOW
                                )
                        ),
                        AclRule(
                                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf("kc_1", "kc_3")),
                                host = "192.168.3.45",
                                resource = AclResource(
                                        type = AclResource.Type.GROUP,
                                        name = "backend-",
                                        namePattern = AclResource.NamePattern.PREFIXED
                                ),
                                operation = AclOperation(
                                        type = AclOperation.Type.ALL,
                                        policy = AclOperation.Policy.DENY
                                )
                        )

                )
        )

        apiClient.createPrincipalAcls(principalAcls)
        val createdAcls = apiClient.getPrincipalAcls("bob")
        assertThat(createdAcls).isEqualTo(principalAcls)
    }
}