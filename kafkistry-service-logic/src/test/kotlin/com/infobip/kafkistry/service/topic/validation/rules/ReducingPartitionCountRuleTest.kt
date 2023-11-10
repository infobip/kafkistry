package com.infobip.kafkistry.service.topic.validation.rules

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.topic.ExistingTopicInfo
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ReducingPartitionCountRuleTest {

    private val rule = ReducingPartitionCountRule()

    private val clusterMetadata = ClusterMetadata(
        ref = ClusterRef("test-cluster"),
        info = null,
    )

    @Test
    fun `not violated - no change`() {
        assertThat(
            rule.check(newTopicDescriptionView(2, 2), clusterMetadata)
        ).isNull()
    }

    @Test
    fun `not violated - increase`() {
        assertThat(
            rule.check(newTopicDescriptionView(2, 4), clusterMetadata)
        ).isNull()
    }

    @Test
    fun `violated - reduce`() {
        assertThat(
            rule.check(newTopicDescriptionView(8, 4), clusterMetadata)
        ).isNotNull
    }

    @Test
    fun `violated - fix config`() {
        val fixedTopicDescription = rule.fixConfig(
            newTopicDescription(5),
            clusterMetadata,
            existingTopicInfo(10),
        )
        assertThat(fixedTopicDescription.propertiesForCluster(clusterMetadata.ref)).isEqualTo(
            TopicProperties(10, 1)
        )
    }

    private fun existingTopicInfo(partitionCount: Int) = ExistingTopicInfo(
        uuid = null,
        name = "test-reducing-partitions-topic",
        properties = TopicProperties(partitionCount, 1),
        config = emptyMap(),
        partitionsAssignments = emptyList(),
        assignmentsDisbalance = mock(),
    )

    private fun newTopicDescriptionView(
        actualPartitionCount: Int,
        expectedPartitionCount: Int,
    ) = TopicDescriptionView(
        name = "test-reducing-partitions-topic",
        properties = TopicProperties(expectedPartitionCount, 1),
        config = emptyMap(),
        presentOnCluster = true,
        originalDescription = newTopicDescription(expectedPartitionCount),
        existingTopicInfo = existingTopicInfo(actualPartitionCount),
    )

    private fun newTopicDescription(expectedPartitionCount: Int) = newTopic(
        name = "test-reducing-partitions-topic",
        properties = TopicProperties(expectedPartitionCount, 1),
    )
}