package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.TopicProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class SuggestedTopicEdit(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topicBefore = newTopic(
            name = "my-suggest-1",
            properties = TopicProperties(12, 1),
            config = mapOf(
                    "retention.bytes" to "2147483648",
                    "retention.ms" to "600000"
            )
    )
    private val topicAfter = newTopic(
            name = "my-suggest-1",
            properties = TopicProperties(24, 3),
            config = mapOf(
                    "retention.bytes" to "42949672960",
                    "retention.ms" to "3600000"
            )
    )

    @BeforeEach
    fun setup() {
        createTopicOnKafka(topicAfter)
        addTopicToRegistry(topicBefore)
        addKafkaClusterToRegistry()
    }

    @AfterEach
    fun checkEdited() {
        assertThat(topicsApi.getTopic("my-suggest-1")).isEqualTo(topicAfter)
    }

    @Test
    fun `test alter topic config using UI`() {
        navigateToTopicInspectPage("my-suggest-1")

        browser.assertPageText().contains(
                "CONFIG MISMATCH", "WRONG_CONFIG", "WRONG_PARTITION_COUNT", "WRONG_REPLICATION_FACTOR"
        )

        browser.findElementWithText("Suggested edit").scrollIntoView().click()
        await {
            browser.assertPageText().contains("Suggested edit to match current state on clusters")
        }

        browser.assertPageText().contains(
                "partitionCount: 12",   //old
                "replicationFactor: 1", //old
                "partitionCount: 24",   //new
                "replicationFactor: 3", //new
                "retention.bytes: \"2147483648\"",  //old
                "retention.ms: \"600000\"",         //old
                "retention.bytes: \"42949672960\"", //new
                "retention.ms: \"3600000\""         //new
        )

        browser.findElementByCssSelector("input#update-message").sendKeys("suggested edit")

        browser.findElementById("dry-run-inspect-btn").ensureClick()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains("my-cluster", "OK")
        }

        //trigger actual edit
        browser.findElementById("edit-btn").ensureClick()
        await("to save edit and redirect to topic page") {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            browser.assertPageText().contains("Topic: my-suggest-1")
        }

        browser.assertPageText().contains("ALL OK", "OK")
    }
}
