package com.infobip.kafkistry.it.broswer

import com.infobip.kafkistry.api.AclsApi
import com.infobip.kafkistry.api.ClustersApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.api.TopicsApi
import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.it.broswer.cases.autopilot.AutopilotCreateTopic
import com.infobip.kafkistry.it.broswer.cases.clusters.AddClusterToRegistry
import com.infobip.kafkistry.it.broswer.cases.clusters.RemoveClusterFromRegistry
import com.infobip.kafkistry.it.broswer.cases.sql.PerformSqlQuery
import com.infobip.kafkistry.it.broswer.cases.topics.*
import com.infobip.kafkistry.service.deleteAllOnCluster
import com.infobip.kafkistry.kafka.ClientFactory
import com.infobip.kafkistry.kafka.ConnectionDefinition
import com.infobip.kafkistry.kafka.KafkaManagementClient
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSamplerFactory
import com.infobip.kafkistry.webapp.WebHttpProperties
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.InetAddress
import java.util.*
import jakarta.annotation.Resource

class KBrowserWebDriverContainer : BrowserWebDriverContainer<KBrowserWebDriverContainer>()

@ExtendWith(
    SpringExtension::class,
    LoggingTestWatcher::class,
    FailedTestScreenshotWatcher::class,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@EmbeddedKafka(
        count = 3,
        brokerProperties = [
            "authorizer.class.name=kafka.security.authorizer.AclAuthorizer",
            "super.users=User:ANONYMOUS",
            "auto.leader.rebalance.enable=false",
        ]
)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
            "app.repository.git.write-branch-select.write-to-master=true",
            "app.scheduling.enabled=true"
        ]
)
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("defaults", "it")
@EnabledIfSystemProperty(
    named = "enabledIntegrationTests",
    matches = "all|.*(browser-ui).*",
    disabledReason = "These tests are too slow to run each time",
)
class BrowserItTestSuite {

    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var httpProperties: WebHttpProperties
    @Autowired
    lateinit var applicationContext: ApplicationContext
    @Autowired
    lateinit var topicsApi: TopicsApi
    @Autowired
    lateinit var clustersApi: ClustersApi
    @Autowired
    lateinit var aclsApi: AclsApi
    @Autowired
    lateinit var inspectApi: InspectApi

    @Resource
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    //needed for browser running in docker to be able to access KR app running on host
    private val localhost = run {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        when {
            osName.startsWith("mac os x") -> "host.docker.internal"
            else -> InetAddress.getLocalHost().hostName
        }
    }

    lateinit var baseUrl: String

    lateinit var kafkaClient: KafkaManagementClient

    companion object {

        @Container
        @JvmField
        val chrome: KBrowserWebDriverContainer = KBrowserWebDriverContainer()
                .withCapabilities(ChromeOptions())

        val clientFactory = ClientFactory(
                KafkaManagementClientProperties(),
                RecordReadSamplerFactory(),
                Optional.empty()
        )
    }

    @BeforeEach
    fun setup() {
        baseUrl = "http://$localhost:$port${httpProperties.rootPath}"
        kafkaClient = clientFactory.createManagementClient(
                ConnectionDefinition(embeddedKafka.brokersAsString, ssl = false, sasl = false, profiles = emptyList())
        )
        deleteAll()
        chrome.webDriver.get("$baseUrl/home")
    }

    @AfterEach
    fun cleanup() {
        kafkaClient.close()
    }

    private fun deleteAll() {
        topicsApi.listTopics().forEach {
            topicsApi.deleteTopic(it.name, "clean-test", null)
        }
        aclsApi.listPrincipalAcls().forEach {
            aclsApi.deletePrincipalAcls(it.principal, "clean-test", null)
        }
        clustersApi.listClusters().forEach {
            clustersApi.removeCluster(it.identifier, "clean-test", null)
        }
        kafkaClient.deleteAllOnCluster()
    }

    private fun context() = Context(
            browser = chrome.webDriver,
            appCtx = applicationContext,
            clustersApi = clustersApi,
            topicsApi = topicsApi,
            kafkaClient = kafkaClient,
            aclsApi = aclsApi,
            inspectApi = inspectApi
    )

    ///////////////////////////////////////////////////////////////////////////////////
    // implementation in separate class files with goals:
    //      - to avoid this file becoming too big
    //      - to run all tests within:
    //          - same application instance/context
    //          - using same instance of kafka
    //          - over same instance of browser

    @Nested
    inner class AddClusterToRegistryTest : AddClusterToRegistry({ context() })

    @Nested
    inner class RemoveClusterFromRegistryTest : RemoveClusterFromRegistry({ context() })

    @Nested
    inner class CreateTopicInRegistryTest : CreateTopicInRegistry({ context() })

    @Nested
    inner class CreateMissingTopicTest : CreateMissingTopic({ context() })

    @Nested
    inner class UnknownTopicImportTest : UnknownTopicImport({ context() })

    @Nested
    inner class EditTopicInRegistryTest : EditTopicInRegistry({ context() })

    @Nested
    inner class DeleteUnknownTopicTest : DeleteUnknownTopic({ context() })

    @Nested
    inner class AlterTopicConfigTest : AlterTopicConfig({ context() })

    @Nested
    inner class SuggestedTopicEditTest : SuggestedTopicEdit({ context() })

    @Nested
    inner class IncreaseTopicPartitionCountTest : IncreaseTopicPartitionCount({ context() })

    @Nested
    inner class IncreaseTopicReplicationFactorTest : IncreaseTopicReplicationFactor({ context() })

    @Nested
    inner class ReBalanceTopicReplicasAndLeadersTest : ReBalanceTopicReplicasAndLeaders({ context() })

    @Nested
    inner class PerformSqlQueryTest : PerformSqlQuery({ context() })

    @Nested
    inner class AutopilotUiTest : AutopilotCreateTopic({ context() })

}