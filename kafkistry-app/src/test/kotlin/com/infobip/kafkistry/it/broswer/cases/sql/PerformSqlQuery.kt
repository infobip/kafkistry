package com.infobip.kafkistry.it.broswer.cases.sql

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.sql.DbWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class PerformSqlQuery(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    private val topic1 = newTopic(
            name = "my-sql-1",
            properties = TopicProperties(3, 1),
            config = mapOf("retention.bytes" to "104857600")    //100MB
    )
    private val topic2 = newTopic(
            name = "my-sql-2",
            properties = TopicProperties(12, 3),
            config = mapOf("retention.bytes" to "536870912")    //512MB
    )
    private val topic3 = newTopic(
            name = "my-sql-3",
            properties = TopicProperties(6, 2),
            config = mapOf("retention.bytes" to "209715200")    //200MB
    )

    @BeforeEach
    fun setupTopics() {
        createTopicOnKafka(topic1)
        createTopicOnKafka(topic2)
        createTopicOnKafka(topic3)
        addTopicToRegistry(topic1)
        addTopicToRegistry(topic2)
        addTopicToRegistry(topic3)
        addKafkaClusterToRegistry()
        appCtx.getBean(DbWriter::class.java).writeAll()
    }

    @AfterEach
    fun closeExtraTabs() {
        while (browser.windowHandles.size > 1) {
            browser.switchTo().window(browser.windowHandles.elementAt(1))
            browser.close()
            browser.switchTo().window(browser.windowHandles.elementAt(0))
        }
    }

    @Test
    fun `test query topics with highest retention`() {
        navigateToSql()

        //load query example
        browser.findElementWithText("Query examples").click()
        await {
            browser.assertPageText().contains("Highest total size retention topics")
        }
        browser.findElementWithText("Highest total size retention topics").click()
        await {
            assertThat(getEditorContent()).contains("SELECT", "Topics_ActualConfigs")
        }

        //perform loaded query
        browser.findElementWithText("Execute SQL").click()
        await {
            browser.assertPageText().contains("Got result set of 3 rows")
        }

        val magnifier = "\uD83D\uDD0D"
        browser.assertPageText().contains(
                "1 $magnifier $CLUSTER_IDENTIFIER my-sql-2 12 3 536870912 18",
                "2 $magnifier $CLUSTER_IDENTIFIER my-sql-3 6 2 209715200 2.344",
                "3 $magnifier $CLUSTER_IDENTIFIER my-sql-1 3 1 104857600 0.293"
        )

        //check query is added to url
        assertThat(browser.currentUrl).contains("query=")
        val sqlQuery = getEditorContent()

        assertThat(browser.windowHandles).`as`("Only one tab in browser").hasSize(1)

        //open first result link
        browser.findElementByCssSelector("#sql-result a")
                .scrollIntoView()
                .also { assertThat(it.text).isEqualTo(magnifier) }
                .click()
        //check that it was opened in new tab
        await {
            assertThat(browser.windowHandles).`as`("Two tabs in browser").hasSize(2)
        }

        //switch to new tab
        browser.switchTo().window(browser.windowHandles.elementAt(1))
        await {
            assertThat(browser.currentUrl).contains("/topics/cluster-inspect")
            browser.assertPageText().contains(
                    "Topic on cluster inspection",
                    "Topic my-sql-2",
                    "Cluster $CLUSTER_IDENTIFIER"
            )
        }

        //verify switching back to first tab
        browser.switchTo().window(browser.windowHandles.elementAt(0))
        await {
            assertThat(browser.currentUrl).contains("query=")
            browser.assertPageText().contains("Got result set of 3 rows")
        }

        //verify that url parameter "query" fills in editor
        browser.navigate().refresh()
        await {
            assertThat(getEditorContent()).isEqualTo(sqlQuery)
        }

    }

    @Test
    fun `test execution invalid sql query`() {
        navigateToSql()

        assertThat(getEditorContent()).isEmpty()
        //exec empty
        browser.findElementWithText("Execute SQL").click()
        await {
            browser.assertPageText().contains("KafkistrySQLException", "SQLITE_ERROR")
        }
    }

    @Test
    fun `test use schema help to fill in default query`() {
        navigateToSql()

        browser.findElementWithText("Schema help").click()
        await {
            browser.assertPageText().contains("Table name Column names")
        }
        browser.findElementWithText("Topics_Replicas").ensureClick()
        await {
            assertThat(getEditorContent()).contains("SELECT", "Topics_Replicas")
        }

        browser.findElementWithText("Execute SQL").ensureClick()
        await {
            browser.assertPageText().contains(
                    "Got result set of 20 rows, total count without LIMIT would be ${3 * 1 + 12 * 3 + 6 * 2}"
            )
        }
    }

    private fun navigateToSql() {
        browser.findElementById("nav-sql").click()
        await {
            assertThat(browser.currentUrl).endsWith("/sql")
            browser.assertPageText().contains("Execute SQL")
        }
        await {
            assertThat(getEditorContent()).isNotNull()
        }
    }

    private fun getEditorContent(): String? = browser.executeScript("return editor.getValue();") as? String
}