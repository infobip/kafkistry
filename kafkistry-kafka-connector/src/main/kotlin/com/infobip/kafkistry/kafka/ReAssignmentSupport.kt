package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.clients.admin.PartitionReassignment
import org.apache.kafka.common.TopicPartition

typealias MoveMap = Map<TopicName, Map<Partition, ReAssignmentSupport.PartitionMove>>

object ReAssignmentSupport {

    data class PartitionMove(
        val sources: Set<BrokerId>,
        val destinations: Set<BrokerId>,
    )

    fun calculateProposedMoveMap(
        currentReassignments: Map<TopicPartition, PartitionReassignment>,
        proposedReassignments: Map<TopicPartition, List<BrokerId>>,
        currentAssignments: Map<TopicPartition, List<BrokerId>>,
    ): MoveMap {
        fun processPartition(
            currentAssignment: List<BrokerId>,
            proposedAssignment: List<BrokerId>?,
            currentReassignment: PartitionReassignment?,
        ): PartitionMove {
            val currentReassignmentAdding = currentReassignment?.addingReplicas().orEmpty().toSet()
            val sources = currentAssignment.toSet() - currentReassignmentAdding
            val destinations = (proposedAssignment.orEmpty().toSet() - currentAssignment) + currentReassignmentAdding
            return PartitionMove(sources, destinations)
        }
        return (proposedReassignments.keys + currentReassignments.keys)
            .map { topicPartition ->
                topicPartition to processPartition(
                    currentAssignment = currentAssignments[topicPartition].orEmpty(),
                    proposedAssignment = proposedReassignments[topicPartition],
                    currentReassignment = currentReassignments[topicPartition],
                )
            }
            .groupBy(
                { (tp, _) -> tp.topic() },
                { (tp, moves) -> tp.partition() to moves },
            )
            .mapValues { it.value.toMap() }
    }

    fun calculateLeaderThrottles(moveMap: MoveMap): Map<String, String> {
        return moveMap.mapValues { (_, partMoveMap) ->
            partMoveMap
                .flatMap { (partition, moves) ->
                    if (moves.destinations.isNotEmpty()) {
                        moves.sources.map { source -> "$partition:$source" }
                    } else {
                        emptyList()
                    }
                }
                .joinToString(separator = ",")
        }
    }

    fun calculateFollowerThrottles(moveMap: MoveMap): Map<String, String> {
        return moveMap.mapValues { (_, partMoveMap) ->
            partMoveMap
                .flatMap { (partition, moves) ->
                    moves.destinations
                        .filter { it !in moves.sources }
                        .map { destination -> "$partition:$destination" }
                }
                .joinToString(separator = ",")
        }
    }

    fun calculateReassigningBrokers(moveMap: MoveMap): Set<BrokerId> {
        return moveMap.flatMap { (_, partMoveMap) ->
            partMoveMap.values.flatMap { it.sources + it.destinations }
        }.toSet()
    }

}