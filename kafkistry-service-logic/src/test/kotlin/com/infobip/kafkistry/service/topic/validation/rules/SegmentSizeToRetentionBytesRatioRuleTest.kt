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
    ) = TopicDescriptionView(
        name = "test-segment-bytes-topic",
        properties = TopicProperties(1, 1),
        config = mapOf(
            TopicConfig.RETENTION_BYTES_CONFIG to retentionBytes,
            TopicConfig.SEGMENT_BYTES_CONFIG to segmentBytes,
        ),
        presentOnCluster = true,
        originalDescription = newTopic("irrelevant"),
    )

    private fun newTopicDescription(
        retentionBytes: String?,
        segmentBytes: String?,
    ) = newTopic(
        name = "test-segment-bytes-topic",
        config = mapOf(
            TopicConfig.RETENTION_BYTES_CONFIG to retentionBytes,
            TopicConfig.SEGMENT_BYTES_CONFIG to segmentBytes,
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
        assertThat(result?.renderMessage()).contains("having ratio 0.002 which is less than min configured ratio 0.02")
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
        assertThat(result?.renderMessage()).contains("having ratio 0.9 which is more than max configured ratio 0.5")
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

}