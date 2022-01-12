package com.infobip.kafkistry.it.broswer

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.openqa.selenium.OutputType
import org.slf4j.LoggerFactory
import org.testcontainers.shaded.org.apache.commons.io.FileUtils
import java.io.File

class FailedTestScreenshotWatcher : TestWatcher {

    private val log = LoggerFactory.getLogger(FailedTestScreenshotWatcher::class.java)

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        log.info("Test '{}' failed, going to take screenshot (failure cause: {})", context.displayName, cause.toString())
        try {
            val screenshotBytes = BrowserItTestSuite.chrome.webDriver.getScreenshotAs(OutputType.BYTES)
            val file = File("test-failure-screenshots")
                .apply { mkdirs() }
                .let { dir ->
                    val testClassName = context.testClass.map { it.simpleName }.orElse("null")
                    File(dir, "screen_${testClassName}_${context.displayName}.png")
                }
            FileUtils.writeByteArrayToFile(file, screenshotBytes)
            log.info("Test '{}' failed, screenshot saved to: {}", context.displayName, file.absolutePath)
        } catch (ex: Exception) {
            log.info("Test '{}' failed, and failed to take screenshot", context.displayName, ex)
        }

    }
}