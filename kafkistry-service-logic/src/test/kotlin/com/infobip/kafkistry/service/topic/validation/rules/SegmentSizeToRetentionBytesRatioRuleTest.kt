package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.renderMessage
import io.kotlintest.mock.mock
import org.apache.kafka.common.config.TopicConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SegmentSizeToRetentionBytesRatioRuleTest {

    private val rule = SegmentSizeToRetentionBytesRatioRule(
        SegmentSizeToRetentionBytesRatioRuleProperties()
    )

    private val clusterMetadata = ClusterMetadata(
        ref = ClusterRef("test-cluster"),
        info = null,
    )

    private fun newTopicDescriptionView(
        retentionBytes: String?,
        segmentBytes: String?,
        maxMessageBytes: String? = null,
    ) = TopicDescriptionView(
        name = "test-segment-bytes-topic",
        properties = TopicProperties(1, 1),
        config = mapOf(
            TopicConfig.RETENTION_BYTES_CONFIG to retentionBytes,
            TopicConfig.SEGMENT_BYTES_CONFIG to segmentBytes,
            TopicConfig.MAX_MESSAGE_BYTES_CONFIG to maxMessageBytes,
        ),
        presentOnCluster = true,
        originalDescription = newTopic("irrelevant"),
        existingTopicInfo = null,
    )

    private fun newTopicDescription(
        retentionBytes: String?,
        segmentBytes: String?,
        maxMessageBytes: String? = null,
    ) = newTopic(
        name = "test-segment-bytes-topic",
        config = mapOf(
            TopicConfig.RETENTION_BYTES_CONFIG to retentionBytes,
            TopicConfig.SEGMENT_BYTES_CONFIG to segmentBytes,
            TopicConfig.MAX_MESSAGE_BYTES_CONFIG to maxMessageBytes,
        ),
    )

    @Test
    fun `test in acceptable range`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 10_000_000.toString(), segmentBytes = 2_000_000.toString()
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNull()
    }

    @Test
    fun `test fix in acceptable range`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 10_000_000.toString(), segmentBytes = 2_000_000.toString()
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, 2_000_000.toString()
        )
    }

    @Test
    fun `test in below acceptable range`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 10_000_000.toString(), segmentBytes = 20_000.toString()
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNotNull
        assertThat(result?.renderMessage()).contains("having ratio 0.2 which is less than min configured ratio 2.0")
    }

    @Test
    fun `test fix below acceptable range`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 10_000_000.toString(), segmentBytes = 20_000.toString()
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, 200_000.toString()
        )
    }

    @Test
    fun `test above acceptable range`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 10_000_000.toString(), segmentBytes = 9_000_000.toString()
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNotNull
        assertThat(result?.renderMessage()).contains("having ratio 90.0 which is more than max configured ratio 50.0")
    }

    @Test
    fun `test above acceptable range - but restricted by max message bytes`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 10_000_000.toString(), segmentBytes = 9_000_000.toString(), maxMessageBytes = 9_000_000.toString(),
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNull()
    }

    @Test
    fun `test fix above acceptable range`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 10_000_000.toString(), segmentBytes = 9_000_000.toString()
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, 5_000_000.toString()
        )
    }

    @Test
    fun `test fix above acceptable range - but restricted by max message bytes`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 10_000_000.toString(), segmentBytes = 9_000_000.toString(), maxMessageBytes = 6_000_000.toString(),
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, 6_000_000.toString()
        )
    }

    @Test
    fun `test segment bytes not a number`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 10_000_000.toString(), segmentBytes = "abc"
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNotNull
        assertThat(result?.renderMessage()).contains("segment.bytes property is not numeric value, (abc)")
    }

    @Test
    fun `test fix segment bytes not a number`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 10_000_000.toString(), segmentBytes = "abc"
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, 200_000.toString()
        )
    }

    @Test
    fun `test segment bytes bigger than max int`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = Int.MAX_VALUE.toLong().times(20).toString(),
            segmentBytes = Int.MAX_VALUE.toLong().times(2).toString(),
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNotNull
        assertThat(result?.renderMessage()).contains("segment.bytes property is bigger than MAX_INT")
    }

    @Test
    fun `test fix segment bytes bigger than max int`() {
        val topicDescription = newTopicDescription(
            retentionBytes = Int.MAX_VALUE.toLong().times(20).toString(),
            segmentBytes = Int.MAX_VALUE.toLong().times(2).toString(),
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, Int.MAX_VALUE.toString()
        )
    }

    @Test
    fun `test segment bytes at MAX_INT which is below acceptable range`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = Int.MAX_VALUE.toLong().times(100).toString(),
            segmentBytes = Int.MAX_VALUE.toString(),
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNull()
    }

    @Test
    fun `test fix segment bytes at MAX_INT which is below acceptable range`() {
        val topicDescription = newTopicDescription(
            retentionBytes = Int.MAX_VALUE.toLong().times(100).toString(),
            segmentBytes = Int.MAX_VALUE.toLong().toString(),
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, Int.MAX_VALUE.toString()
        )
    }

    @Test
    fun `test retention way bigger than MAX_INT segment bytes below acceptable range`() {
        val topicDescriptionView = newTopicDescriptionView(
            retentionBytes = 40_000_000_000L.toString(),
            segmentBytes = 40_000_000.toString(),
        )
        val result = rule.check(topicDescriptionView, mock())
        assertThat(result).isNotNull
        assertThat(result?.renderMessage()).contains("having ratio 0.1 which is less than min configured ratio 2.0")
    }

    @Test
    fun `test fix retention way bigger than MAX_INT segment bytes below acceptable range`() {
        val topicDescription = newTopicDescription(
            retentionBytes = 40_000_000_000L.toString(),
            segmentBytes = 40_000_000.toString(),
        )
        val fixedTopicDescription = rule.doFixConfig(topicDescription, clusterMetadata)
        assertThat(fixedTopicDescription.perClusterConfigOverrides["test-cluster"]).containsEntry(
            TopicConfig.SEGMENT_BYTES_CONFIG, (40_000_000_000L * 0.02).toLong().toString()
        )
    }

}
