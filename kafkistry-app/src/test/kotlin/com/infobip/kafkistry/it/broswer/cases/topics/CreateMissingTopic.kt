package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class CreateMissingTopic(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareClusterAndMissingTopic() {
        addKafkaClusterToRegistry()
        addTopicToRegistry(newTopic("my-missing-1"))
    }

    @AfterEach
    fun checkExist() {
        assertThat(inspectApi.inspectTopic("my-missing-1").aggStatusFlags.allOk).isEqualTo(true)
    }

    @Test
    fun `test creating single missing topic through UI`() {
        navigateToTopicInspectPage("my-missing-1")

        browser.findElementWithText("Create topic on kafka").click()

        await {
            browser.assertPageText().contains("Create missing topic")
        }
        browser.findElementById("create-missing-btn").click()
        await {
            browser.assertPageText().contains("Creation completed with success")
        }

        navigateBackToInspectTopicPage()
    }

    @Test
    fun `test create where missing topic through UI`() {
        navigateToTopicInspectPage("my-missing-1")

        browser.findElementWithText("Create where missing").click()

        await {
            browser.assertPageText().contains("Missing topic bulk creation")
        }
        browser.findElementById("bulk-create-where-missing-btn").click()
        await {
            browser.assertPageText().contains(
                    "Topic creation completed with success",
                    "Topic creation for all completed"
            )
        }

        navigateBackToInspectTopicPage()
    }

    private fun navigateBackToInspectTopicPage() {
        //go back to topic inspect
        browser.findElementWithText("Back").click()

        await {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            browser.assertPageText().contains("Topic: my-missing-1")
        }
        browser.assertPageText().contains("ALL OK", "OK")
    }

    @Test
    fun `test create missing topics using create all on cluster`() {
        addTopicToRegistry(newTopic("my-missing-2"))    //to have 2 topics

        browser.findElementById("nav-clusters").click()
        await {
            browser.assertPageText().contains("Status of clusters", "my-cluster")
        }

        browser.findElementWithText("my-cluster").click()
        await {
            browser.assertPageText().contains("Cluster: my-cluster")
        }

        browser.findElementWithText("Create all missing...").click()
        await {
            browser.assertPageText().contains("Create missing topics")
        }
        browser.findElementWithText("Create missing topics").click()
        await {
            browser.assertPageText().contains("Missing topics bulk creation", "There are 2 missing topic(s) to create")
        }

        browser.findElementById("bulk-create-missing-btn").click()

        await {
            browser.assertPageText().contains(
                    "missing-1", "missing-2",
                    "Creating topic completed with success",
                    "Creating topic for all completed"
            )
        }

        //go back to cluster page
        browser.findElementWithText("Back").click()
        await {
            assertThat(browser.currentUrl).contains("/clusters/inspect")
            browser.assertPageText().contains("Cluster: my-cluster")
        }

        //trigger refresh now
        browser.findElementWithText("Registry action...").click()
        await {
            browser.assertPageText().contains("Refresh")
        }
        browser.findElementById("refresh-btn").click()

        await {
            assertThat(browser.findElementById("topics").text).contains(
                    "my-missing-1", "OK",
                    "my-missing-2", "OK"
            )
        }
    }

}