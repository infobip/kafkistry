package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.TopicProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

abstract class IncreaseTopicReplicationFactor(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topicBefore = newTopic(
            name = "my-replication-1",
            properties = TopicProperties(6, 1)
    )
    private val topicAfter = newTopic(
            name = "my-replication-1",
            properties = TopicProperties(6, 2)
    )

    @BeforeEach
    fun setup() {
        createTopicOnKafka(topicBefore)
        addTopicToRegistry(topicAfter)
        addKafkaClusterToRegistry()
    }

    @AfterEach
    fun checkOk() {
        assertThat(inspectApi.inspectTopicOnCluster("my-replication-1", CLUSTER_IDENTIFIER).status.flags.allOk).isEqualTo(true)
    }

    @Test
    fun `test increase topic replication factor using UI`() {
        navigateToTopicInspectPage("my-replication-1")

        browser.assertPageText().contains(
                "CONFIG MISMATCH", "WRONG_REPLICATION_FACTOR"
        )

        //open inspection of topic on specific cluster page
        browser.findElementWithText(CLUSTER_IDENTIFIER).click()
        await {
            browser.assertPageText().contains("Topic on cluster inspection")
        }

        browser.findElementWithText("Apply replication factor change").scrollIntoView().click()
        await {
            browser.assertPageText().contains("Topic replication factor change on kafka")
        }

        browser.assertPageText().contains(
                "Replication factor before 1",
                "Replication factor after 2",
                "Total number of added partition replicas 6",
                "Leader", "New",
                "Don't forget to do (Verify re-assignments)"
        )

        //trigger actual creation of partitions
        browser.findElementById("assign-new-replicas-btn").scrollIntoView().click()
        await {
            browser.assertPageText().contains("New partition replicas assignment completed with success")
        }

        browser.findElementWithText("Back").scrollIntoView().click()
        await {
            browser.assertPageText().contains(
                    "Topic on cluster inspection"
            )
        }
        browser.assertPageText().contains("HAS_REPLICATION_THROTTLING")

        await {
            browser.findElementWithText("Execute verify re-assignments").scrollIntoView().click()
            await {
                browser.assertPageText().contains("Verified")
            }
            browser.assertPageText().containsPattern(Pattern.compile("(completed successfully(.|\n)*){6}"))
            browser.assertPageText().contains("Topic: Throttle was removed")
        }
        await {
            browser.navigate().refresh()
            await {
                browser.assertPageText().contains("Topic status on cluster OK")
            }
        }

        //go back to topic inspection page
        browser.findElementWithText("Back").scrollIntoView().click()
        await {
            browser.assertPageText().contains(
                    "Topic: my-replication-1"
            )
        }
        browser.assertPageText().contains("ALL OK", "OK")
    }
}