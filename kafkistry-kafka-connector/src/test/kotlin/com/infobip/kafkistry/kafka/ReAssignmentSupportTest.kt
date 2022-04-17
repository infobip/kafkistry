package com.infobip.kafkistry.kafka

import org.apache.kafka.clients.admin.PartitionReassignment
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ReAssignmentSupportTest {

    @Test
    fun `test move map single topic 2 reassigning partitions`() {
        val moveMap = ReAssignmentSupport.calculateProposedMoveMap(
            currentReassignments = emptyMap(),
            proposedReassignments = mapOf(
                TopicPartition("t1", 0) to listOf(0, 1),
                TopicPartition("t1", 1) to listOf(1, 2),
                TopicPartition("t1", 2) to listOf(2, 0),
            ),
            currentAssignments = mapOf(
                TopicPartition("t1", 0) to listOf(0, 1),
                TopicPartition("t1", 1) to listOf(2, 0),
                TopicPartition("t1", 2) to listOf(1, 2),
            ),
        )
        assertThat(moveMap).isEqualTo(
            mapOf(
                "t1" to mapOf(
                    0 to ReAssignmentSupport.PartitionMove(
                        sources = setOf(0, 1),
                        destinations = emptySet(),
                    ),
                    1 to ReAssignmentSupport.PartitionMove(
                        sources = setOf(2, 0),
                        destinations = setOf(1),
                    ),
                    2 to ReAssignmentSupport.PartitionMove(
                        sources = setOf(1, 2),
                        destinations = setOf(0),
                    ),
                )
            )
        )
        val leaderThrottles = ReAssignmentSupport.calculateLeaderThrottles(moveMap)
            .mapValues { it.value.split(",").toSet() }
        assertThat(leaderThrottles).isEqualTo(
            mapOf("t1" to setOf("1:2", "1:0", "2:1", "2:2"))
        )
        val followerThrottles = ReAssignmentSupport.calculateFollowerThrottles(moveMap)
            .mapValues { it.value.split(",").toSet() }
        assertThat(followerThrottles).isEqualTo(
            mapOf("t1" to setOf("1:1", "2:0"))
        )
    }

    @Test
    fun `test move map single topic with in-progress re-assignments`() {
        val moveMap = ReAssignmentSupport.calculateProposedMoveMap(
            currentReassignments = mapOf(
                TopicPartition("t1", 0) to PartitionReassignment(
                    listOf(0, 1, 2), listOf(2), listOf(1)
                ),
            ),
            proposedReassignments = mapOf(
                TopicPartition("t1", 1) to listOf(3, 2),
            ),
            currentAssignments = mapOf(
                TopicPartition("t1", 0) to listOf(0, 1, 2),
                TopicPartition("t1", 1) to listOf(1, 2),
                TopicPartition("t1", 2) to listOf(2, 3),
            ),
        )
        assertThat(moveMap).isEqualTo(
            mapOf(
                "t1" to mapOf(
                    0 to ReAssignmentSupport.PartitionMove(
                        sources = setOf(0, 1),
                        destinations = setOf(2),
                    ),
                    1 to ReAssignmentSupport.PartitionMove(
                        sources = setOf(1, 2),
                        destinations = setOf(3),
                    ),
                )
            )
        )
        val leaderThrottles = ReAssignmentSupport.calculateLeaderThrottles(moveMap)
            .mapValues { it.value.split(",").toSet() }
        assertThat(leaderThrottles)
            .`as`("leader throttles partition:broker")
            .isEqualTo(mapOf("t1" to setOf("0:0", "0:1", "1:1", "1:2")))
        val followerThrottles = ReAssignmentSupport.calculateFollowerThrottles(moveMap)
            .mapValues { it.value.split(",").toSet() }
        assertThat(followerThrottles)
            .`as`("follower throttles partition:broker")
            .isEqualTo(mapOf("t1" to setOf("0:2", "1:3")))
    }
}