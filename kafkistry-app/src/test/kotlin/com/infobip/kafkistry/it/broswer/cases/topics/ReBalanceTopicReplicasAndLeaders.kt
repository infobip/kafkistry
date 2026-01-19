package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.TopicProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class ReBalanceTopicReplicasAndLeaders(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topic = newTopic(
            name = "my-rebalance-1",
            properties = TopicProperties(6, 2)
    )

    @BeforeEach
    fun setup() {
        //expect to run on cluster which has brokers with ids: [0, 1, 2]
        val assignments = mapOf(
                0 to listOf(0, 1),
                1 to listOf(1, 2),
                2 to listOf(1, 0),
                3 to listOf(2, 0),
                4 to listOf(1, 0),
                5 to listOf(1, 0)
        )
        createTopicOnKafka(topic, assignments)
        addTopicToRegistry(topic)
        addKafkaClusterToRegistry()
    }

    @AfterEach
    fun checkOk() {
        assertThat(inspectApi.inspectTopicOnCluster("my-rebalance-1", CLUSTER_IDENTIFIER).status.flags.allOk).isEqualTo(true)
    }

    @Test
    fun `test re-balance replicas and leaders using UI`() {
        navigateToTopicInspectPage("my-rebalance-1")

        browser.assertPageText().contains(
                "RUNTIME ISSUE", "PARTITION_REPLICAS_DISBALANCE", "PARTITION_LEADERS_DISBALANCE"
        )

        //open inspection of topic on specific cluster page
        browser.findElementWithText(CLUSTER_IDENTIFIER).scrollIntoView().click()
        await {
            browser.assertPageText().contains("Topic on cluster inspection")
        }

        browser.findElementWithText("Suggest re-balance").ensureClick()
        await {
            browser.assertPageText().contains("Topic re-balance on kafka")
            assertThat(browser.currentUrl).endsWith("reBalanceMode=REPLICAS_THEN_LEADERS")
        }

        browser.assertPageText().contains(
                "Total number of added partition replicas 0",
                "Number of moved/migrated partition replicas 2",
                "Number of re-electing partition leaders 3",
                "Leader", "Leader -L", "New", "New +L"
        )

        //trigger actual rebalance of replicas&leaders
        browser.findElementById("apply-re-assignments-btn").ensureClick()
        await {
            browser.assertPageText().contains("New partition assignments applied with success")
        }

        Thread.sleep(1_500) //should complete re-assignment

        browser.findElementWithText("Back").ensureClick()
        await {
            browser.assertPageText().contains(
                    "Topic my-rebalance-1"
            )
        }
        await {
            browser.navigate().refresh()
            browser.assertPageText().contains(
                    "NEEDS_LEADER_ELECTION",
                    "HAS_UNVERIFIED_REASSIGNMENTS",
                    "WRONG_CONFIG",
            )
        }

        await("wait for sync on new replicas") {
            browser.findElementWithText("Execute verify re-assignments").ensureClick()
            await {
                browser.assertPageText().contains("Verified")
            }
            browser.assertPageText().contains("Throttle was removed")
            browser.assertPageText().doesNotContain("is still in progress", "failed")
        }

        await {
            browser.navigate().refresh()
            await {
                browser.assertPageText().contains("Topic my-rebalance-1")
            }
            browser.assertPageText().contains("Partition and leader assignments over brokers is optimal")
        }

        browser.assertPageText().contains("NEEDS_LEADER_ELECTION")
        browser.findElementById("run-preferred-replica-election-btn").ensureClick()
        await("for election completion") {
            browser.assertPageText().contains("Elections completed")
        }

        await {
            browser.navigate().refresh()
            await {
                browser.assertPageText().contains("Topic my-rebalance-1")
            }
            browser.findElementWithText("Topic status on cluster").scrollIntoView()
            browser.assertPageText().contains("Topic status on cluster OK")
        }
    }
}