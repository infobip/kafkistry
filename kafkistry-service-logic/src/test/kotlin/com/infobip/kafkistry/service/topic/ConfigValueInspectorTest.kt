package com.infobip.kafkistry.service.topic

import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DEFAULT_CONFIG
import org.apache.kafka.clients.admin.ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.service.topic.ConfigValueInspectorTest.Validity.*
import com.infobip.kafkistry.service.topic.ConfigValueInspectorTest.Inclusion.*
import org.junit.Test

open class ConfigValueInspectorTest {

    private val inspector = ConfigValueInspector()

    fun String?.default() = ConfigValue(this, default = true, readOnly = true, sensitive = false, DEFAULT_CONFIG)
    fun String?.nonDefault() = ConfigValue(this, default = false, readOnly = true, sensitive = false, DYNAMIC_TOPIC_CONFIG)

    enum class Validity {
        VALID, INVALID
    }
    enum class Inclusion {
        INCLUDE, IGNORE
    }

    fun noChange(): ConfigValueChange? = null
    fun change(old: String?, new: String?, toClusterDefault: Boolean): ConfigValueChange = ConfigValueChange("", old, new, toClusterDefault)

    infix fun Validity.shouldBe(valid: Boolean) {
        assertThat(if (valid) VALID else INVALID).isEqualTo(this)
    }
    infix fun Inclusion.shouldBe(needInclude: Boolean) {
        assertThat(if (needInclude) INCLUDE else IGNORE).isEqualTo(this)
    }
    infix fun ConfigValueChange?.shouldBe(change: ConfigValueChange?) {
        assertThat(change?.copy(key = "")).isEqualTo(this?.copy(key = ""))
    }

    fun check(
            key: String, expected: String?, current: ConfigValue, vararg clusterConf: Pair<String, ConfigValue>
    ): Boolean {
        val valueInspection = inspector.checkConfigProperty(
                nameKey = key,
                actualValue = current,
                expectedValue = expected,
                clusterConfig = mapOf(*clusterConf)
        )
        return valueInspection.valid
    }

    fun include(
            key: String, current: ConfigValue, vararg clusterConf: Pair<String, ConfigValue>
    ): Boolean {
        return inspector.needIncludeConfigValue(
                nameKey = key,
                actualValue = current,
                clusterConfig = mapOf(*clusterConf)
        )
    }

    fun change(
            key: String, expected: String?, current: ConfigValue, vararg clusterConf: Pair<String, ConfigValue>
    ): ConfigValueChange? {
        return inspector.requiredConfigValueChange(
                nameKey = key,
                expectedValue = expected,
                actualValue = current,
                clusterConfig = mapOf(*clusterConf)
        )
    }

    class ValidTest : ConfigValueInspectorTest() {

        @Test
        fun `test valid - matching expected - default - cluster default`() {
            VALID shouldBe check("x", "1", "1".default(), "x" to "1".default())
        }

        @Test
        fun `test valid - nothing expected - current default - cluster default`() {
            VALID shouldBe check("x", null, "1".default(), "x" to "1".default())
        }

        @Test
        fun `test valid - matching expected - current non-default - cluster differs current default and non-default`() {
            VALID shouldBe check("x", "1", "1".default(), "x" to "2".default())
        }

        @Test
        fun `test valid - nothing expected - current non-default - cluster non-default matching current`() {
            VALID shouldBe check("x", null, "1".nonDefault(), "x" to "1".nonDefault())
        }

        @Test
        fun `test valid - nothing expected - current default - cluster non-default matching current`() {
            VALID shouldBe check("x", null, "1".default(), "x" to "1".default())
        }

        @Test
        fun `test valid - nothing expected - current default - cluster nothing`() {
            VALID shouldBe check("x", null, "1".default())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster nothing`() {
            INVALID shouldBe check("x", null, "1".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster default miss-matching current`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "x" to "2".default())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster default`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "x" to "2".default())
        }

        @Test
        fun `test invalid - wrong expected - current non-default - cluster default`() {
            INVALID shouldBe check("x", "2", "1".nonDefault(), "x" to "2".default())
        }

        @Test
        fun `test invalid - wrong expected - current default - cluster default`() {
            INVALID shouldBe check("x", "2", "1".default(), "x" to "1".default())
        }

        @Test
        fun `test invalid - nothing expected - current default - cluster different non-default`() {
            INVALID shouldBe check("x", null, "1".default(), "x" to "2".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster different non-default`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "x" to "2".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster different default`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "x" to "2".default())
        }

        @Test
        fun `test valid - nothing expected - current default - cluster null`() {
            VALID shouldBe check("x", null, "1".default(), "x" to null.default())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster null`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "x" to null.default())
        }

        @Test
        fun `test valid - expecting 1 - current matches - cluster null`() {
            VALID shouldBe check("x", "1", "1".default(), "x" to null.default())
        }

        @Test
        fun `test invalid - expecting 2 - current not matching - cluster null`() {
            INVALID shouldBe check("x", "2", "1".nonDefault(), "x" to null.default())
        }

        @Test
        fun `test valid - nothing expected - current non-default - cluster matching current non-default log prefix`() {
            VALID shouldBe check("x", null, "1".nonDefault(), "log.x" to "1".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster differs current non-default log prefix`() {
            INVALID shouldBe check("x", null, "1".nonDefault(), "log.x" to "2".nonDefault())
        }

        @Test
        fun `test valid - 2-1-IV - current non-default - cluster differs current non-default log prefix`() {
            VALID shouldBe check("message.format.version", "2.1-IV", "2.1-IV".nonDefault(), "log.message.format.version" to "2.1".nonDefault())
        }

        @Test
        fun `test valid - nothing expected - current non-default - cluster matches current non-default`() {
            VALID shouldBe check("message.format.version", null, "2.1-IV".nonDefault(), "log.message.format.version" to "2.1".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster miss-matches current non-default`() {
            INVALID shouldBe check("message.format.version", null, "2.1-IV".nonDefault(), "log.message.format.version" to "1.1".nonDefault())
        }

        @Test
        fun `test valid - 1024 - current non-default - cluster differs current non-default`() {
            VALID shouldBe check("max.message.bytes", "1024", "1024".nonDefault(), "message.max.bytes" to "128".nonDefault())
        }

        @Test
        fun `test valid - nothing expected - current non-default - cluster matches current non-default 512`() {
            VALID shouldBe check("max.message.bytes", null, "512".nonDefault(), "message.max.bytes" to "512".nonDefault())
        }

        @Test
        fun `test invalid - nothing expected - current non-default - cluster miss-matches current non-default 2048`() {
            INVALID shouldBe check("max.message.bytes", null, "64".nonDefault(), "message.max.bytes" to "2048".nonDefault())
        }
    }

    class IncludeTest : ConfigValueInspectorTest() {

        @Test
        fun `test include - current non-default - cluster default different`() {
            INCLUDE shouldBe include("x", "1".nonDefault(), "x" to "2".default())
        }

        @Test
        fun `test include - current default - cluster non-default different log`() {
            INCLUDE shouldBe include("x", "1".default(), "log.x" to "2".nonDefault())
        }

        @Test
        fun `test ignore - current default - cluster nothing`() {
            IGNORE shouldBe include("x", "1".default())
        }

        @Test
        fun `test include - current non-default - cluster nothing`() {
            INCLUDE shouldBe include("x", "1".nonDefault())
        }

        @Test
        fun `test include - current default - cluster non-default different`() {
            INCLUDE shouldBe include("x", "1".default(), "x" to "2".nonDefault())
        }

        @Test
        fun `test include - current non-default - cluster non-default different`() {
            INCLUDE shouldBe include("x", "1".nonDefault(), "x" to "2".nonDefault())
        }

        @Test
        fun `test ignore - current default - cluster default same`() {
            IGNORE shouldBe include("x", "1".default(), "x" to "1".default())
        }

        @Test
        fun `test ignore - current default - cluster default same log`() {
            IGNORE shouldBe include("x", "1".default(), "log.x" to "1".default())
        }

        @Test
        fun `test ignore - current non-default - cluster non-default same`() {
            IGNORE shouldBe include("x", "1".nonDefault(), "x" to "1".nonDefault())
        }

        @Test
        fun `test ignore - current non-default - cluster non-default same log`() {
            IGNORE shouldBe include("x", "1".nonDefault(), "log.x" to "1".nonDefault())
        }

        @Test
        fun `test ignore message-format-version - current non-default - cluster non-default same log`() {
            IGNORE shouldBe include("message.format.version", "2.1-IV".nonDefault(), "log.message.format.version" to "2.1".nonDefault())
        }
    }

    class ChangesTest : ConfigValueInspectorTest() {
        @Test
        fun `test no-change - current matches expected`() {
            noChange() shouldBe change("x", "1", "1".default())
        }

        @Test
        fun `test no-change - nothing expected - current default`() {
            noChange() shouldBe change("x", null, "1".default())
        }

        @Test
        fun `test no-change - nothing expected - current default - cluster same default`() {
            noChange() shouldBe change("x", null, "1".default(), "x" to "1".default())
        }

        @Test
        fun `test no-change - nothing expected - current non-default - cluster same non-default`() {
            noChange() shouldBe change("x", null, "1".nonDefault(), "x" to "1".nonDefault())
        }

        @Test
        fun `test change - nothing expected - current non-default - cluster different non-default`() {
            change("2", "1", true) shouldBe change("x", null, "2".nonDefault(), "x" to "1".nonDefault())
        }

        @Test
        fun `test change - current differs expected`() {
            change("1", "2", false) shouldBe change("x", "2", "1".default())
        }

        @Test
        fun `test change - expect nothing - current non-default`() {
            change("1", null, true) shouldBe change("x", null, "1".nonDefault())
        }
        @Test
        fun `test change - expect nothing - current non-default - cluster different non-default`() {
            change("1", "2", true) shouldBe change("x", null, "1".nonDefault(), "x" to "2".nonDefault())
        }

        @Test
        fun `test change - expect nothing - current 2-1-IV non-default - cluster 2-1 matches non-default`() {
            noChange() shouldBe change("message.format.version", null, "2.1-IV".nonDefault(), "log.message.format.version" to "2.1".nonDefault())
        }
    }

}