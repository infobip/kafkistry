package com.infobip.kafkistry.service.kafkastreams

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.ClusterConsumerGroups
import com.infobip.kafkistry.kafkastate.Maybe
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.TopicName
import org.junit.jupiter.api.Test
import java.util.*

internal class KStreamsAppsDetectorTest {

    private val detector = KStreamsAppsDetector(KStreamConfigProperties())

    @Test
    fun `no apps on empty`() {
        val apps = detector.findKStreamApps(
            "test-cluster",
            ClusterConsumerGroups(consumerGroups = emptyMap()),
            topics = emptyList(),
        )
        assertThat(apps).isEmpty()
    }

    @Test
    fun `few regular groups and topics`() {
        val apps = detector.findKStreamApps(
            "test-cluster",
            ClusterConsumerGroups(
                consumerGroups = mapOf(
                    mockGroup("dummy-group", topics = mapOf("topic1" to 8, "topic2" to 12)),
                    mockGroup("test-group", topics = mapOf("topic1" to 8, "topic2" to 12)),
                    mockGroup("another-group", topics = mapOf("topic3" to 1)),
                )
            ),
            topics = listOf(
                mockTopic("topic1", 8),
                mockTopic("topic2", 12),
                mockTopic("topic3", 1),
            ),
        )
        assertThat(apps).isEmpty()
    }

    @Test
    fun `one kstream join app`() {
        val apps = detector.findKStreamApps(
            "test-cluster",
            ClusterConsumerGroups(
                consumerGroups = mapOf(
                    mockGroup("kstream-app", partitionAssignor = "stream", topics = mapOf("in-1" to 4, "in-2" to 4)),
                )
            ),
            topics = listOf(
                mockTopic("in-1", 4),
                mockTopic("in-2", 4),
                mockTopic("kstream-app-KSTREAM-JOINOTHER-000000005-store-changelog", 4),
                mockTopic("kstream-app-KSTREAM-JOINTHIS-0000000004-store-changelog", 4),
                mockTopic("out", 4),
            ),
        )
        assertThat(apps).containsExactlyInAnyOrder(
            KafkaStreamsApp(
                kafkaStreamAppId = "kstream-app",
                inputTopics = listOf("in-1", "in-2"),
                kStreamInternalTopics = listOf(
                    "kstream-app-KSTREAM-JOINOTHER-000000005-store-changelog",
                    "kstream-app-KSTREAM-JOINTHIS-0000000004-store-changelog",
                )
            ),
        )
    }

    @Test
    fun `multiple kstream apps and other stuff`() {
        val apps = detector.findKStreamApps(
            "test-cluster",
            ClusterConsumerGroups(
                consumerGroups = mapOf(
                    mockGroup("kstream-app", partitionAssignor = "stream", topics = mapOf("in-1" to 4, "in-2" to 4)),
                    mockGroup("dummy-group", topics = mapOf("out" to 4, "topic1" to 12)),
                    mockGroup("test-group", topics = mapOf("in-1" to 4, "in-2" to 4, "topic-2" to 8)),
                    mockGroup("another-group", topics = mapOf("in-1" to 4, "topic3" to 12)),
                    mockGroup("analyze-app", partitionAssignor = "stream", topics = mapOf("topic1" to 12, "topic3" to 12)),
                )
            ),
            topics = listOf(
                mockTopic("topic1", 12),
                mockTopic("topic2", 8),
                mockTopic("topic3", 12),
                mockTopic("in-1", 4),
                mockTopic("in-2", 4),
                mockTopic("out", 4),
                mockTopic("kstream-app-KSTREAM-JOINTHIS-0000000004-store-changelog", 4),
                mockTopic("kstream-app-KSTREAM-JOINOTHER-000000005-store-changelog", 4),
                mockTopic("analyze-app-KSTREAM-JOINTHIS-0000000008-store-changelog", 12),
                mockTopic("analyze-app-KSTREAM-JOINOTHER-000000009-store-changelog", 12),
            ),
        )
        assertThat(apps).containsExactlyInAnyOrder(
            KafkaStreamsApp(
                kafkaStreamAppId = "kstream-app",
                inputTopics = listOf("in-1", "in-2"),
                kStreamInternalTopics = listOf(
                    "kstream-app-KSTREAM-JOINOTHER-000000005-store-changelog",
                    "kstream-app-KSTREAM-JOINTHIS-0000000004-store-changelog",
                )
            ),
            KafkaStreamsApp(
                kafkaStreamAppId = "analyze-app",
                inputTopics = listOf("topic1", "topic3"),
                kStreamInternalTopics = listOf(
                    "analyze-app-KSTREAM-JOINOTHER-000000009-store-changelog",
                    "analyze-app-KSTREAM-JOINTHIS-0000000008-store-changelog",
                )
            ),
        )
    }

    private fun mockGroup(
        groupId: ConsumerGroupId,
        partitionAssignor: String = "range",
        consumerGroupStatus: ConsumerGroupStatus = ConsumerGroupStatus.STABLE,
        numMembers: Int = 1,
        topics: Map<TopicName, Int>,
    ): Pair<ConsumerGroupId, Maybe<ConsumerGroup>> {
        val members = (1..numMembers).map {
            val clientId = "$groupId-$it"
            ConsumerGroupMember(memberId = clientId + "-" + UUID.randomUUID(), clientId, host = "/10.11.12.${it + 1}")
        }
        val offsets = topics.flatMap { (topic, partitions) ->
            (1..partitions).map { partition ->
                TopicPartitionOffset(topic, partition, 12_345L)
            }
        }
        val assignments = offsets.mapIndexed { index, it ->
            val member = members[index % numMembers]
            TopicPartitionMemberAssignment(it.topic, it.partition, member.memberId)
        }
        return groupId to Maybe.Result(
            ConsumerGroup(
                id = groupId,
                status = consumerGroupStatus,
                partitionAssignor = partitionAssignor,
                members = members,
                offsets = offsets,
                assignments = assignments,
            )
        )
    }

    private fun mockTopic(topic: TopicName, partitions: Int = 1) = KafkaExistingTopic(
        name = topic,
        internal = false,
        config = emptyMap(),
        partitionsAssignments = (1..partitions).map {
            PartitionAssignments(
                it, listOf(
                    ReplicaAssignment(
                        brokerId = 1,
                        leader = true,
                        inSyncReplica = true,
                        preferredLeader = true,
                        rank = 0
                    )
                )
            )
        }
    )

}
