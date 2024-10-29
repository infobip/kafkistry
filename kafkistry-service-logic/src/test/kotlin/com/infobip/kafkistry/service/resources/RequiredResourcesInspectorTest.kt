package com.infobip.kafkistry.service.resources

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.AbstractDoubleAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import com.infobip.kafkistry.service.newClusterInfo
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.DataRetention
import com.infobip.kafkistry.model.ClusterRef
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RequiredResourcesInspectorTest {

    private val inspector = RequiredResourcesInspector()

    @Test
    fun `test no input data`() {
        val usage = inspect(rate = "0/s")
        assertThat(usage).isEqualTo(TopicResourceRequiredUsages(
                1, 0.0, 0.0,
                0L, 0L, 0L, 0L,
                0.0, 0.0, 0.0, 0.0, 0.0
        ))
    }

    @Test
    fun `test disk usage`() {
        val usage = inspect(
                partitions = 12, replication = 3, brokers = 4, rate = "500/sec", retention = "2-day"
        )
        assertThat(usage.totalDiskUsageBytes) shouldBe 265_420_800_000L             //265GB
        assertThat(usage.diskUsagePerPartitionReplica) shouldBe 7_372_800_000L      //7GB
        assertThat(usage.diskUsagePerBroker) shouldBe 66_355_200_000L               //66GB
    }

    @Test
    fun `test io usage`() {
        val usage = inspect(
                brokers = 5, rate = "5/sec", msgSize = "100B", partitions = 20, replication = 3
        )
        assertThat(usage.bytesPerSec) shouldBeClose 500.0                   // rate:5 * size:100
        assertThat(usage.partitionInBytesPerSec) shouldBeClose 25.0         // bytesPerSec:500 / partitions:20
        assertThat(usage.partitionSyncOutBytesPerSec) shouldBeClose 50.0    // partitionIn:25 * (replication:3 - 1)
        assertThat(usage.brokerProducerInBytesPerSec) shouldBeClose 100.0   // bytesPerSec:500 / brokers:5
        assertThat(usage.brokerSyncBytesPerSec) shouldBeClose 200.0         // partitionIn:25 * followersOn1Broker:8
        assertThat(usage.brokerInBytesPerSec) shouldBeClose 300.0           // brokerIn:100 + brokerSync:200
    }

    private infix fun AbstractObjectAssert<*, *>.shouldBe(any: Any?) = this.isEqualTo(any)
    private infix fun AbstractDoubleAssert<*>.shouldBeClose(num: Double?) = this.isCloseTo(num, Percentage.withPercentage(0.1))

    private fun inspect(
            partitions: Int = 1,
            replication: Int = 1,
            msgSize: String = "1kB",
            retention: String = "1d",
            rate: String = "1/s",
            brokers: Int = 1
    ): TopicResourceRequiredUsages {
        fun String.toTimeUnit() = when (this) {
            "d", "day" -> TimeUnit.DAYS to RateUnit.MSG_PER_DAY
            "s", "sec" -> TimeUnit.SECONDS to RateUnit.MSG_PER_SECOND
            "m", "min" -> TimeUnit.MINUTES to RateUnit.MSG_PER_MINUTE
            "h", "hrs" -> TimeUnit.HOURS to RateUnit.MSG_PER_HOUR
            else -> throw IllegalArgumentException("Unknown unit type: $this")
        }
        fun String.toSizeUnit() = when (this) {
            "B" -> BytesUnit.B
            "kB" -> BytesUnit.KB
            "MB" -> BytesUnit.MB
            "GB" -> BytesUnit.GB
            else -> throw IllegalArgumentException("Unknown unit type: $this")
        }
        fun String.splitAmountUnit() = replace(Regex("(\\d)([a-zA-Z])"), "$1-$2")
                .split("/", "-")
                .let { (amount, unit) -> amount to unit }
        return inspector.inspectTopicResources(
                topicProperties = TopicProperties(partitions, replication),
                resourceRequirements = ResourceRequirements(
                        avgMessageSize = msgSize.splitAmountUnit().let { (amount, unit) ->
                            MsgSize(amount.toInt(), unit.toSizeUnit())
                        },
                        retention = retention.splitAmountUnit().let { (amount, unit) ->
                            DataRetention(amount = amount.toInt(), unit = unit.toTimeUnit().first)
                        },
                        messagesRate = rate.splitAmountUnit().let { (amount, unit) ->
                            MessagesRate(amount = amount.toLong(), factor = ScaleFactor.ONE, unit = unit.toTimeUnit().second)
                        },
                        messagesRateOverrides = emptyMap(),
                        messagesRateTagOverrides = emptyMap(),
                ),
                clusterRef = ClusterRef("none", emptyList()),
                clusterInfo = newClusterInfo(
                        identifier = "none",
                        nodeIds = (1..brokers).toList(),
                        onlineNodeIds = (1..brokers).toList(),
                )
        )
    }
}