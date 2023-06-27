package com.infobip.kafkistry.it.broswer.cases.clusters

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import org.junit.jupiter.api.Test

abstract class AddClusterToRegistry(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private fun navigateToAddClusterPage() {
        browser.findElementById("nav-clusters").click()
        await {
            browser.assertPageText().contains("Status of clusters")
        }
        val addClusterLink = browser.findElementByLinkText("Add new cluster...")
        addClusterLink.click()

        browser.assertPageText().contains("Add cluster")
    }

    @Test
    fun `test adding cluster to registry through UI`() {
        navigateToAddClusterPage()

        val kafkaConnection = kafkaClient.clusterInfo("").get().connectionString
        browser.findElementById("connection").sendKeys(kafkaConnection)
        browser.findElementById("test-btn").click()

        await("to test connection completes") {
            assertThat(browser.findElementByCssSelector("input[name=clusterIdentifier]"))
                    .extracting { it.isDisplayed }
                    .isEqualTo(true)
        }

        browser.findElementByCssSelector("input[name=clusterIdentifier]").sendKeys("my-cluster")
        browser.findElementById("add-cluster-btn").click()

        await("to test connection completes") {
            browser.assertPageText().contains("Successfully added cluster to registry")
        }

        //go back to clusters
        browser.findElementWithText("Back").click()
        await("to go back to /clusters page") {
            browser.assertPageText().contains("Status of clusters registry")
        }

        //trigger refresh now
        browser.findElementById("refresh-btn").click()

        await("for newly added cluster to become visible") {
            browser.navigate().refresh()
            browser.assertPageText().contains("my-cluster", "VISIBLE")
        }
    }

    @Test
    fun `test failure while adding cluster to registry`() {
        navigateToAddClusterPage()

        browser.findElementById("connection").sendKeys("not.kafka:1234")
        browser.findElementById("test-btn").click()

        await("to test connection fails") {
            browser.assertPageText().contains("Connection test failed", "KafkaException")
        }
    }
}
