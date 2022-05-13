package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.yaml.YamlMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.support.ui.Select
import java.util.concurrent.TimeUnit

abstract class EditTopicInRegistry(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topic = newTopic("my-edit-1")

    @BeforeEach
    fun prepareClusterAndMissingTopic() {
        createTopicOnKafka(topic)
        addKafkaClusterToRegistry()
        addTopicToRegistry(topic)
    }

    @Test
    fun `test edit topic description through UI`() {
        navigateToTopicInspectPage("my-edit-1")

        browser.findElementWithText("Edit topic").click()
        await {
            assertThat(browser.currentUrl).contains("/topics/edit")
            browser.assertPageText().contains("Edit topic")
        }

        browser.findElementByCssSelector("textarea[name=description]").run {
            clear()
            sendKeys("by test updated description")
        }

        browser.findElementById("dry-run-inspect-btn").scrollIntoView().click()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains("my-cluster", "OK")
        }

        //try save without message
        browser.findElementById("edit-btn").scrollIntoView().click()
        await {
            browser.assertPageText().contains("Please specify update reason")
        }

        browser.findElementByCssSelector("input#update-message").sendKeys("test reason")

        browser.findElementById("edit-btn").scrollIntoView().click()
        await("to save edit and redirect to topic page") {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            browser.assertPageText().contains("Topic: my-edit-1")
        }

        browser.assertPageText().contains(
                "Description", "by test updated description"
        )
        assertThat(topicsApi.getTopic(topic.name)).isEqualTo(topic.copy(description = "by test updated description"))
    }

    @Test
    fun `test edit everything possible in topic through UI`() {
        navigateToTopicInspectPage("my-edit-1")

        browser.findElementWithText("Edit topic").click()
        await {
            assertThat(browser.currentUrl).contains("/topics/edit")
            browser.assertPageText().contains("Edit topic")
        }

        //meta
        browser.findElementByCssSelector("input[name=owner]").clearAndSendKeys("new owner")
        browser.findElementByCssSelector("textarea[name=description]").clearAndSendKeys("new description")
        browser.findElementByCssSelector("input[name=producer]").clearAndSendKeys("new producer")

        //presence
        browser.findElementByCssSelector("input[value=INCLUDED_CLUSTERS]").scrollIntoView().click()
        browser.findElementByCssSelector("select[name=selectedClusters]").run { Select(this) }
            .selectByValue(CLUSTER_IDENTIFIER)

        //resource requirements
        browser.findElementByCssSelector("input[name=resourceRequirementsDefined]").scrollIntoView().click()
        //rate
        browser.findElementByCssSelector("input[name=messagesRateAmount]").sendKeys("100")
        browser.findElementByCssSelector("select[name=messagesRateFactor]").run { Select(this) }
                .selectByVisibleText("1M msg")
        browser.findElementByCssSelector("select[name=messagesRateUnit]").run { Select(this) }
                .selectByVisibleText("/ hour")
        //rate override
        browser.findElementWithText("Add override").scrollIntoView().click()
        browser.findElementByCssSelector("select[name=overrideWhere]").run { Select(this) }
                .selectByVisibleText(CLUSTER_IDENTIFIER)
        browser.findElementByCssSelector(".messages-rate-override input[name=messagesRateAmount]").sendKeys("200")
        browser.findElementByCssSelector(".messages-rate-override select[name=messagesRateFactor]").run { Select(this) }
                .selectByVisibleText("1K msg")
        browser.findElementByCssSelector(".messages-rate-override select[name=messagesRateUnit]").run { Select(this) }
                .selectByVisibleText("/ minute")
        //msg size
        browser.findElementByCssSelector("input[name=avgMessageSize]").sendKeys("5")
        browser.findElementByCssSelector("select[name=avgMessageSizeUnit]").run { Select(this) }
                .selectByVisibleText("kB")
        //retention
        browser.findElementByCssSelector("input[name=retentionAmount]").sendKeys("12")
        browser.findElementByCssSelector("select[name=retentionUnit]").run { Select(this) }
                .selectByVisibleText("hour(s)")

        //apply resource requirements
        browser.findElementWithText("Apply requirements to config").scrollIntoView().click()
        await {
            browser.assertPageText().doesNotContain("Applying resource requirements")
        }

        //global config
        //topic-properties
        browser.findElementByCssSelector(".globalConfig input[name=partitionCount]").clearAndSendKeys("36")
        browser.findElementByCssSelector(".globalConfig input[name=replicationFactor]").clearAndSendKeys("4")
        //config
        browser.findElementByCssSelector(".globalConfig select.config-key-select").run { Select(this) }
                .selectByVisibleText("max.message.bytes")
        browser.findElementByCssSelector(".globalConfig input.conf-value-in[name='max.message.bytes']").clearAndSendKeys("10k")
        browser.findElementByCssSelector(".globalConfig select.config-key-select").run { Select(this) }
                .selectByVisibleText("segment.ms")
        browser.findElementByCssSelector(".globalConfig input.conf-value-in[name='segment.ms']").clearAndSendKeys("1h")

        //cluster override config
        browser.findElementWithText("Add per-cluster/per-tag override").scrollIntoView().click()
        browser.findElementByCssSelector(".cluster-override select[name=overrideWhere]").run { Select(this) }
                .selectByValue(CLUSTER_IDENTIFIER)
        //topic-properties
        browser.findElementByCssSelector(".cluster-override input[name=propertiesOverridden]").scrollIntoView().click()
        browser.findElementByCssSelector(".cluster-override input[name=partitionCount]").clearAndSendKeys("24")
        browser.findElementByCssSelector(".cluster-override input[name=replicationFactor]").clearAndSendKeys("3")
        //config
        browser.findElementByCssSelector(".cluster-override  select.config-key-select").run { Select(this) }
                .selectByVisibleText("min.insync.replicas")
        browser.findElementByCssSelector(".cluster-override input.conf-value-in[name='min.insync.replicas']").clearAndSendKeys("2")

        browser.findElementByCssSelector("input#update-message").sendKeys("try update all")

        browser.findElementById("dry-run-inspect-btn").scrollIntoView().click()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains(
                "my-cluster", "WRONG_CONFIG", "WRONG_PARTITION_COUNT", "WRONG_REPLICATION_FACTOR"
            )
        }

        browser.findElementById("edit-btn").scrollIntoView().click()
        await("to save edit and redirect to topic page") {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            browser.assertPageText().contains("Topic: my-edit-1")
        }

        val expectedTopicDescription = TopicDescription(
                name = "my-edit-1",
                owner = "new owner",
                description = "new description",
                producer = "new producer",
                presence = Presence(PresenceType.INCLUDED_CLUSTERS, listOf(CLUSTER_IDENTIFIER)),
                resourceRequirements = ResourceRequirements(
                        messagesRate = MessagesRate(100L, ScaleFactor.M, RateUnit.MSG_PER_HOUR),
                        messagesRateOverrides = mapOf(
                                CLUSTER_IDENTIFIER to MessagesRate(200L, ScaleFactor.K, RateUnit.MSG_PER_MINUTE)
                        ),
                        messagesRateTagOverrides = emptyMap(),
                        avgMessageSize = MsgSize(5, BytesUnit.KB),
                        retention = DataRetention(12, TimeUnit.HOURS)
                ),
                properties = TopicProperties(36, 4),
                config = mapOf(
                        "retention.bytes" to "24576000000",     //----- calculated based on resource requirements override
                        "retention.ms" to "43200000",
                        "segment.bytes" to "${Int.MAX_VALUE}",  //----- calculated from retention.bytes (would be 2457600000) and capped down to MAX_INT
                        "max.message.bytes" to "10240",
                        "segment.ms" to "3600000",
                ),
                perClusterProperties = mapOf(
                        CLUSTER_IDENTIFIER to TopicProperties(24, 3)
                ),
                perClusterConfigOverrides = mapOf(
                        CLUSTER_IDENTIFIER to mapOf(
                                "min.insync.replicas" to "2"
                        )
                ),
                perTagProperties = emptyMap(),
                perTagConfigOverrides = emptyMap(),
        )
        assertThat(topicsApi.getTopic("my-edit-1")).isEqualTo(expectedTopicDescription)
        assertThat(browser.findElementById("topic-yaml").text).isEqualTo(YamlMapper().serialize(expectedTopicDescription).trim())
    }

}