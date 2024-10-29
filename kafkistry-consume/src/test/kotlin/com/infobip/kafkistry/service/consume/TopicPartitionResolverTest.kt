package com.infobip.kafkistry.service.consume

import com.google.common.collect.Sets
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.service.consume.serialize.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class TopicPartitionResolverTest {

    private val resolver = TopicPartitionResolver(serializers)

    private fun resolve(key: String, serializer: KeySerializerType, partitions: Int): Partition {
        return resolver.resolvePartition("topic", key, serializer, partitions)
    }

    companion object {

        private val serializers = listOf(
            StringKeySerializer(),
            Base64KeySerializer(),
            IntegerKeySerializer(),
            LongKeySerializer(),
            FloatKeySerializer(),
            DoubleKeySerializer(),
            ShortKeySerializer(),
        )

        @JvmStatic
        fun combinations(): List<Arguments> = Sets.cartesianProduct(
            serializers.map { it.type }.toSet(),
            setOf(1, 4, 10),                    //partitions counts
            setOf("10", "11", "52", "100"),     //keys
        ).map { Arguments { it.toTypedArray() } }
    }

    @ParameterizedTest
    @MethodSource("combinations")
    fun `test in range and consistent`(serializer: KeySerializerType, partitions: Int, key: String) {
        val partition = resolve(key, serializer, partitions)
        assertThat(partition)
            .isGreaterThanOrEqualTo(0)
            .isLessThan(partitions)
        assertThat(resolve(key, serializer, partitions)).`as`("consistent").isEqualTo(partition)
    }

    @Test
    fun `resolve string key x1 partition`() {
        assertThat(resolve("x1", "STRING", 2)).isEqualTo(0)
    }

    @Test
    fun `resolve string key y1 partition`() {
        assertThat(resolve("y1", "STRING", 2)).isEqualTo(1)
    }

    @Test
    fun `resolve int key 1 partition`() {
        assertThat(resolve("1", "INTEGER", 2)).isEqualTo(0)
    }

    @Test
    fun `resolve int key 2 partition`() {
        assertThat(resolve("2", "INTEGER", 2)).isEqualTo(1)
    }

    @Test
    fun `resolve base64 key 10 partitions`() {
        assertThat(resolve("savf6uua", "BASE64", 10)).isEqualTo(8)
    }

    @Test
    fun `test uniform distribution`() {
        val size = 10_000
        val partitions = 10
        val freq = (1..size)
            .map { resolve(it.toString(), "LONG", partitions) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .associate { it.toPair() }
        val expectedCount = size.toDouble() / partitions
        with(SoftAssertions()) {
            freq.forEach { (partition, count) ->
                println("partition=$partition -> count=$count")
                assertThat(count.toDouble())
                    .`as`("count for partition $partition")
                    .isCloseTo(expectedCount, Percentage.withPercentage(10.0))
            }
            assertAll()
        }
    }

}