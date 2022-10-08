package com.infobip.kafkistry.it.broswer

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import com.infobip.kafkistry.api.AclsApi
import com.infobip.kafkistry.api.ClustersApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.api.TopicsApi
import com.infobip.kafkistry.service.toKafkaCluster
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.KafkaManagementClient
import com.infobip.kafkistry.kafka.KafkaTopicConfiguration
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.topic.propertiesForCluster
import org.junit.jupiter.api.BeforeEach
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.context.ApplicationContext
import java.util.concurrent.TimeUnit

/**
 * Extend this class to write some UI flow test.
 * Following is ensured in environment:
 *    - having one kafka cluster which can be added to registry using @BeforeEach
 *    - browser started and navigated to KR's home page as starting point for further interactions
 *    - kafka cluster empty: no topics/groups/acls...
 *    - KR app repositories empty: no clusters/topics/acls
 */
abstract class UITestCase(
        //use lazy supplier to be able to request it "after" everything needed is initialized in BrowserItTestSuite
        private val contextSupplier: () -> Context
) {

    companion object {
        const val CLUSTER_IDENTIFIER = "my-cluster"
        val CLUSTER_REF = ClusterRef(CLUSTER_IDENTIFIER, emptyList())
    }

    lateinit var browser: RemoteWebDriver
    lateinit var appCtx: ApplicationContext
    lateinit var kafkaClient: KafkaManagementClient
    lateinit var topicsApi: TopicsApi
    lateinit var clustersApi: ClustersApi
    lateinit var aclsApi: AclsApi
    lateinit var inspectApi: InspectApi

    @BeforeEach
    fun initContext() {
        val context = contextSupplier()
        browser = context.browser
        appCtx = context.appCtx
        kafkaClient = context.kafkaClient
        topicsApi = context.topicsApi
        clustersApi = context.clustersApi
        aclsApi = context.aclsApi
        inspectApi = context.inspectApi
        await {
            browser.currentUrl.endsWith("/home")
            browser.assertPageText().contains("Status counts")
        }
    }

    fun await(what: String? = null, timeoutSecs: Int = 5, assertCheck: () -> Unit) {
        Awaitility.await(what)
                .atMost(timeoutSecs.toLong(), TimeUnit.SECONDS)
                .untilAsserted(assertCheck)
    }

    fun RemoteWebDriver.pageText(): String = findElementByTagName("body").text

    fun RemoteWebDriver.assertPageText() = assertThat(pageText())!!

    fun RemoteWebDriver.findElementWithText(text: String): WebElement =
            this
                    .findElements(By.xpath("//*[text()[contains(.,'$text')]]"))
                    .firstOrNull { it.isDisplayed }
                    ?: throw RuntimeException("Can't find displayed element with text: '$text'")

    fun WebElement.clearAndSendKeys(keys: String) = run {
        clear()
        sendKeys(keys)
    }

    fun WebElement.scrollIntoView() = apply {
        (browser as JavascriptExecutor).executeScript("arguments[0].scrollIntoView(true);", this)
        WebDriverWait(browser, 1).until(
            visibilityOfElementLocated(By.xpath(generateXPATH(this)))
        )
    }

    private fun generateXPATH(childElement: WebElement, current: String = ""): String? {
        val childTag = childElement.tagName
        if (childTag == "html") {
            return "/html[1]$current"
        }
        val parentElement = childElement.findElement(By.xpath(".."))
        val childrenElements = parentElement.findElements(By.xpath("*"))
        var count = 0
        for (i in childrenElements.indices) {
            val childrenElement = childrenElements[i]
            val childrenElementTag = childrenElement.tagName
            if (childTag == childrenElementTag) {
                count++
            }
            if (childElement == childrenElement) {
                return generateXPATH(parentElement, "/$childTag[$count]$current")
            }
        }
        return null
    }

    fun addKafkaClusterToRegistry() {
        val cluster = kafkaClient.clusterInfo(CLUSTER_IDENTIFIER).get().toKafkaCluster()
        clustersApi.addCluster(cluster, "add", null)
        clustersApi.refreshCluster(CLUSTER_IDENTIFIER)
    }

    fun addTopicToRegistry(topic: TopicDescription) {
        topicsApi.createTopic(topic, "create", null)
    }

    fun createTopicOnKafka(
            topic: TopicDescription,
            assignments: Map<Partition, List<BrokerId>>? = null
    ) {
        val cluster = kafkaClient.clusterInfo(CLUSTER_IDENTIFIER).get()
        val properties = topic.propertiesForCluster(CLUSTER_REF)
        val config = topic.configForCluster(CLUSTER_REF)
        val finalAssignments = assignments
                ?: PartitionsReplicasAssignor().assignNewPartitionReplicas(
                        emptyMap(),
                        cluster.nodeIds,
                        properties.partitionCount,
                        properties.replicationFactor,
                        emptyMap()
                ).newAssignments
        kafkaClient.createTopic(KafkaTopicConfiguration(topic.name, finalAssignments, config)).get()
    }

    fun clearTopicsSearch() {
        //because previous search can have content already filled in
        browser.findElementByCssSelector(".dataTables_filter input.form-control[type=search]")
            .sendKeys(Keys.chord(Keys.CONTROL,"a", Keys.DELETE))
    }

    fun topicsPageSearchAndOpenTopic(name: String) {
        clearTopicsSearch()
        //type topic in search
        browser.findElementByCssSelector(".dataTables_filter input.form-control[type=search]").sendKeys(name)
        //open topic inspect
        browser.findElementWithText(name).click()
    }

    fun navigateToTopicInspectPage(name: String) {
        browser.findElementById("nav-topics").click()
        await {
            assertThat(browser.currentUrl).endsWith("/topics")
            browser.assertPageText().contains("Status of all topics in registry")
        }

        topicsPageSearchAndOpenTopic(name)

        await {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            browser.assertPageText().contains("Topic: $name")
        }
    }

}

data class Context(
        /**
         * Instance for interaction with browser
         */
        val browser: RemoteWebDriver,

        //beans used for setup/preparation/assertion
        val appCtx: ApplicationContext,
        val kafkaClient: KafkaManagementClient,
        val topicsApi: TopicsApi,
        val clustersApi: ClustersApi,
        val aclsApi: AclsApi,
        val inspectApi: InspectApi
)