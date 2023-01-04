package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class AlterTopicConfig(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topicBefore = newTopic(
            name = "my-alter-1",
            properties = TopicProperties(1, 2),
            config = mapOf(
                    "retention.bytes" to "2147483648",
                    "retention.ms" to "600000"
            )
    )
    private val topicAfter = newTopic(
            name = "my-alter-1",
            properties = TopicProperties(1, 2),
            config = mapOf(
                    "retention.bytes" to "4294967296",
                    "retention.ms" to "1200000"
            )
    )

    @BeforeEach
    fun setup() {
        createTopicOnKafka(topicBefore)
        addTopicToRegistry(topicAfter)
        addKafkaClusterToRegistry()
    }

    @AfterEach
    fun checkOk() {
        assertThat(inspectApi.inspectTopicOnCluster("my-alter-1", CLUSTER_IDENTIFIER).status.flags.allOk).isEqualTo(true)
    }

    @Test
    fun `test alter topic config using UI`() {
        navigateToTopicInspectPage("my-alter-1")

        browser.assertPageText().contains(
                "CONFIG MISMATCH", "WRONG_CONFIG"
        )

        browser.findElementWithText("Update topic config").click()
        await {
            browser.assertPageText().contains("Updating topic config on cluster")
        }

        browser.assertPageText().contains(
                "retention.bytes 2147483648 (2GB) 4294967296 (4GB)",
                "retention.ms 600000 (10 min) 1200000 (20 min)"
        )

        //trigger actual update
        browser.findElementById("update-topic-config-btn").click()
        await {
            browser.assertPageText().contains("Config update completed with success")
        }

        browser.findElementWithText("Back").click()
        await {
            browser.assertPageText().contains(
                    "Topic: my-alter-1"
            )
        }
        browser.assertPageText().contains("ALL OK", "OK")
    }
}