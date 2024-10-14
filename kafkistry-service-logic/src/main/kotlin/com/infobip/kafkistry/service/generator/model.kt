package com.infobip.kafkistry.service.generator

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import java.lang.StringBuilder

data class AssignmentsChange(
        val oldAssignments: Map<Partition, List<BrokerId>>,
        val newAssignments: Map<Partition, List<BrokerId>>,
        val addedPartitionReplicas: Map<Partition, List<BrokerId>>,
        val removedPartitionReplicas: Map<Partition, List<BrokerId>>,
        val newLeaders: Map<Partition, BrokerId>,
        val exLeaders: Map<Partition, BrokerId>,
        val reAssignedPartitionsCount: Int,
) {
    val hasChange: Boolean get() = reAssignedPartitionsCount > 0
}

data class AssignmentsValidation(
        val valid: Boolean,
        val overallProblems: List<String>,
        val partitionProblems: Map<Partition, PartitionValidation>
)

data class PartitionValidation(
        val valid: Boolean,
        val problems: List<String>
)

data class AssignmentsDisbalance (
    val replicasDisbalance: Int,
    val replicasDisbalancePercent: Double,
    val leadersDisbalance: Int,
    val leadersDisbalancePercent: Double,
    val leadersDeepDisbalance: List<Int>
)

data class Broker(
    val id: BrokerId,
    val rack: String? = null,
)

fun List<Broker>.ids() = map { it.id }

data class BrokerLoad(
    val numReplicas: Int,
    val diskBytes: Long,
)

data class PartitionLoad(
    val diskSize: Long,
)

fun Map<Partition, List<BrokerId>>.prettyString(allBrokers: List<BrokerId>): String {
    return with(StringBuilder()) {
        append("|    |")
        allBrokers.forEach { append(" %2d |".format(it)) }
        append("\n+----+")
        allBrokers.forEach { _ -> append("----+") }
        this@prettyString.forEach { (partition, brokers) ->
            append("\n| %2d |".format(partition))
            allBrokers.forEach {
                val brokerStr = when {
                    brokers.isNotEmpty() && brokers[0] == it -> "Lx"
                    it in brokers -> "x"
                    else -> ""
                }
                append(" %2s |".format(brokerStr))
            }
        }
        append("\n")
        toString()
    }
}

//for debugger evaluation
fun Map<Partition, List<BrokerId>>.prettyPrint(allBrokers: List<BrokerId>) {
    println(prettyString(allBrokers))
}

fun AssignmentsChange.currentLeaders(): Map<Partition, BrokerId> =
        oldAssignments.mapValues { it.value.first() } + exLeaders
