package com.infobip.kafkistry.kafkastate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class ClusterEnabledFilterTest {

    private fun filterOf(disabled: String?, enabled: String?) = KafkaEnabledClustersProperties().apply {
        if (!disabled.isNullOrEmpty()) {
            clusters.excluded = disabled.split(",").toSet()
        }
        if (!enabled.isNullOrEmpty()) {
            clusters.included = enabled.split(",").toSet()
        }
    }.let { ClusterEnabledFilter(it) }

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
}