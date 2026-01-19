package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class UnknownTopicImport(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareClusterAndUnknownTopic() {
        createTopicOnKafka(newTopic("my-unknown-1", properties = TopicProperties(1, 2)))
        addKafkaClusterToRegistry() //add cluster after creation of topic to get aware that topic exist right away
    }

    @AfterEach
    fun checkImported() {
        assertThat(inspectApi.inspectTopic("my-unknown-1").aggStatusFlags.allOk).isEqualTo(true)
    }

    @Test
    fun `test create where missing topic through UI`() {
        //open all topics page
        browser.findElementById("nav-topics").click()
        await {
            clearTopicsSearch()
            browser.assertPageText().contains("UNKNOWN")
        }

        browser.findElementWithText("my-unknown-1").click()
        await {
            browser.pageText().contains("Topic: my-unknown-1")
        }

        browser.findElementWithText("Import topic").scrollIntoView().click()
        await {
            browser.assertPageText().contains("Import topic suggestion")
        }

        //check dry run inspect
        browser.findElementById("dry-run-inspect-btn").ensureClick()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains(
                "ALL OK", "my-cluster", "OK"
            )
        }

        //try import without filling owner/description/producer
        browser.findElementById("import-btn").ensureClick()
        await {
            browser.assertPageText().contains("Owner is blank")
            assertThat(browser.currentUrl).contains("/topics/import")
        }

        browser.findElementByCssSelector("input[name=owner]").sendKeys("Unknown-owner")
        browser.findElementByCssSelector("textarea[name=description]").sendKeys("Unknown-description")
        browser.findElementByCssSelector("input[name=producer]").sendKeys("Unknown-producer")

        //check dry run inspect again because of changes
        browser.findElementById("dry-run-inspect-btn").ensureClick()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains(
                "ALL OK", "my-cluster", "OK"
            )
        }

        browser.findElementById("import-btn").ensureClick()
        await("create and redirect to all topics page") {
            assertThat(browser.currentUrl).endsWith("/topics/inspect?topicName=my-unknown-1&")
            browser.assertPageText().contains("Topic: my-unknown-1")
        }

        navigateToTopicInspectPage("my-unknown-1")

        browser.assertPageText().contains(
                "Topic: my-unknown-1",
                "Unknown-owner",
                "Unknown-description",
                "Unknown-producer",
                "ALL OK"
        )
    }

}