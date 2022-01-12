package com.infobip.kafkistry.it.broswer.cases.clusters

import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class RemoveClusterFromRegistry(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun addExistingCluster() = addKafkaClusterToRegistry()

    @Test
    fun `test removing cluster from registry through UI`() {
        browser.findElementById("nav-clusters").click()

        browser.assertPageText().contains("Status of clusters")

        browser.findElementByLinkText("my-cluster").click()
        await {
            browser.assertPageText().contains("Cluster: my-cluster")
        }

        browser.findElementWithText("Registry action").click()
        await {
            browser.assertPageText().contains("Remove from registry")
        }
        browser.findElementWithText("Remove from registry").click()
        await {
            browser.assertPageText().contains("Removing cluster 'my-cluster'")
        }

        browser.findElementById("delete-btn").click()
        await {
            browser.assertPageText().contains("Cluster is successfully removed from registry")
        }

        //go back to list of clusters
        browser.findElementById("nav-clusters").click()
        await {
            browser.assertPageText().contains("no clusters in registry")
        }

    }
}