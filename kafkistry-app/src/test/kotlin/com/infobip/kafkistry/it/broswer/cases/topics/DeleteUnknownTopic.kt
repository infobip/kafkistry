package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class DeleteUnknownTopic(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareClusterAndUnknownTopic() {
        createTopicOnKafka(newTopic("my-delete-1"))
        addKafkaClusterToRegistry() //add cluster after creation of topic to get aware that topic exist right away
        assertThat(inspectApi.inspectUnknownTopics())
                .extracting<String> { it.topicName }
                .contains("my-delete-1")
    }

    @AfterEach
    fun checkDeleted() {
        assertThat(inspectApi.inspectUnknownTopics()).isEmpty()
    }

    @Test
    fun `test delete unknown topic through UI`() {
        //open all topics page
        browser.findElementById("nav-topics").click()
        await {
            browser.assertPageText().contains("Status of all topics in registry")
        }
        clearTopicsSearch()
        await {
            browser.assertPageText().contains("UNKNOWN")
        }

        browser.findElementWithText("my-delete-1").click()
        await {
            browser.pageText().contains("Topic: my-delete-1")
        }

        browser.findElementWithText(CLUSTER_IDENTIFIER).scrollIntoView().click()
        await {
            browser.assertPageText().contains("Topic on cluster inspection")
        }

        browser.findElementWithText("Delete topic on kafka").click()
        await {
            browser.assertPageText().contains("Delete actual topic on cluster")
        }

        //try delete without confirming sanity check
        browser.findElementById("delete-topic-btn").click()
        await {
            browser.assertPageText().contains("You did not confirm deletion by entering DELETE correctly")
        }

        browser.findElementByCssSelector("input#delete-confirm").sendKeys("DELETE")
        browser.findElementById("delete-topic-btn").click()
        await {
            browser.assertPageText().contains("Deletion completed with success")
        }

        //go back to topic on cluster inspect
        browser.findElementWithText("Back").click()
        await {
            assertThat(browser.currentUrl).contains("/topics/cluster-inspect")
            browser.assertPageText().contains("Topic my-delete-1")
        }

        await {
            browser.navigate().refresh()
            await(timeoutSecs = 2) {
                browser.assertPageText().contains("UNAVAILABLE", "(no data for selected cluster)")
            }
        }
    }

}