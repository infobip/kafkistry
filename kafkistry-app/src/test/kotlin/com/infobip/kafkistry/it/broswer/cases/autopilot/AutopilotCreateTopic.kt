package com.infobip.kafkistry.it.broswer.cases.autopilot

import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties
import com.infobip.kafkistry.hostname.HostnameResolver
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class AutopilotCreateTopic(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareClusterAndMissingTopic() {
        appCtx.getBean(AutopilotEnabledFilterProperties::class.java).enabled = true
        addKafkaClusterToRegistry()
        addTopicToRegistry(newTopic("autopilot-missing-1", properties = TopicProperties(1, 2)))
    }

    @AfterEach
    fun checkExist() {
        appCtx.getBean(AutopilotEnabledFilterProperties::class.java).enabled = false
        assertThat(inspectApi.inspectTopic("autopilot-missing-1").aggStatusFlags.allOk).isEqualTo(true)
    }

    @Test
    fun `test autopilot create missing topic`() {
        browser.findElementById("nav-autopilot").click()
        await {
            assertThat(browser.currentUrl).endsWith("/autopilot")
        }
        await("for action to become pending") {
            browser.navigate().refresh()
            await {
                browser.assertPageText().contains("Actions")
            }
            browser.assertPageText().contains("autopilot-missing-1", "PENDING")
        }
        Thread.sleep(10_000)
        await("for pending action to complete") {
            browser.navigate().refresh()
            await {
                browser.assertPageText().contains("Actions")
            }
            browser.assertPageText().contains("autopilot-missing-1", "SUCCESSFUL")
        }
        browser.findElementWithText("CreateMissingTopic").click()   //expand flow outcomes
        val hostname = appCtx.getBean(HostnameResolver::class.java).hostname
        browser.assertPageText().contains("ago)", hostname, "PENDING", "SUCCESSFUL")
    }

}