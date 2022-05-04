package com.infobip.kafkistry.it.security

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.Kafkistry
import com.infobip.kafkistry.it.ui.ApiClient
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.UserRole
import com.infobip.kafkistry.webapp.security.auth.owners.UserOwnerVerifier
import com.infobip.kafkistry.webapp.security.auth.preauth.PreAuthUserResolver
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.client.RestClientResponseException
import java.util.*
import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest

@RunWith(SpringRunner::class)
@SpringBootTest(
        classes = [Kafkistry::class, SecurityPermissionsRestApiItTest.MockSecurityConfig::class],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
            "app.security.enabled=true"
        ]
)
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("defaults", "it", "dir")
class SecurityPermissionsRestApiItTest {

    @LocalServerPort
    var port = 0

    lateinit var apiAdmin: ApiClient
    lateinit var apiUser: ApiClient
    lateinit var apiEmptyRole: ApiClient
    lateinit var apiUnauthenticated: ApiClient

    @Autowired
    lateinit var clustersRegistry: ClustersRegistryService
    @Autowired
    lateinit var aclsRegistry: AclsRegistryService

    companion object {
        @ClassRule
        @JvmField
        val kafka = EmbeddedKafkaRule(3, true, "consumers-topic")

        private var clusterInitialized = false
    }

    class MockSecurityConfig {

        @Bean
        fun preAuth(): PreAuthUserResolver = object: PreAuthUserResolver {
            override fun getPreAuthenticatedPrincipal(request: HttpServletRequest): User? {
                val cookie = request.cookies?.find { it.name == "mock-role" } ?: return null
                val role = UserRole.valueOf(cookie.value)
                return User("t-test", "Testy", "Test ($role)", "t@test.com", role)
            }
        }

        @Bean
        fun mockOwnerUsers(): UserOwnerVerifier = object : UserOwnerVerifier {
            override fun isUserOwner(user: User, owner: String): Boolean {
                return "owner-${user.role.name.lowercase(Locale.getDefault())}" == owner
            }
        }

    }

    @PostConstruct
    fun initialize() {
        apiAdmin = ApiClient("localhost", port, "/kafkistry", "mock-role=" + UserRole.ADMIN.name)
        apiUser = ApiClient("localhost", port, "/kafkistry", "mock-role=" + UserRole.USER.name)
        apiEmptyRole = ApiClient("localhost", port, "/kafkistry", "mock-role=" + UserRole.EMPTY.name)
        apiUnauthenticated = ApiClient("localhost", port, "/kafkistry")
        if (!clusterInitialized) {
            //this initialization needs to run only once after context is started
            //but junit tests create new instance of "this" class for each test
            clusterInitialized = true
            val clusterInfo = apiAdmin.testClusterConnection(kafka.embeddedKafka.brokersAsString)
            val cluster = clusterInfo.toKafkaCluster().copy(identifier = "test-cluster")
            apiAdmin.addCluster(cluster)
            apiAdmin.refreshClusters()
            aclsRegistry.create(principalAcls("owner-admin", "consumer-admin"), UpdateContext("test"))
            aclsRegistry.create(principalAcls("owner-user", "consumer-user"), UpdateContext("test"))
            aclsRegistry.create(principalAcls("owner-empty", "consumer-empty"), UpdateContext("test"))
            aclsRegistry.create(principalAcls("owner-unauthenticated", "consumer-unauthenticated"), UpdateContext("test"))
            AdminClient.create(mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.embeddedKafka.brokersAsString
            )).use {
                val offsets = mapOf(TopicPartition("consumers-topic", 0) to OffsetAndMetadata(10L))
                val adminFuture = it.alterConsumerGroupOffsets("consumer-admin", offsets).all()
                val userFuture = it.alterConsumerGroupOffsets("consumer-user", offsets).all()
                val otherFuture = it.alterConsumerGroupOffsets("consumer-other", offsets).all()
                listOf(adminFuture, userFuture, otherFuture).forEach { it.get() }
            }
        }
        clustersRegistry.listClusters().filter { it.identifier != "test-cluster" }.forEach { clustersRegistry.removeCluster(it.identifier) }
    }

    private fun principalAcls(owner: String, ruleGroup: ConsumerGroupId) = PrincipalAclRules(
        principal = "User:p-$owner",
        description = "",
        owner = owner,
        rules = listOf("User:p-$owner * GROUP:$ruleGroup READ ALLOW".parseAcl().toAclRule(Presence.ALL))
    )

    private fun kafkaCluster(identifier: KafkaClusterIdentifier) = KafkaCluster(
        identifier = identifier, clusterId = identifier, connectionString = "$identifier:9092",
        sslEnabled = false, saslEnabled = false,
        tags = emptyList()
    )

    //////////////////////////////////
    // admin tests
    //////////////////////////////////

    @Test
    fun `admin OpenLoginPage`() = assertThat(apiAdmin.tryOpenLoginPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin OpenNonExisting`() = assertThat(apiAdmin.tryOpenNonExisting()).isEqualTo(OpResult.NOT_FOUND).void()

    @Test
    fun `admin GetStaticResource`() = assertThat(apiAdmin.tryGetStaticResource()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin ListTopics`() = assertThat(apiAdmin.tryListTopics()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin ListClusters`() = assertThat(apiAdmin.tryListClusters()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin InspectTopics`() = assertThat(apiAdmin.tryInspectTopics()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin AddAcl`() = assertThat(apiAdmin.tryAddAcls(aclsFor("admin"))).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin GetAcl`() = assertThat(apiAdmin.tryListAcls()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin InspectTopicDryRun`() = assertThat(apiAdmin.tryInspectTopicDryRun(newTopic("admin-dry-run")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin AddTopic`() = assertThat(apiAdmin.tryAddTopic(newTopic("admin-add-topic")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin AddCluster`() = assertThat(apiAdmin.tryAddCluster(kafkaCluster("admin")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin SuggestDefaultTopicConfig`() = assertThat(apiAdmin.trySuggestDefaultTopicConfig()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin SuggestObjectToYaml`() = assertThat(apiAdmin.trySuggestObjectToYaml()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin CreateMissingTopic`() {
        apiAdmin.addTopic(newTopic("admin-create-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        assertThat(apiAdmin.tryCreateMissingTopic("admin-create-topic", "test-cluster"))
                .isEqualTo(OpResult.SUCCESS)
    }

    @Test
    fun `admin VerifyTopicReAssignment`() {
        apiAdmin.addTopic(newTopic("admin-verify-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        apiAdmin.createMissingTopic("admin-verify-topic", "test-cluster")
        assertThat(apiAdmin.tryVerifyTopicReAssignment("admin-verify-topic", "test-cluster"))
                .isEqualTo(OpResult.SUCCESS)
    }

    @Test
    fun `admin GetHomePage`() = assertThat(apiAdmin.tryGetHomePage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin GetTopicsPage`() = assertThat(apiAdmin.tryGetTopicsPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin GetClustersPage`() = assertThat(apiAdmin.tryGetClustersPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin GetTopicWizardPage`() = assertThat(apiAdmin.tryGetTopicWizardPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin SubmitWizardAnswers`() = assertThat(apiAdmin.trySubmitWizardAnswers(newWizardAnswers(topicNameSuffix = "admin-topic")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin GetQuotasPage`() = assertThat(apiAdmin.tryGetQuotasPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin CreateMissingQuotas`() {
        apiAdmin.createEntityQuotas(newQuota(QuotaEntity.user("u-admin")))
        assertThat(apiAdmin.tryCreateMissingQuotas("u-admin|<all>", "test-cluster"))
            .isEqualTo(OpResult.SUCCESS)
    }

    @Test
    fun `admin Delete it's consumer group`() = assertThat(apiAdmin.tryDeleteConsumerGroup("consumer-admin", "test-cluster"))
        .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `admin Delete other consumer group`() = assertThat(apiAdmin.tryDeleteConsumerGroup("consumer-other", "test-cluster"))
        .isEqualTo(OpResult.SUCCESS).void()

    //////////////////////////////////
    // user access tests
    //////////////////////////////////

    @Test
    fun `user OpenLoginPage`() = assertThat(apiUser.tryOpenLoginPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user OpenNonExisting`() = assertThat(apiUser.tryOpenNonExisting()).isEqualTo(OpResult.NOT_FOUND).void()

    @Test
    fun `user GetStaticResource`() = assertThat(apiUser.tryGetStaticResource()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user ListTopics`() = assertThat(apiUser.tryListTopics()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user ListClusters`() = assertThat(apiUser.tryListClusters()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user AddAcl`() = assertThat(apiUser.tryAddAcls(aclsFor("user"))).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user GetAcl`() = assertThat(apiUser.tryListAcls()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user InspectTopics`() = assertThat(apiUser.tryInspectTopics()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user InspectTopicDryRun`() = assertThat(apiUser.tryInspectTopicDryRun(newTopic("user-dry-run")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user AddTopic`() = assertThat(apiUser.tryAddTopic(newTopic("user-add-topic")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user AddCluster`() = assertThat(apiUser.tryAddCluster(kafkaCluster("user")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user SuggestDefaultTopicConfig`() = assertThat(apiUser.trySuggestDefaultTopicConfig()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user SuggestObjectToYaml`() = assertThat(apiUser.trySuggestObjectToYaml()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user CreateMissingTopic`() {
        apiAdmin.addTopic(newTopic("user-create-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        assertThat(apiUser.tryCreateMissingTopic("user-create-topic", "test-cluster"))
                .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `user VerifyTopicReAssignment`() {
        apiAdmin.addTopic(newTopic("user-verify-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        apiAdmin.createMissingTopic("user-verify-topic", "test-cluster")
        assertThat(apiUser.tryVerifyTopicReAssignment("user-verify-topic", "test-cluster"))
                .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `user GetHomePage`() = assertThat(apiUser.tryGetHomePage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user GetTopicsPage`() = assertThat(apiUser.tryGetTopicsPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user GetClustersPage`() = assertThat(apiUser.tryGetClustersPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user GetTopicWizardPage`() = assertThat(apiUser.tryGetTopicWizardPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user SubmitWizardAnswers`() = assertThat(apiUser.trySubmitWizardAnswers(newWizardAnswers(topicNameSuffix = "user-topic")))
            .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user GetQuotasPage`() = assertThat(apiUser.tryGetQuotasPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user CreateMissingQuotas`() {
        apiUser.createEntityQuotas(newQuota(QuotaEntity.user("u-user")))
        assertThat(apiUser.tryCreateMissingQuotas("u-user|<all>", "test-cluster"))
            .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `user Delete it's consumer group`() = assertThat(apiUser.tryDeleteConsumerGroup("consumer-user", "test-cluster"))
        .isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `user Delete other consumer group`() = assertThat(apiUser.tryDeleteConsumerGroup("consumer-other", "test-cluster"))
        .isEqualTo(OpResult.FORBIDDEN).void()

    //////////////////////////////////
    // unauthorized no-auth tests
    //////////////////////////////////

    @Test
    fun `no-auth OpenLoginPage`() = assertThat(apiUnauthenticated.tryOpenLoginPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `no-auth OpenNonExisting`() = assertThat(apiUnauthenticated.tryOpenNonExisting()).isEqualTo(OpResult.NO_CONTENT).void()    //no_content because of redirect to /login

    @Test
    fun `no-auth GetStaticResource`() = assertThat(apiUnauthenticated.tryGetStaticResource()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `no-auth ListTopics`() = assertThat(apiUnauthenticated.tryListTopics()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth ListClusters`() = assertThat(apiUnauthenticated.tryListClusters()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth InspectTopics`() = assertThat(apiUnauthenticated.tryInspectTopics()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth AddAcl`() = assertThat(apiUnauthenticated.tryAddAcls(aclsFor("no-auth"))).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth GetAcl`() = assertThat(apiUnauthenticated.tryListAcls()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth InspectTopicDryRun`() = assertThat(apiUnauthenticated.tryInspectTopicDryRun(newTopic("no-auth-dry-run")))
            .isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth AddTopic`() = assertThat(apiUnauthenticated.tryAddTopic(newTopic("no-auth-add-topic")))
            .isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth AddCluster`() = assertThat(apiUnauthenticated.tryAddCluster(kafkaCluster("no-auth")))
            .isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth SuggestDefaultTopicConfig`() = assertThat(apiUnauthenticated.trySuggestDefaultTopicConfig()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth SuggestObjectToYaml`() = assertThat(apiUnauthenticated.trySuggestObjectToYaml()).isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth CreateMissingTopic`() {
        apiAdmin.addTopic(newTopic("no-auth-create-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        assertThat(apiUnauthenticated.tryCreateMissingTopic("no-auth-create-topic", "test-cluster"))
                .isEqualTo(OpResult.UNAUTHORIZED)
    }

    @Test
    fun `no-auth VerifyTopicReAssignment`() {
        apiAdmin.addTopic(newTopic("no-auth-verify-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        apiAdmin.createMissingTopic("no-auth-verify-topic", "test-cluster")
        assertThat(apiUnauthenticated.tryVerifyTopicReAssignment("no-auth-verify-topic", "test-cluster"))
                .isEqualTo(OpResult.UNAUTHORIZED)
    }

    @Test
    fun `no-auth GetHomePage`() = assertThat(apiUnauthenticated.tryGetHomePage()).isEqualTo(OpResult.NO_CONTENT).void()

    @Test
    fun `no-auth GetTopicsPage`() = assertThat(apiUnauthenticated.tryGetTopicsPage()).isEqualTo(OpResult.NO_CONTENT).void()

    @Test
    fun `no-auth GetClustersPage`() = assertThat(apiUnauthenticated.tryGetClustersPage()).isEqualTo(OpResult.NO_CONTENT).void()

    @Test
    fun `no-auth GetTopicWizardPage`() = assertThat(apiUnauthenticated.tryGetTopicWizardPage()).isEqualTo(OpResult.NO_CONTENT).void()

    @Test
    fun `no-auth SubmitWizardAnswers`() = assertThat(apiUnauthenticated.trySubmitWizardAnswers(newWizardAnswers(topicNameSuffix = "no-auth-topic")))
            .isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth GetQuotasPage`() = assertThat(apiUnauthenticated.tryGetQuotasPage()).isEqualTo(OpResult.NO_CONTENT).void()

    @Test
    fun `no-auth CreateMissingQuotas`() {
        apiAdmin.createEntityQuotas(newQuota(QuotaEntity.user("u-no-auth")))
        assertThat(apiUnauthenticated.tryCreateMissingQuotas("u-no-auth|<all>", "test-cluster"))
            .isEqualTo(OpResult.UNAUTHORIZED)
    }

    @Test
    fun `no-auth Delete it's consumer group`() = assertThat(apiUnauthenticated.tryDeleteConsumerGroup("consumer-unauthenticated", "test-cluster"))
        .isEqualTo(OpResult.UNAUTHORIZED).void()

    @Test
    fun `no-auth Delete other consumer group`() = assertThat(apiUnauthenticated.tryDeleteConsumerGroup("consumer-other", "test-cluster"))
        .isEqualTo(OpResult.UNAUTHORIZED).void()

    //////////////////////////////////
    // mock role, no authorities tests
    //////////////////////////////////

    @Test
    fun `mock-role OpenLoginPage`() = assertThat(apiEmptyRole.tryOpenLoginPage()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `mock-role OpenNonExisting`() = assertThat(apiEmptyRole.tryOpenNonExisting()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetStaticResource`() = assertThat(apiEmptyRole.tryGetStaticResource()).isEqualTo(OpResult.SUCCESS).void()

    @Test
    fun `mock-role ListTopics`() = assertThat(apiEmptyRole.tryListTopics()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role ListClusters`() = assertThat(apiEmptyRole.tryListClusters()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role AddAcl`() = assertThat(apiEmptyRole.tryAddAcls(aclsFor("mock-rolee"))).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetAcl`() = assertThat(apiEmptyRole.tryListAcls()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role InspectTopics`() = assertThat(apiEmptyRole.tryInspectTopics()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role InspectTopicDryRun`() = assertThat(apiEmptyRole.tryInspectTopicDryRun(newTopic("mock-role-dry-run")))
            .isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role AddTopic`() = assertThat(apiEmptyRole.tryAddTopic(newTopic("mock-role-add-topic")))
            .isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role AddCluster`() = assertThat(apiEmptyRole.tryAddCluster(kafkaCluster("mock-role")))
            .isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role SuggestDefaultTopicConfig`() = assertThat(apiEmptyRole.trySuggestDefaultTopicConfig()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role SuggestObjectToYaml`() = assertThat(apiEmptyRole.trySuggestObjectToYaml()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role CreateMissingTopic`() {
        apiAdmin.addTopic(newTopic("mock-role-create-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        assertThat(apiEmptyRole.tryCreateMissingTopic("mock-role-create-topic", "test-cluster"))
                .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `mock-role VerifyTopicReAssignment`() {
        apiAdmin.addTopic(newTopic("mock-role-verify-topic", presence = Presence(PresenceType.ALL_CLUSTERS, null)))
        apiAdmin.createMissingTopic("mock-role-verify-topic", "test-cluster")
        assertThat(apiEmptyRole.tryVerifyTopicReAssignment("mock-role-verify-topic", "test-cluster"))
                .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `mock-role GetHomePage`() = assertThat(apiEmptyRole.tryGetHomePage()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetTopicsPage`() = assertThat(apiEmptyRole.tryGetTopicsPage()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetClustersPage`() = assertThat(apiEmptyRole.tryGetClustersPage()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetTopicWizardPage`() = assertThat(apiEmptyRole.tryGetTopicWizardPage()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role SubmitWizardAnswers`() = assertThat(apiEmptyRole.trySubmitWizardAnswers(newWizardAnswers(topicNameSuffix = "mock-role-topic")))
            .isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role GetQuotasPage`() = assertThat(apiEmptyRole.tryGetQuotasPage()).isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role CreateMissingQuotas`() {
        apiAdmin.createEntityQuotas(newQuota(QuotaEntity.user("u-mock-role")))
        assertThat(apiEmptyRole.tryCreateMissingQuotas("u-mock-role|<all>", "test-cluster"))
            .isEqualTo(OpResult.FORBIDDEN)
    }

    @Test
    fun `mock-role Delete it's consumer group`() = assertThat(apiEmptyRole.tryDeleteConsumerGroup("consumer-empty", "test-cluster"))
        .isEqualTo(OpResult.FORBIDDEN).void()

    @Test
    fun `mock-role Delete other consumer group`() = assertThat(apiEmptyRole.tryDeleteConsumerGroup("consumer-other", "test-cluster"))
        .isEqualTo(OpResult.FORBIDDEN).void()


    private data class OpResult(val name: String, val httpCode: Int, val httpMsg: String? = null) {
        companion object {
            val SUCCESS = OpResult("SUCCESS", 200)
            val FORBIDDEN = OpResult("FORBIDDEN",403)
            val UNAUTHORIZED = OpResult("UNAUTHORIZED", 200)
            val NOT_FOUND = OpResult("NOT_FOUND",404)
            val NO_CONTENT = OpResult("NO_CONTENT",-1)

            fun other(httpCode: Int, httpMsg: String) = OpResult("OTHER", httpCode, httpMsg)
        }
    }

    private fun ApiClient.tryOpenLoginPage(): OpResult = tryOperation { getPageOrNull("/login") }
    private fun ApiClient.tryOpenNonExisting(): OpResult = tryOperation { getPageOrNull("/non-existing-page") }
    private fun ApiClient.tryGetStaticResource(): OpResult = tryOperation { getContent("/static/css/main.css") }
    private fun ApiClient.tryListTopics(): OpResult = tryOperation { listAllTopics() }
    private fun ApiClient.tryListClusters(): OpResult = tryOperation { listAllClusters() }
    private fun ApiClient.tryInspectTopics(): OpResult = tryOperation { inspectAllTopics() }
    private fun ApiClient.tryInspectTopicDryRun(topic: TopicDescription): OpResult = tryOperation { inspectTopicUpdateDryRun(topic) }
    private fun ApiClient.tryAddTopic(topic: TopicDescription): OpResult = tryOperation { addTopic(topic) }
    private fun ApiClient.tryAddCluster(cluster: KafkaCluster): OpResult = tryOperation { addCluster(cluster) }
    private fun ApiClient.tryAddAcls(principalAcls: PrincipalAclRules): OpResult = tryOperation { createPrincipalAcls(principalAcls) }
    private fun ApiClient.tryListAcls(): OpResult = tryOperation { listPrincipalsAcls() }
    private fun ApiClient.trySuggestDefaultTopicConfig(): OpResult = tryOperation { suggestDefaultTopicDescription() }
    private fun ApiClient.trySuggestObjectToYaml(): OpResult = tryOperation { suggestObjectToYaml(mapOf("str" to "text", "number" to 123)) }
    private fun ApiClient.tryVerifyTopicReAssignment(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier): OpResult = tryOperation { verifyTopicReAssignment(topicName, clusterIdentifier) }
    private fun ApiClient.tryCreateMissingTopic(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier): OpResult = tryOperation { createMissingTopic(topicName, clusterIdentifier) }
    private fun ApiClient.tryGetHomePage(): OpResult = tryOperation { getPageOrNull("/home") }
    private fun ApiClient.tryGetTopicsPage(): OpResult = tryOperation { getPageOrNull("/topics") }
    private fun ApiClient.tryGetClustersPage(): OpResult = tryOperation { getPageOrNull("/clusters") }
    private fun ApiClient.tryGetTopicWizardPage(): OpResult = tryOperation { getPageOrNull("/topics/wizard") }
    private fun ApiClient.trySubmitWizardAnswers(answers: TopicCreationWizardAnswers): OpResult = tryOperation { submitWizardAnswers(answers) }
    private fun ApiClient.tryGetQuotasPage(): OpResult = tryOperation { getPageOrNull("/quotas") }
    private fun ApiClient.tryCreateMissingQuotas(quotaEntityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier): OpResult = tryOperation { createMissingEntityQuotas(quotaEntityID, clusterIdentifier) }
    private fun ApiClient.tryDeleteConsumerGroup(consumerGroupId: ConsumerGroupId, clusterIdentifier: KafkaClusterIdentifier): OpResult = tryOperation { deleteClusterConsumerGroup(clusterIdentifier, consumerGroupId) }

    private fun ApiClient.tryOperation(operation: ApiClient.() -> Any?): OpResult {
        return try {
            val response = operation()
            if (response != null) {
                OpResult.SUCCESS
            } else {
                OpResult.NO_CONTENT
            }
        } catch (e: RestClientResponseException) {
            when (e.rawStatusCode) {
                HttpStatus.FORBIDDEN.value() -> OpResult.FORBIDDEN
                HttpStatus.UNAUTHORIZED.value() -> OpResult.UNAUTHORIZED
                HttpStatus.NOT_FOUND.value() -> OpResult.NOT_FOUND
                else -> OpResult.other(e.rawStatusCode, e.statusText)
            }
        }
    }

    private fun Any.void(): Unit = let { }

}