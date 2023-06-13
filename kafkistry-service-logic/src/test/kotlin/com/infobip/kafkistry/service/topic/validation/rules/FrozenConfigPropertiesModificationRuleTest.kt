package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.model.FreezeDirective
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.renderMessage
import com.infobip.kafkistry.service.topic.ExistingTopicInfo
import com.nhaarman.mockitokotlin2.mock
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.common.config.TopicConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrozenConfigPropertiesModificationRuleTest {

    private val rule = FrozenConfigPropertiesModificationRule()

    private val allFreeze = FreezeDirective(
        reasonMessage = "reason", partitionCount = true, replicationFactor = true,
        configProperties = listOf(
            TopicConfig.RETENTION_BYTES_CONFIG,
            TopicConfig.RETENTION_MS_CONFIG,
        ),
    )
    private val noFreeze: FreezeDirective? = null

    @Test
    fun `no directives edit`() {
        val violation = rule.check(
            newTopicDescriptionView(
                description = topicDescription( noFreeze),
                existing = exitingTopic(2, 4, 4000, 8000),
            ), mock()
        )
        assertThat(violation).isNull()
    }

    @Test
    fun `no edits`() {
        val violation = rule.check(
            newTopicDescriptionView(
                description = topicDescription(allFreeze),
                existing = exitingTopic(1, 1, 1000, 1000),
            ), mock()
        )
        assertThat(violation).isNull()
    }

    @Test
    fun `edit all`() {
        val violation = rule.check(
            newTopicDescriptionView(
                description = topicDescription(allFreeze),
                existing = exitingTopic(2, 4, 4000, 8000),
            ), mock()
        )
        assertThat(violation?.renderMessage()).contains(
            "partition-count", "replication-factor",  TopicConfig.RETENTION_MS_CONFIG, TopicConfig.RETENTION_BYTES_CONFIG
        )
    }

    @Test
    fun `edit just retention-ms and replication-factor`() {
        val violation = rule.check(
            newTopicDescriptionView(
                description = topicDescription(allFreeze),
                existing = exitingTopic(1, 4, 1000, 8000),
            ), mock()
        )
        assertThat(violation?.renderMessage())
            .contains("replication-factor", TopicConfig.RETENTION_MS_CONFIG)
            .doesNotContain("partition-count", TopicConfig.RETENTION_BYTES_CONFIG)
    }

    private fun topicDescription(freeze: FreezeDirective?) = newTopic(
        name = "test-freeze-topic",
        properties = TopicProperties(1, 1),
        config = mapOf(
            TopicConfig.RETENTION_BYTES_CONFIG to "1000",
            TopicConfig.RETENTION_MS_CONFIG to "1000",
        ),
        freezeDirectives = listOfNotNull(freeze),
    )

    private fun exitingTopic(
        partitionCount: Int,
        replicationFactor: Int,
        retentionBytes: Long?,
        retentionMs: Long?,
    ) = ExistingTopicInfo(
        name = "test-freeze-topic",
        properties = TopicProperties(partitionCount, replicationFactor),
        config = listOfNotNull(
            retentionBytes?.toString()?.let { TopicConfig.RETENTION_BYTES_CONFIG to it.asConfigValue() },
            retentionMs?.toString()?.let { TopicConfig.RETENTION_MS_CONFIG to it.asConfigValue() },
        ).toMap(),
        partitionsAssignments = emptyList(),
        assignmentsDisbalance = mock(),
    )

    private fun String.asConfigValue() = ConfigValue(
        value = this, default = false, readOnly = false, sensitive = false, source = ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG
    )

    private fun newTopicDescriptionView(
        description: TopicDescription,
        existing: ExistingTopicInfo?,
    ) = TopicDescriptionView(
        name = description.name,
        properties = description.properties,
        config = description.config,
        presentOnCluster = true,
        originalDescription = description,
        existingTopicInfo = existing,
    )
}
