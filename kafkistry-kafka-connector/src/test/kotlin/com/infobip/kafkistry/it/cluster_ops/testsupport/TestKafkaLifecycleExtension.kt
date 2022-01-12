package com.infobip.kafkistry.it.cluster_ops.testsupport

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.ReflectionUtils
import java.lang.reflect.Modifier

class TestKafkaLifecycleExtension : BeforeAllCallback, AfterAllCallback {

    private fun ExtensionContext.currentTestClass(): Class<*> = testClass
        .orElseThrow { IllegalStateException("Can run only on actual test class") }

    private fun ExtensionContext.kafkaClusterLifecycle(): KafkaClusterLifecycle<*> {
        val testClass = currentTestClass()
        val kafkaFields = ReflectionUtils.findFields(
            testClass,
            { field ->
                KafkaClusterLifecycle::class.java.isAssignableFrom(field.type) && Modifier.isStatic(field.modifiers)
            },
            ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP,
        )
        if (kafkaFields.isEmpty()) {
            throw IllegalStateException("No static fields of ${KafkaClusterLifecycle::class.java} found on test class $testClass")
        }
        return kafkaFields.first()
            .apply { trySetAccessible() }
            .let { it.get(null) as KafkaClusterLifecycle<*> }
    }

    override fun beforeAll(context: ExtensionContext) = context.kafkaClusterLifecycle().start()
    override fun afterAll(context: ExtensionContext) = context.kafkaClusterLifecycle().stop()

}