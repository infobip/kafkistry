package com.infobip.kafkistry.it.broswer

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import org.slf4j.LoggerFactory
import java.util.*

class LoggingTestWatcher : TestWatcher {

    private val log = LoggerFactory.getLogger(LoggingTestWatcher::class.java)

    override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
        log.info("Test: DISABLED '{}' - {} disable reason: {}",
            context.displayName, context.testClass.map { it.name }.orElse("[null]"),
            reason.orElse("[unspecified reason]")
        )
    }

    override fun testSuccessful(context: ExtensionContext) {
        log.info("Test: SUCCESS '{}' - {}",
            context.displayName, context.testClass.map { it.name }.orElse("[null]")
        )
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable) {
        log.error("Test: ABORTED '{}' - {} abort reason:",
            context.displayName, context.testClass.map { it.name }.orElse("[null]"),
            cause
        )
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        log.error("Test: FAILED '{}' - {} fail cause:",
            context.displayName, context.testClass.map { it.name }.orElse("[null]"),
            cause
        )
    }
}