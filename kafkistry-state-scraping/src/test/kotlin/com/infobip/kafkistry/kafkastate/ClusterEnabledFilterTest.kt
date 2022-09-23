package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.Tag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class ClusterEnabledFilterTest {

    private fun filterOf(
        disabled: String? = "", enabled: String? = "",
        disabledTags: String? = "", enabledTags: String? = "",
    ): ClusterEnabledFilter {
        return KafkaEnabledClustersProperties().apply {
            if (!disabled.isNullOrEmpty()) {
                clusters.excluded = disabled.split(",").toSet()
            }
            if (!enabled.isNullOrEmpty()) {
                clusters.included = enabled.split(",").toSet()
            }
            if (!disabledTags.isNullOrEmpty()) {
                tags.excluded = disabledTags.split(",").toSet()
            }
            if (!enabledTags.isNullOrEmpty()) {
                tags.included = enabledTags.split(",").toSet()
            }
        }.let { ClusterEnabledFilter(it) }
    }

    private fun ClusterEnabledFilter.testTags(vararg tags: Tag): Boolean = enabled(ClusterRef("dummy", listOf(*tags)))

    @Test
    fun `all enabled by default`() {
        val filter = filterOf("", "")
        assertAll(
            { assertThat(filter.enabled("kafka-a1")).isTrue },
            { assertThat(filter.enabled("kafka-a2")).isTrue },
            { assertThat(filter.enabled("kafka-b1")).isTrue },
        )
        val filterNull = filterOf(null, null)
        assertAll(
            { assertThat(filterNull.enabled("kafka-a1")).isTrue },
            { assertThat(filterNull.enabled("kafka-a2")).isTrue },
            { assertThat(filterNull.enabled("kafka-b1")).isTrue },
        )
    }

    @Test
    fun `single disabled`() {
        val filter = filterOf("kafka-b1", "")
        assertAll(
            { assertThat(filter.enabled("kafka-a1")).isTrue },
            { assertThat(filter.enabled("kafka-a2")).isTrue },
            { assertThat(filter.enabled("kafka-b1")).isFalse },
        )
    }

    @Test
    fun `multi disabled`() {
        val filter = filterOf("kafka-a1,kafka-a2", "")
        assertAll(
            { assertThat(filter.enabled("kafka-a1")).isFalse },
            { assertThat(filter.enabled("kafka-a2")).isFalse },
            { assertThat(filter.enabled("kafka-b1")).isTrue },
        )
    }

    @Test
    fun `single enabled`() {
        val filter = filterOf("", "kafka-b1")
        assertAll(
            { assertThat(filter.enabled("kafka-a1")).isFalse },
            { assertThat(filter.enabled("kafka-a2")).isFalse },
            { assertThat(filter.enabled("kafka-b1")).isTrue },
        )
    }

    @Test
    fun `multi enabled`() {
        val filter = filterOf("", "kafka-a2,kafka-b1")
        assertAll(
            { assertThat(filter.enabled("kafka-a1")).isFalse },
            { assertThat(filter.enabled("kafka-a2")).isTrue },
            { assertThat(filter.enabled("kafka-b1")).isTrue },
        )
    }

    @Test
    fun `any tag enabled by default`() {
        val filter = filterOf()
        assertAll(
            { assertThat(filter.testTags()).isTrue },
            { assertThat(filter.testTags("t1")).isTrue },
            { assertThat(filter.testTags("t1", "t2")).isTrue },
        )
    }

    @Test
    fun `only enabled tag`() {
        val filter = filterOf(enabledTags = "prod")
        assertAll(
            { assertThat(filter.testTags()).isFalse() },
            { assertThat(filter.testTags("t1")).isFalse() },
            { assertThat(filter.testTags("t1", "t2")).isFalse() },
            { assertThat(filter.testTags("prod")).isTrue() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isTrue() },
        )
    }

    @Test
    fun `only enabled tags`() {
        val filter = filterOf(enabledTags = "prod,dev")
        assertAll(
            { assertThat(filter.testTags()).isFalse() },
            { assertThat(filter.testTags("t1")).isFalse() },
            { assertThat(filter.testTags("t1", "t2")).isFalse() },
            { assertThat(filter.testTags("prod")).isTrue() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isTrue() },
            { assertThat(filter.testTags("dev")).isTrue() },
            { assertThat(filter.testTags("dev", "prod")).isTrue() },
        )
    }

    @Test
    fun `enabled all tags`() {
        val filter = filterOf(enabledTags = "*")
        assertAll(
            { assertThat(filter.testTags()).isFalse() },
            { assertThat(filter.testTags("t1")).isTrue() },
            { assertThat(filter.testTags("t1", "t2")).isTrue() },
            { assertThat(filter.testTags("prod")).isTrue() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isTrue() },
            { assertThat(filter.testTags("dev")).isTrue() },
            { assertThat(filter.testTags("dev", "prod")).isTrue() },
        )
    }

    @Test
    fun `only disabled tag`() {
        val filter = filterOf(disabledTags = "prod")
        assertAll(
            { assertThat(filter.testTags()).isTrue() },
            { assertThat(filter.testTags("t1")).isTrue() },
            { assertThat(filter.testTags("t1", "t2")).isTrue() },
            { assertThat(filter.testTags("prod")).isFalse() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isFalse() },
        )
    }

    @Test
    fun `only disabled tags`() {
        val filter = filterOf(disabledTags = "prod,dev")
        assertAll(
            { assertThat(filter.testTags()).isTrue() },
            { assertThat(filter.testTags("t1")).isTrue() },
            { assertThat(filter.testTags("t1", "t2")).isTrue() },
            { assertThat(filter.testTags("prod")).isFalse() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isFalse() },
            { assertThat(filter.testTags("dev")).isFalse() },
            { assertThat(filter.testTags("dev", "prod")).isFalse() },
        )
    }

    @Test
    fun `disabled all tags`() {
        val filter = filterOf(disabledTags = "*")
        assertAll(
            { assertThat(filter.testTags()).isTrue() },
            { assertThat(filter.testTags("t1")).isFalse() },
            { assertThat(filter.testTags("t1", "t2")).isFalse() },
            { assertThat(filter.testTags("prod")).isFalse() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isFalse() },
            { assertThat(filter.testTags("dev")).isFalse() },
            { assertThat(filter.testTags("dev", "prod")).isFalse() },
        )
    }

    @Test
    fun `disabled and enabled tags`() {
        val filter = filterOf(disabledTags = "dev", enabledTags = "prod")
        assertAll(
            { assertThat(filter.testTags()).isFalse() },
            { assertThat(filter.testTags("t1")).isFalse() },
            { assertThat(filter.testTags("t1", "t2")).isFalse() },
            { assertThat(filter.testTags("prod")).isTrue() },
            { assertThat(filter.testTags("t1", "t2", "prod")).isTrue() },
            { assertThat(filter.testTags("dev")).isFalse() },
            { assertThat(filter.testTags("dev", "prod")).isFalse() },
        )
    }
}