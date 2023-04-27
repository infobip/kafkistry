package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.newTopic
import com.nhaarman.mockitokotlin2.mock
import org.apache.kafka.common.config.TopicConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Test

class MaxMessageBytesMoreThanSegmentBytesRuleTest {

    private val rule = MaxMessageBytesMoreThanSegmentBytesRule()

    @Test
    fun `test ok`() {
        rule.assertRule("200", "1000", "5000")
            .isNull()
    }

    @Test
    fun `test not ok`() {
        rule.assertRule("10000", "1000", "5000")
            .isNotNull
    }

    private fun MaxMessageBytesMoreThanSegmentBytesRule.assertRule(
        maxMessageBytes: String?,
        segmentBytes: String?,
        retentionBytes: String?,
    ): ObjectAssert<RuleViolation?> {
        val violation = check(newTopicDescriptionView(maxMessageBytes, segmentBytes, retentionBytes), mock())
        return assertThat(violation)
            .`as`("Should be valid for maxMessage=$maxMessageBytes, segment=$segmentBytes retention=$retentionBytes")

    }

    private fun newTopicDescriptionView(
        maxMessageBytes: String?,
        segmentBytes: String?,
        retentionBytes: String?,
    ) = TopicDescriptionView(
        name = "test-segment-bytes-topic",
        properties = TopicProperties(1, 1),
        config = mapOf(
            TopicConfig.MAX_MESSAGE_BYTES_CONFIG to maxMessageBytes,
            TopicConfig.SEGMENT_BYTES_CONFIG to segmentBytes,
            TopicConfig.RETENTION_BYTES_CONFIG to retentionBytes,
        ),
        presentOnCluster = true,
        originalDescription = newTopic("irrelevant"),
        existingTopicInfo = null,
    )


}
