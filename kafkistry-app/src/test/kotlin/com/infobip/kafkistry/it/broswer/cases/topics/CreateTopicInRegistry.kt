package com.infobip.kafkistry.it.broswer.cases.topics

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.repository.storage.git.GitRefreshTrigger
import com.infobip.kafkistry.repository.storage.git.GitRepository
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By

abstract class CreateTopicInRegistry(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun addExistingCluster() {
        addKafkaClusterToRegistry()
        assertThat(topicsApi.listTopics())
                .extracting<String> { it.name }
                .doesNotContain("my-topic-1")
    }

    @AfterEach
    fun checkTopicExistInRegistry() {
        assertThat(topicsApi.listTopics())
                .extracting<String> { it.name }
                .contains("my-topic-1")
    }

    @Test
    fun `test creating new topic using wizard UI`() {
        browser.findElementById("nav-topics").click()
        await {
            browser.assertPageText().contains("Status of all topics in registry")
        }

        //open dropdown
        browser.findElementWithText("Create new topic").click()
        browser.findElementWithText("Wizard").click()

        await {
            browser.assertPageText().contains("Create Topic Wizard")
        }

        //fill form first part
        browser.findElementByCssSelector("input[name=topicName]").sendKeys("my-topic-1")
        browser.findElementWithText("On all clusters").scrollIntoView().click()
        browser.findElementByCssSelector("input[name=teamName]").sendKeys("Testers")
        browser.findElementByCssSelector("textarea[name=purpose]").sendKeys("Dummy text description")
        browser.findElementByCssSelector("input[name=producer]").sendKeys("Nothing")
        browser.findElementById("wizard-next-btn").scrollIntoView().click()
        //fill form second part
        browser.findElementByCssSelector("input[name=messagesRateAmount]").sendKeys("2000")
        browser.findElementByCssSelector("input[name=avgMessageSize]").sendKeys("1024")
        browser.findElementByCssSelector("input[name=retentionAmount]").sendKeys("3")
        browser.findElementByCssSelector("input[name=highAvailability][value=STRONG_AVAILABILITY]").scrollIntoView().click()

        //submit
        browser.findElementWithText("Suggest topic configuration").ensureClick()

        await("for redirection to topic creation") {
            assertThat(browser.currentUrl).endsWith("/topics/wizard/create")
        }

        browser.assertPageText().contains("Create new topic from wizard")

        //check dry run inspect
        browser.findElementById("dry-run-inspect-btn").ensureClick()
        await {
            assertThat(browser.findElementById("dry-run-inspect-status").text).contains(
                    "my-cluster", "TO_CREATE"
            )
        }

        //write into specific branch
        browser.findElementByCssSelector("input[name=targetBranch]").sendKeys("my-branch")

        //create topic in registry
        browser.findElementById("create-btn").ensureClick()

        //creation redirects to /topics
        await("to complete topic creation") {
            assertThat(browser.currentUrl).doesNotContain("/topics/wizard/create")
            assertThat(browser.currentUrl).endsWith("/topics")
        }

        browser.assertPageText().contains("Status of all topics in registry")
        browser.findElementByCssSelector(".pending-updates-container tbody tr:nth-child(1)").text.also {
            assertThat(it).contains("my-topic-1", "ADD in my-branch")
        }

        appCtx.getBean(GitRepository::class.java).doOperation { git, _ ->
            val b1Commit = git.repository.resolve("my-branch")
            val mergeResult = git.merge()
                .include(b1Commit).setCommit(true).setMessage("my-branch to mater")
                .call()
            assertThat(mergeResult.mergeStatus.isSuccessful).`as`("Successful merge").isTrue()
        }
        appCtx.getBean(GitRefreshTrigger::class.java).trigger()

        browser.navigate().refresh()
        await {
            browser.assertPageText().contains("Status of all topics in registry")
        }
        browser.findElementByCssSelector(".pending-updates-container").text.also {
            assertThat(it).contains("no pending update requests")
        }

    }

    @Test
    fun `test topic creation with manual configuration`() {
        browser.findElementById("nav-topics").click()
        await {
            browser.assertPageText().contains("Status of all topics in registry")
        }

        //open dropdown
        browser.findElementWithText("Create new topic").click()
        browser.findElementWithText("Manual setup").click()

        await {
            assertThat(browser.currentUrl).endsWith("/topics/create")
            browser.assertPageText().contains("Create Topic")
        }

        browser.findElementByCssSelector("input[name=topicName]").sendKeys("my-topic-1")
        browser.findElementByCssSelector("input[name=owner]").sendKeys("new-owner")
        browser.findElementByCssSelector("textarea[name=description]").sendKeys("new-description")
        browser.findElementByCssSelector("input[name=producer]").sendKeys("new-producer")

        browser.findElementById("dry-run-inspect-btn").ensureClick()
        await {
            browser.assertPageText().contains("Possible status over all clusters after saving", "ALL OK")
        }

        browser.findElementById("create-btn").ensureClick()
        await("to create new topic and redirect to all topics page") {
            assertThat(browser.currentUrl).endsWith("/topics/inspect?topicName=my-topic-1&")
            browser.assertPageText().contains("Topic: my-topic-1")
        }

        browser.assertPageText().contains(
                "my-topic-1", "new-owner", "new-description", "new-producer"
        )
    }

    @Test
    fun `test topic creation with by cloning existing topic`() {
        topicsApi.createTopic(newTopic("clone-source-1", description = "cloning topic"), "add", null)

        browser.findElementById("nav-topics").click()
        await {
            browser.assertPageText().contains("Status of all topics in registry")
        }

        //open dropdown
        browser.findElementWithText("Create new topic").click()
        browser.findElementWithText("Clone existing").click()
        browser.findElementById("cloneInput").run {
            sendKeys("clone-source-1")
            submit()
        }

        await {
            assertThat(browser.currentUrl).contains("/topics/clone-add-new")
            browser.assertPageText().contains("Create new topic by clone")
        }

        browser.findElementByCssSelector("input[name=topicName]").sendKeys("my-topic-1")

        browser.findElementById("create-btn").ensureClick()
        await {
            browser.assertPageText().contains("Please perform 'Dry run inspect config' before saving")
        }
        browser.findElementById("dry-run-inspect-btn").scrollIntoView().click()
        await {
            browser.assertPageText().contains("TO_CREATE")
        }
        assertThat(browser.findElements(By.cssSelector(".blocker-issue-item")).map { it.text })
            .containsOnly(
                "'CONFIG_RULE_VIOLATIONS' for cluster 'my-cluster'",
            )
        browser.findElementByCssSelector("input[name=blockers-consent]").scrollIntoView().click()

        browser.findElementById("create-btn").scrollIntoView().click()
        await("to create new topic and redirect to all topics page") {
            assertThat(browser.currentUrl).endsWith("/topics/inspect?topicName=my-topic-1&")
            browser.assertPageText().contains("Topic: my-topic-1")
        }

        browser.assertPageText().contains(
                "my-topic-1", "cloning topic"
        )
    }
}
