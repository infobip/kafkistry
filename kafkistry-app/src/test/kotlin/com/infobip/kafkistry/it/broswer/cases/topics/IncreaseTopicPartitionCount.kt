package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.TopicProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class IncreaseTopicPartitionCount(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topicBefore = newTopic(
            name = "my-partitions-1",
            properties = TopicProperties(3, 2)
    )
    private val topicAfter = newTopic(
            name = "my-partitions-1",
            properties = TopicProperties(12, 2)
    )

    @BeforeEach
    fun setup() {
        createTopicOnKafka(topicBefore)
        addTopicToRegistry(topicAfter)
        addKafkaClusterToRegistry()
    }

    @AfterEach
    fun checkOk() {
        assertThat(inspectApi.inspectTopicOnCluster("my-partitions-1", CLUSTER_IDENTIFIER).status.flags.allOk).isEqualTo(true)
    }

    @Test
    fun `test increase topic partition count using UI`() {
        navigateToTopicInspectPage("my-partitions-1")

        browser.assertPageText().contains(
                "CONFIG MISMATCH", "WRONG_PARTITION_COUNT"
        )

        browser.findElementWithText("Apply partition count change").scrollIntoView().click()
        await {
            browser.assertPageText().contains("Topic partition count change on kafka")
        }

        browser.assertPageText().contains(
                "Partition count before 3",
                "Partition count after 12",
                "Total number of added partition replicas 18",
                "Leader", "In-sync", "New", "New +L"
        )

        //trigger actual creation of partitions
        browser.findElementById("add-partitions-btn").scrollIntoView().click()
        await {
            browser.assertPageText().contains("New partitions creation completed with success")
        }

        browser.findElementWithText("Back").scrollIntoView().click()
        await {
            browser.assertPageText().contains(
                    "Topic: my-partitions-1"
            )
        }
        browser.assertPageText().contains("ALL OK", "OK")
    }
}