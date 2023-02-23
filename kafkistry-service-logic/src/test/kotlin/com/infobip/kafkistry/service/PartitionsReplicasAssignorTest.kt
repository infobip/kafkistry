package com.infobip.kafkistry.service

import io.kotlintest.matchers.fail
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.generator.*
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

class PartitionsReplicasAssignorTest {

    private val assignor = PartitionsReplicasAssignor()

    private fun Int.asBrokers(): List<BrokerId> = (1..this).toList()

    private fun assignNewPartitionReplicas(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            numberOfNewPartitions: Int,
            replicationFactor: Int,
            existingPartitionLoads: Map<Partition, PartitionLoad> = existingAssignments.mapValues { PartitionLoad(0L) },
            clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf()
    ): AssignmentsChange {
        val assignmentsChange = assignor.assignNewPartitionReplicas(
                existingAssignments, allBrokers, numberOfNewPartitions, replicationFactor, existingPartitionLoads, clusterBrokersLoad
        )
        val validation = assignor.validateAssignments(assignmentsChange.newAssignments, allBrokers, TopicProperties(
                partitionCount = existingAssignments.size + numberOfNewPartitions,
                replicationFactor = replicationFactor
        ))
        assertThat(validation.valid).`as`("Validation $validation").isTrue()
        return assignmentsChange
    }

    private fun assignPartitionsNewReplicas(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<Int>,
            replicationFactorIncrease: Int,
            existingPartitionLoads: Map<Partition, PartitionLoad> = existingAssignments.mapValues { PartitionLoad(0L) },
            clusterBrokersLoad: Map<BrokerId, BrokerLoad> = mapOf()
    ): AssignmentsChange {
        val assignmentsChange = assignor.assignPartitionsNewReplicas(
                existingAssignments, allBrokers, replicationFactorIncrease, existingPartitionLoads, clusterBrokersLoad
        )
        val validation = assignor.validateAssignments(assignmentsChange.newAssignments, allBrokers, TopicProperties(
                partitionCount = existingAssignments.size,
                replicationFactor = (existingAssignments[0]?.size ?: 0) + replicationFactorIncrease
        ))
        assertThat(validation.valid).`as`("Validation $validation").isTrue()
        return assignmentsChange
    }

    private fun assignmentsDisbalance(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad> = existingAssignments.mapValues { PartitionLoad(0L) }
    ): AssignmentsDisbalance = assignor.assignmentsDisbalance(existingAssignments, allBrokers, existingPartitionLoads)

    private fun reBalanceReplicasThenLeaders(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad> = existingAssignments.mapValues { PartitionLoad(0L) }
    ): AssignmentsChange = assignor.reBalanceReplicasThenLeaders(existingAssignments, allBrokers, existingPartitionLoads)

    private fun reBalanceReplicasAssignments(
            existingAssignments: Map<Partition, List<BrokerId>>,
            allBrokers: List<BrokerId>,
            existingPartitionLoads: Map<Partition, PartitionLoad> = existingAssignments.mapValues { PartitionLoad(0L) }
    ): AssignmentsChange = assignor.reBalanceReplicasAssignments(existingAssignments, allBrokers, existingPartitionLoads)

    @Test
    fun `reproduce encountered issue 1`() {
        val disbalance = assignor.assignmentsDisbalance(
            existingAssignments = mapOf(
                0 to listOf(4, 1, 6, 3),
                1 to listOf(5, 2, 3, 6),
            ),
            allBrokers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            existingPartitionLoads = emptyMap(),
        )
        assertThat(disbalance.replicasDisbalance).isEqualTo(2)
        assertThat(disbalance.leadersDisbalance).isEqualTo(0)
    }

    ///////////////////////////////////////////////////////////
    // tests for adding new partitions to existing assignments
    ///////////////////////////////////////////////////////////

    @Test
    fun `assign nothing to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 0, 1)
        assertThat(assignments.addedPartitionReplicas).isEmpty()
    }

    @Test
    fun `assign one partition and one replica to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 1, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(0 to listOf(1))
        )
    }

    @Test
    fun `assign one partition and two replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 1, 2)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(0 to listOf(1, 2))
        )
    }

    @Test
    fun `assign one partition and brokerNum replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 1, 3)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(0 to listOf(1, 2, 3))
        )
    }

    @Test
    fun `assign two partitions and one replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 2, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2)
                )
        )
    }

    @Test
    fun `assign two partitions and numBroker replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 2, 3)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1, 3, 2),
                        1 to listOf(2, 1, 3)
                )
        )
    }

    @Test
    fun `assign numBrokers partitions and one replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 3, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3)
                )
        )
    }

    @Test
    fun `assign numBrokers partitions and two replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 3, 2)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1)
                )
        )
    }

    @Test
    fun `assign numBrokers partitions and numBrokers replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 3, 3)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1, 2, 3),
                        1 to listOf(2, 3, 1),
                        2 to listOf(3, 1, 2)
                )
        )
    }

    @Test
    fun `assign 2x numBrokers partitions and one replica to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 6, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3),
                        3 to listOf(1),
                        4 to listOf(2),
                        5 to listOf(3)
                )
        )
    }

    @Test
    fun `assign 2x numBrokers partitions and two replicas to nothing`() {
        val assignments = assignNewPartitionReplicas(emptyMap(), 3.asBrokers(), 6, 2)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1),
                        3 to listOf(1, 2),
                        4 to listOf(2, 3),
                        5 to listOf(3, 1)
                )
        )
    }

    @Test
    fun `assign one partition and one replica to one partition with one replica`() {
        val assignments = assignNewPartitionReplicas(mapOf(0 to listOf(1)), 3.asBrokers(), 1, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(1 to listOf(2))
        )
    }

    @Test
    fun `assign one partition and one replica to one partition with one replica 2`() {
        val assignments = assignNewPartitionReplicas(mapOf(0 to listOf(2)), 3.asBrokers(), 1, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(1 to listOf(3))
        )
    }

    @Test
    fun `assign one partition and one replica to one partition with one replica 3`() {
        val assignments = assignNewPartitionReplicas(mapOf(0 to listOf(3)), 3.asBrokers(), 1, 1)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(1 to listOf(1))
        )
    }

    @Test
    fun `assign one partition and two replicas to one partition with two replicas`() {
        val assignments = assignNewPartitionReplicas(mapOf(0 to listOf(1, 2)), 3.asBrokers(), 1, 2)
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(1 to listOf(3, 1))
        )
    }

    @Test
    fun `assign two partitions and two replicas to one partition with two replicas`() {
        val assignments = assignNewPartitionReplicas(
                mapOf(0 to listOf(1, 2)),
                3.asBrokers(),
                2,
                2
        )
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        1 to listOf(2, 3),
                        2 to listOf(3, 1)
                )
        )
    }

    @Test(expected = KafkistryValidationException::class)
    fun `assign two partitions and 6 replicas to nothing but overloading`() {
        assignNewPartitionReplicas(mapOf(), 3.asBrokers(), 2, 6)
    }

    @Test
    fun `assign 4 partitions and two replicas to nothing with loaded cluster`() {
        val assignments = assignNewPartitionReplicas(
                mapOf(),
                8.asBrokers(), 4, 2,
                mapOf(),
                mapOf(
                        1 to BrokerLoad(2, 0L),
                        2 to BrokerLoad(3, 0L),
                        3 to BrokerLoad(4, 0L),
                        5 to BrokerLoad(1, 0L),
                        6 to BrokerLoad(1, 0L),
                        7 to BrokerLoad(1, 0L)
                )
        )
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(4, 7),
                        1 to listOf(8, 1),
                        2 to listOf(5, 2),
                        3 to listOf(6, 3)
                )
        )
    }

    @Test
    fun `dont assign on loaded cluster`() {
        val assignments = assignNewPartitionReplicas(
                mapOf(),
                4.asBrokers(), 2, 1,
                mapOf(),
                mapOf(
                        1 to BrokerLoad(100, 0L),
                        2 to BrokerLoad(5, 0L),
                        3 to BrokerLoad(100, 0L),
                        4 to BrokerLoad(0, 0L)
                )
        )
        assertThat(assignments.addedPartitionReplicas).isEqualTo(
                mapOf(
                        0 to listOf(4),
                        1 to listOf(2)
                )
        )
    }

    //////////////////////////////////////////////
    // tests for increasing replication factor
    //////////////////////////////////////////////

    @Test(expected = KafkistryValidationException::class)
    fun `increase replication factor by on single partition on single broker`() {
        assignPartitionsNewReplicas(
                mapOf(0 to listOf(1)),
                1.asBrokers(), 1
        )
    }

    @Test
    fun `increase replication factor by 1 on 2 partitions on 2 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2)
                ), 2.asBrokers(), 1
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 1)
                )
        )
    }

    @Test
    fun `increase replication factor by 1 on 3 partitions on 3 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1)
                ), 3.asBrokers(), 1
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 2, 3),
                        1 to listOf(2, 3, 1),
                        2 to listOf(3, 1, 2)
                )
        )
    }

    @Test
    fun `increase replication factor by 1 on 3 partitions on 6 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3)
                ), 6.asBrokers(), 1
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 4),
                        1 to listOf(2, 5),
                        2 to listOf(3, 6)
                )
        )
    }

    @Test
    fun `increase replication factor by 2 on 3 partitions on 6 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3)
                ), 6.asBrokers(), 2
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 4, 2),
                        1 to listOf(2, 5, 3),
                        2 to listOf(3, 6, 4)
                )
        )
    }

    @Test
    fun `increase replication factor by 1 on 6 partitions on 3 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3),
                        3 to listOf(1),
                        4 to listOf(2),
                        5 to listOf(3)
                ), 3.asBrokers(), 1
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1),
                        3 to listOf(1, 2),
                        4 to listOf(2, 3),
                        5 to listOf(3, 1)
                )
        )
    }

    @Test
    fun `increase replication factor by 2 on 6 partitions on 3 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3),
                        3 to listOf(1),
                        4 to listOf(2),
                        5 to listOf(3)
                ), 3.asBrokers(), 2
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 2, 3),
                        1 to listOf(2, 3, 1),
                        2 to listOf(3, 1, 2),
                        3 to listOf(1, 2, 3),
                        4 to listOf(2, 3, 1),
                        5 to listOf(3, 1, 2)
                )
        )
    }

    @Test
    fun `increase replication factor by 2 on 6 partitions on 6 brokers`() {
        val assignments = assignPartitionsNewReplicas(
                mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3),
                        3 to listOf(4),
                        4 to listOf(5),
                        5 to listOf(6)
                ), 6.asBrokers(), 2
        )
        assertThat(assignments.newAssignments).isEqualTo(
                mapOf(
                        0 to listOf(1, 2, 3),
                        1 to listOf(2, 3, 4),
                        2 to listOf(3, 4, 5),
                        3 to listOf(4, 5, 6),
                        4 to listOf(5, 6, 1),
                        5 to listOf(6, 1, 2)
                )
        )
    }

    ///////////////////////////////////////////////////////////
    // tests validating assignments
    ///////////////////////////////////////////////////////////

    @Test
    fun `valid assignment optimal`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isTrue()
    }

    @Test
    fun `valid assignment on two brokers only`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(1, 2),
                        2 to listOf(2, 1)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isTrue()
    }

    @Test
    fun `invalid assignment missing partition`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).isNotEmpty
    }

    private fun extract(
            extractor: (Pair<Partition, PartitionValidation>) -> Any?
    ): Function<Pair<Partition, PartitionValidation>, Any?> {
        return Function { extractor(it) }
    }

    @Test
    fun `invalid assignment wrong replication on all partitions`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1),
                        1 to listOf(2),
                        2 to listOf(3)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(0)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, false, 1),
                        tuple(1, false, 1),
                        tuple(2, false, 1)
                )
    }

    @Test
    fun `invalid assignment wrong replication on one partition`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 2, 1)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(0)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, true, 0),
                        tuple(1, true, 0),
                        tuple(2, false, 1)
                )
    }

    @Test
    fun `invalid assignment wrong missing partition`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(1)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, true, 0),
                        tuple(1, true, 0),
                        tuple(2, false, 1)
                )
    }

    @Test
    fun `invalid assignment wrong extra partition`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 1),
                        3 to listOf(1, 3)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(1)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, true, 0),
                        tuple(1, true, 0),
                        tuple(2, true, 0)
                )
    }

    @Test
    fun `invalid assignment unknown broker`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 3),
                        2 to listOf(3, 4)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(0)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, true, 0),
                        tuple(1, true, 0),
                        tuple(2, false, 1)
                )
    }

    @Test
    fun `invalid assignment duplicate broker`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(2, 2),
                        2 to listOf(3, 1)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(0)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, true, 0),
                        tuple(1, false, 1),
                        tuple(2, true, 0)
                )
    }

    @Test
    fun `invalid all problems`() {
        val validation = assignor.validateAssignments(
                assignments = mapOf(
                        1 to listOf(2, 2, 1, 4),
                        2 to listOf(3, 1),
                        5 to listOf(1, 2)
                ),
                allBrokers = listOf(1, 2, 3),
                topicProperties = TopicProperties(3, 2)
        )
        assertThat(validation.valid).isFalse()
        assertThat(validation.overallProblems).hasSize(2)
        assertThat(validation.partitionProblems.toList())
                .extracting(extract { it.first }, extract { it.second.valid }, extract { it.second.problems.size })
                .containsExactly(
                        tuple(0, false, 1),
                        tuple(1, false, 3),
                        tuple(2, true, 0)
                )
    }


    @Test
    fun `re-balance already optimal`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(3, 1)
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments.addedPartitionReplicas).isEmpty()
        assertThat(assignments.removedPartitionReplicas).isEmpty()
        assertThat(assignmentsDisbalance(existingAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance one move`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(2, 3)
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(1, 2),
                        1 to listOf(3, 1),
                        2 to listOf(2, 3)
                ),
                addedPartitionReplicas = mapOf(1 to listOf(1)),
                removedPartitionReplicas = mapOf(1 to listOf(2)),
                newLeaders = mapOf(1 to 3),
                exLeaders = mapOf(1 to 2),
                reAssignedPartitionsCount = 1,
        ))
        val disbalance = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalance.replicasDisbalance).isEqualTo(1)
        assertThat(disbalance.leadersDisbalance).isEqualTo(1)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance more moves 1`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2, 3),
                1 to listOf(1, 2, 3),
                2 to listOf(2, 3, 4),
                3 to listOf(2, 3, 4)
        )
        val allBrokers = listOf(1, 2, 3, 4, 5, 6)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(5, 1, 3),
                        1 to listOf(1, 2, 6),
                        2 to listOf(3, 5, 4),
                        3 to listOf(2, 4, 6)
                ),
                addedPartitionReplicas = mapOf(
                        0 to listOf(5),
                        1 to listOf(6),
                        2 to listOf(5),
                        3 to listOf(6)
                ),
                removedPartitionReplicas = mapOf(
                        0 to listOf(2),
                        1 to listOf(3),
                        2 to listOf(2),
                        3 to listOf(3)
                ),
                newLeaders = mapOf(0 to 5, 2 to 3),
                exLeaders = mapOf(0 to 1, 2 to 2),
                reAssignedPartitionsCount = 4,
        ))
        val disbalance = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalance.replicasDisbalance).isEqualTo(4)
        assertThat(disbalance.leadersDisbalance).isEqualTo(2)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(disbalance(
                0, 0, 0.0, 0.0, listOf(0, 0, 1)
        ))
    }

    @Test
    fun `re-balance more moves 3`() {
        val existingAssignments = (0..9)
                .associate { partition ->
                    partition to when {
                        partition % 2 == 0 -> listOf(1, 2)
                        else -> listOf(2, 1)
                    }
                }
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments.addedPartitionReplicas).hasSize(6)
        assertThat(assignments.removedPartitionReplicas).hasSize(6)
        val disbalance = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalance.replicasDisbalance).isEqualTo(6)
        assertThat(disbalance.leadersDisbalance).isEqualTo(3)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance more moves 2`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2, 3, 4),
                1 to listOf(2, 3, 4, 5),
                2 to listOf(3, 4, 5, 6)
        )
        val allBrokers = listOf(1, 2, 3, 4, 5, 6)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(1, 2, 3, 6),
                        1 to listOf(2, 1, 4, 5),
                        2 to listOf(3, 4, 5, 6)
                ),
                addedPartitionReplicas = mapOf(
                        0 to listOf(6),
                        1 to listOf(1)
                ),
                removedPartitionReplicas = mapOf(
                        0 to listOf(4),
                        1 to listOf(3)
                ),
                newLeaders = emptyMap(),
                exLeaders = emptyMap(),
                reAssignedPartitionsCount = 2,
        ))
        val disbalance = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalance.replicasDisbalance).isEqualTo(2)
        assertThat(disbalance.leadersDisbalance).isEqualTo(0)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(disbalance(
                0, 0, 0.0, 0.0, listOf(0, 0, 0, 1)
        ))
    }

    @Test
    fun `re-balance all occupied`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2, 3),
                1 to listOf(1, 2, 3),
                2 to listOf(1, 2, 3)
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(2, 3, 1),
                        1 to listOf(3, 1, 2),
                        2 to listOf(1, 2, 3)
                ),
                addedPartitionReplicas = emptyMap(),
                removedPartitionReplicas = emptyMap(),
                newLeaders = mapOf(0 to 2, 1 to 3),
                exLeaders = mapOf(0 to 1, 1 to 1),
                reAssignedPartitionsCount = 2,
        ))
        val disbalance = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalance.replicasDisbalance).isEqualTo(0)
        assertThat(disbalance.leadersDisbalance).isEqualTo(2)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))
    }

    @Test
    fun `re-balance preferred leader already in balance 1`() {
        val existingAssignments = mapOf(
                0 to listOf(1),
                1 to listOf(2)
        )
        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, listOf(1, 2))
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1),
                1 to listOf(2)
        ))
    }

    @Test
    fun `re-balance preferred leader already in balance 2`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 4),
                1 to listOf(2, 5),
                2 to listOf(3, 6)
        )
        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, listOf(1, 2, 3, 4, 5, 6))
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1, 4),
                1 to listOf(2, 5),
                2 to listOf(3, 6)
        ))
    }

    @Test
    fun `re-balance example case full re-balance`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(1, 2),
                2 to listOf(2, 3)
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(3, 1),
                        1 to listOf(1, 2),
                        2 to listOf(2, 3)
                ),
                addedPartitionReplicas = mapOf(0 to listOf(3)),
                removedPartitionReplicas = mapOf(0 to listOf(2)),
                newLeaders = mapOf(0 to 3),
                exLeaders = mapOf(0 to 1),
                reAssignedPartitionsCount = 1,
        ))
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance preferred leader is not new replica`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 1),
                2 to listOf(2, 3)
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignments).isEqualTo(AssignmentsChange(
                oldAssignments = existingAssignments,
                newAssignments = mapOf(
                        0 to listOf(1, 3),
                        1 to listOf(2, 1),
                        2 to listOf(3, 2)
                ),
                addedPartitionReplicas = mapOf(0 to listOf(3)),
                removedPartitionReplicas = mapOf(0 to listOf(2)),
                newLeaders = mapOf(2 to 3),
                exLeaders = mapOf(2 to 2),
                reAssignedPartitionsCount = 2,
        ))
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance preferred leader is not new replica 2`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(1, 2),
                2 to listOf(1, 2)
        )
        val allBrokers = (1..6).toList()
        val assignments = reBalanceReplicasAssignments(existingAssignments, allBrokers)
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(3, 6),
                1 to listOf(1, 4),
                2 to listOf(2, 5)
        ))
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(2))
    }

    @Test
    fun `re-balance preferred leader`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(1, 2)
        )
        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, listOf(1, 2))
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(2, 1),
                1 to listOf(1, 2)
        ))
    }

    @Test
    fun `re-balance preferred leader 2`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(1, 3)
        )
        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, listOf(1, 2, 3))
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(3, 1)
        ))
    }

    @Test
    fun `re-balance big topic on big cluster`() {
        val existingAssignments = (0..99).associateWith { (1..25).toList() }
        val allBrokers = (1..50).toList()
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        val disbalanceBefore = assignmentsDisbalance(existingAssignments, allBrokers)
        assertThat(disbalanceBefore).isEqualTo(disbalance(1250, 98, 50.0, 98.0, (1..25).map { 98 }))
        val disbalanceAfter = assignmentsDisbalance(assignments.newAssignments, allBrokers)
        assertThat(disbalanceAfter.replicasDisbalance).isEqualTo(0)
        assertThat(disbalanceAfter.leadersDisbalance).isEqualTo(0)
        assertThat(disbalanceAfter.leadersDeepDisbalance).containsOnly(0)
    }

    @Test
    fun `re-balance big partition count topic on small cluster`() {
        val existingAssignments = (0..999).associate {
            it to (1..3).toList()
        }
        val allBrokers = (1..6).toList()
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))
    }

    @Test
    fun `re-balance big partition count topic on big cluster`() {
        val existingAssignments = (0..999).associateWith { (1..3).toList() }
        val allBrokers = (1..300).toList()
        val assignments = reBalanceReplicasThenLeaders(existingAssignments, allBrokers)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))
    }

    @Test
    fun `changes example disbalance`() {
        val allBrokers = (1..6).toList()

        val assignmentsP1R1 = assignor.assignNewPartitionReplicas(emptyMap(), allBrokers, 1, 1, emptyMap())
        assertThat(assignmentsP1R1.validate(allBrokers, 1, 1).valid).isTrue()
        assertThat(assignmentsP1R1.disbalance(allBrokers)).isEqualTo(noDisbalance(1))

        val assignmentsP1R3 = assignor.assignPartitionsNewReplicas(assignmentsP1R1.newAssignments, allBrokers, 2, emptyMap())
        assertThat(assignmentsP1R3.validate(allBrokers, 1, 3).valid).isTrue()
        assertThat(assignmentsP1R3.disbalance(allBrokers)).isEqualTo(noDisbalance(3))

        val assignmentsP6R3 = assignor.assignNewPartitionReplicas(assignmentsP1R3.newAssignments, allBrokers, 5, 3, emptyMap())
        assertThat(assignmentsP6R3.validate(allBrokers, 6, 3).valid).isTrue()
        assertThat(assignmentsP6R3.disbalance(allBrokers)).isEqualTo(noDisbalance(3))

        val assignmentsP6R4 = assignor.assignPartitionsNewReplicas(assignmentsP6R3.newAssignments, allBrokers, 1, emptyMap())
        assertThat(assignmentsP6R4.validate(allBrokers, 6, 4).valid).isTrue()
        assertThat(assignmentsP6R4.disbalance(allBrokers)).isEqualTo(noDisbalance(4))

        val assignmentsP6R6 = assignor.assignPartitionsNewReplicas(assignmentsP6R4.newAssignments, allBrokers, 2, emptyMap())
        assertThat(assignmentsP6R6.validate(allBrokers, 6, 6).valid).isTrue()
        assertThat(assignmentsP6R6.disbalance(allBrokers)).isEqualTo(noDisbalance(6))
    }

    @Test
    fun `adding replicas doesn't generate disbalance`() {
        val existingAssignments = mapOf(
                0 to listOf(2),
                1 to listOf(5),
                2 to listOf(4),
                3 to listOf(1),
                4 to listOf(3),
                5 to listOf(0)
        )
        val allBrokers = (0..5).toList()
        val clusterBrokersLoad = mapOf(
                0 to BrokerLoad(6, 0L),
                2 to BrokerLoad(7, 0L),
                1 to BrokerLoad(6, 0L),
                3 to BrokerLoad(6, 0L),
                5 to BrokerLoad(1, 0L),
                4 to BrokerLoad(1, 0L)
        )
        val assignments = assignor.assignPartitionsNewReplicas(
                existingAssignments = existingAssignments,
                allBrokers = allBrokers,
                replicationFactorIncrease = 2,
                existingPartitionLoads = existingAssignments.mapValues { PartitionLoad(0L) },
                clusterBrokersLoad = clusterBrokersLoad
        )
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))
        assertThatExistingAssignmentsAreNotMoved(existingAssignments, assignments)
    }

    @Test
    fun `adding partitions doesn't generate disbalance`() {
        val existingAssignments = mapOf(
                0 to listOf(0, 1, 3)
        )
        val allBrokers = (0..5).toList()
        val clusterBrokersLoad = mapOf(
                0 to BrokerLoad(1, 0L),
                1 to BrokerLoad(1, 0L),
                2 to BrokerLoad(0, 0L),
                3 to BrokerLoad(1, 0L),
                4 to BrokerLoad(0, 0L),
                5 to BrokerLoad(0, 0L)
        )
        val assignments = assignor.assignNewPartitionReplicas(
                existingAssignments = existingAssignments,
                allBrokers = allBrokers,
                numberOfNewPartitions = 5,
                replicationFactor = 3,
                existingPartitionLoads = existingAssignments.mapValues { PartitionLoad(0L) },
                clusterBrokersLoad = clusterBrokersLoad
        )
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))
        assertThatExistingAssignmentsAreNotMoved(existingAssignments, assignments)
    }

    @Test
    fun `reproduce issue increasing RF from 3 to 4`() {
        val allBrokers = (0..5).toList()

        //nothing to (1, 3)
        val assignments1 = assignor.assignNewPartitionReplicas(
                existingAssignments = emptyMap(),
                allBrokers = allBrokers,
                numberOfNewPartitions = 1,
                replicationFactor = 3,
                existingPartitionLoads = emptyMap(),
                clusterBrokersLoad = emptyMap()
        )
        assertThat(assignmentsDisbalance(assignments1.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))

        //increase partitions (1, 3) to (6, 3)
        val assignments2 = assignor.assignNewPartitionReplicas(
                existingAssignments = assignments1.newAssignments,
                allBrokers = allBrokers,
                numberOfNewPartitions = 5,
                replicationFactor = 3,
                existingPartitionLoads = assignments1.newAssignments.mapValues { PartitionLoad(0L) },
                clusterBrokersLoad = assignments1.newAssignments.countPerBroker().mapValues { BrokerLoad(it.value, 0L) }
        )
        assertThat(assignmentsDisbalance(assignments2.newAssignments, allBrokers)).isEqualTo(noDisbalance(3))

        val brokersLoad = mapOf(
                0 to BrokerLoad(15, 0L),
                1 to BrokerLoad(16, 0L),
                2 to BrokerLoad(15, 0L),
                3 to BrokerLoad(15, 0L),
                4 to BrokerLoad(12, 0L),
                5 to BrokerLoad(11, 0L)
        )
        println("=============================")
        //increase replication (6, 3) to (6, 4)
        val assignments3 = assignor.assignPartitionsNewReplicas(
                existingAssignments = assignments2.newAssignments,
                allBrokers = allBrokers,
                replicationFactorIncrease = 1,
                existingPartitionLoads = assignments2.newAssignments.mapValues { PartitionLoad(0L) },
                clusterBrokersLoad = brokersLoad
        )
        assertThat(assignmentsDisbalance(assignments3.newAssignments, allBrokers)).isEqualTo(noDisbalance(4))
        assertThat(assignments3.newAssignments).`as`("Number of partitions").hasSize(6)
        assertThat(assignments3.newAssignments.map { it }).`as`("Replication factor")
                .extracting<Int> { it.value.size }
                .containsOnly(4)
    }

    @Test
    fun `reproduce leaders disbalance issue`() {
        /*
             0  1  2  3  4
          0  x  L  x
          1     L     x  x
          2  L  x        x
          3     x  L     x
          4  x     x  L
          5        x  L  x
          ----------------
             0  1  2  3  4
          0  x  L  x
          1     x     x  L
          2  L  x        x
          3     x  L     x
          4  x     x  L
          5        x  L  x
         */
        val existingAssignments = mapOf(
                0 to listOf(1, 0, 2),
                1 to listOf(1, 3, 4),
                2 to listOf(0, 4, 1),
                3 to listOf(2, 4, 1),
                4 to listOf(3, 0, 2),
                5 to listOf(3, 2, 4)
        )
        val expectedAssignments = mapOf(
                0 to listOf(1, 0, 2),
                1 to listOf(4, 3, 1),
                2 to listOf(0, 1, 4),
                3 to listOf(2, 4, 1),
                4 to listOf(3, 0, 2),
                5 to listOf(3, 2, 4)
        )
        val allBrokers = (0..4).toList()
        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, allBrokers)
        assertThat(assignmentsDisbalance(assignments.newAssignments, allBrokers)).isEqualTo(disbalance(
                0, 0, 0.0, 0.0, listOf(0, 0, 2)
        ))
        assertThat(assignments.newAssignments).isEqualTo(expectedAssignments)
    }

    @Test
    fun `find breaking disbalance by adding replicas`() {
        val initSeed = AtomicLong(System.currentTimeMillis())
        val brokersCounts = listOf(2, 3, 6, 16, 32, 64, 128)
        val replicationCounts = listOf(1, 2, 3, 4, 8)
        val replicationIncs = listOf(1, 2, 3)
        val partitionCounts = listOf(1, 2, 3, 4, 8, 16, 32, 64, 256, 1024)
        for (replication in replicationCounts) {
            for (rfInc in replicationIncs) {
                for (brokers in brokersCounts) {
                    if (replication + rfInc > brokers) {
                        continue
                    }
                    for (partitions in partitionCounts) {
                        execAddReplicasExample(
                                seed = initSeed.getAndIncrement(),
                                partitions = partitions,
                                brokers = brokers,
                                replication = replication,
                                rfInc = rfInc
                        )
                    }
                }
            }
        }
    }

    private fun execAddReplicasExample(
            seed: Long,
            partitions: Int,
            brokers: Int,
            replication: Int,
            rfInc: Int
    ) {
        val random = Random(seed)
        val allBrokers = (0 until brokers).toList()

        val initialAssignments = assignor.assignNewPartitionReplicas(
                existingAssignments = emptyMap(),
                numberOfNewPartitions = partitions,
                replicationFactor = replication,
                allBrokers = allBrokers,
                existingPartitionLoads = emptyMap(),
                clusterBrokersLoad = allBrokers.associateWith {
                    BrokerLoad(
                            numReplicas = random.nextInt(2 * brokers),
                            diskBytes = random.nextLong() % 1_000_000_000
                    )
                }
        ).newAssignments
        val initialDisbalance = assignmentsDisbalance(initialAssignments, allBrokers)
        val initialValidation = assignor.validateAssignments(initialAssignments, allBrokers, TopicProperties(partitions, replication))
        assertThat(initialDisbalance.leadersDisbalance).`as`("Seed=$seed Initial leaders disbalance").isEqualTo(0)
        assertThat(initialDisbalance.replicasDisbalance).`as`("Seed=$seed Initial replicas disbalance").isEqualTo(0)
        assertThat(initialValidation.valid).`as`("Seed=$seed Initial validation $initialValidation").isEqualTo(true)

        val newAssignmentsChange = assignor.assignPartitionsNewReplicas(
                existingAssignments = initialAssignments,
                allBrokers = allBrokers,
                replicationFactorIncrease = rfInc,
                existingPartitionLoads = initialAssignments.mapValues { PartitionLoad(0L) },
                clusterBrokersLoad = allBrokers.associateWith {
                    BrokerLoad(
                            numReplicas = random.nextInt(2 * brokers),
                            diskBytes = random.nextLong() % 1_000_000_000
                    )
                }
        )
        val newAssignments = newAssignmentsChange.newAssignments
        val newDisbalance = assignmentsDisbalance(newAssignments, allBrokers)
        val newValidation = assignor.validateAssignments(newAssignments, allBrokers, TopicProperties(partitions, replication + rfInc))
        assertThat(newDisbalance.leadersDisbalance).`as`("Seed=$seed Initial leaders disbalance").isEqualTo(0)
        assertThat(newDisbalance.replicasDisbalance).`as`("Seed=$seed Initial replicas disbalance").isEqualTo(0)
        assertThat(newDisbalance.leadersDeepDisbalance.subList(0, newDisbalance.leadersDeepDisbalance.size / 2))
                .`as`("Seed=$seed Initial replicas disbalance").containsOnly(0)
        assertThat(newValidation.valid).`as`("Seed=$seed Initial validation $newValidation").isEqualTo(true)
        assertThatExistingAssignmentsAreNotMoved(initialAssignments, newAssignmentsChange, seed)
    }

    private fun assertThatExistingAssignmentsAreNotMoved(
            existingAssignments: Map<Partition, List<BrokerId>>,
            assignments: AssignmentsChange,
            seed: Long = 0L
    ) {
        assertAll {
            existingAssignments.forEach { (partition, replicaBrokerIds) ->
                replicaBrokerIds.forEach { brokerId ->
                    assignments.removedPartitionReplicas[partition]
                            ?.let { removedFromBrokerIds ->
                                assertThat(removedFromBrokerIds)
                                        .`as`("Seed=$seed Existing replica of partition $partition not moved from broker $brokerId")
                                        .doesNotContain(brokerId)
                            }
                }
            }
        }
    }

    @Test
    fun `find breaking re-balance`() {
        val seed = System.currentTimeMillis()
        val random = Random(seed)
        val brokersCounts = listOf(1, 2, 3, 6, 16, 32, 64, 128)
        val replicationCounts = listOf(1, 2, 3, 4, 8, 16)
        val partitionCounts = listOf(1, 2, 3, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
        var balanced = 0
        var disbalanced = 0
        val failed = mutableListOf<Pair<Int, String>>()
        val leaderDisbalances = mutableListOf<Double>()
        for (replication in replicationCounts) {
            for (brokers in brokersCounts) {
                if (replication > brokers) {
                    continue
                }
                for (partitions in partitionCounts) {
                    val result = runExample(brokers, partitions, replication, random)
                    val text = "balanced=${result.disbalanced} rep=$replication brokers=$brokers part=$partitions failReasons=${result.failReasons} ${result.disbalance} ${result.validation}"
                    //println(text)
                    if (result.disbalanced) {
                        disbalanced++
                        //println(text)
                        leaderDisbalances.add(result.disbalance.leadersDisbalancePercent)
                    } else {
                        balanced++
                    }
                    if (!result.ok) {
                        failed.add(partitions * replication * brokers to text)
                    }
                }
            }
        }
        println("Balanced = $balanced")
        println("Disbalanced = $disbalanced")
        println("Failed = ${failed.size}")
        val avgLeaderDisbalance = (leaderDisbalances.sum() / leaderDisbalances.size)
                .takeIf { it > 0 }
                ?: 0.0
        println("Avg leader disbalance perc = $avgLeaderDisbalance")

        failed.sortBy { it.first }
        failed.take(10).forEach { println(it) }
        if (failed.isNotEmpty()) {
            fail("There are failures, seed = $seed")
        }
        assertThat(avgLeaderDisbalance).isLessThan(6.0)
    }

    private fun runExample(brokers: Int, partitions: Int, replication: Int, random: Random): ExampleRun {
        val allBrokers = (1..brokers).toList()
        val existingAssignments = (0 until partitions).associateWith {
            allBrokers.shuffled(random).take(replication)
        }
        val existingPartitionLoads = (0 until partitions).associateWith {
            PartitionLoad(
                    diskSize = random.nextLong() % 200_000_000L
            )
        }
        val failReasons = mutableListOf<String>()
        val tmpAssignments1 = assignor.reBalanceReplicasAssignments(existingAssignments, allBrokers, existingPartitionLoads)
        tmpAssignments1.newLeaders.forEach { (partition, broker) ->
            val newAssignmentBecameLeader = tmpAssignments1.addedPartitionReplicas[partition]?.contains(broker) ?: false
            val allPartitionReplicasMoved = tmpAssignments1.removedPartitionReplicas[partition]
                    ?.containsAll(existingAssignments[partition] ?: emptyList())
                    ?: false
            if (newAssignmentBecameLeader && !allPartitionReplicasMoved) {
                failReasons.add("Partition $partition replica on broker $broker became new leader and is newly added replica")
            }
        }
        val tmpAssignments2 = assignor.reBalancePreferredLeaders(tmpAssignments1.newAssignments, allBrokers)
        val assignments = assignor.computeChangeDiff(existingAssignments, tmpAssignments2.newAssignments)
        val disbalance = assignmentsDisbalance(assignments.newAssignments, allBrokers)
        val validation = assignor.validateAssignments(assignments.newAssignments, allBrokers, TopicProperties(partitions, replication))
        val disbalanceOk = with(disbalance) {
            replicasDisbalance == 0 && (leadersDisbalance <= 2 || leadersDisbalancePercent <= 12.5)
        }
        if (!disbalanceOk) {
            failReasons.add("Disbalance not within bounds")
        }
        if (!validation.valid) {
            failReasons.add("Validation did not pass")
        }
        val hasDisbalance = disbalance.replicasDisbalance > 0 || disbalance.leadersDisbalance > 0 || disbalance.leadersDeepDisbalance.dropLast(1).any { it > 0 }
        if ((replication + 2) <= allBrokers.size && !hasDisbalance) {
            val moreReplicas = assignor.assignPartitionsNewReplicas(assignments.newAssignments, allBrokers, 2, emptyMap())
            assertThat(assignmentsDisbalance(moreReplicas.newAssignments, allBrokers).leadersDisbalance).isEqualTo(disbalance.leadersDisbalance)
        }
        return ExampleRun(failReasons.isEmpty(), failReasons, hasDisbalance, validation, disbalance)
    }

    private data class ExampleRun(
            val ok: Boolean,
            val failReasons: List<String>,
            val disbalanced: Boolean,
            val validation: AssignmentsValidation,
            val disbalance: AssignmentsDisbalance
    )

    private fun AssignmentsChange.disbalance(allBrokers: List<BrokerId>): AssignmentsDisbalance {
        return assignmentsDisbalance(newAssignments, allBrokers)
    }

    private fun AssignmentsChange.validate(allBrokers: List<BrokerId>, partitions: Int, replication: Int): AssignmentsValidation {
        return assignor.validateAssignments(newAssignments, allBrokers, TopicProperties(partitions, replication))
    }

    private fun noDisbalance(replication: Int) = disbalance(
            0, 0, 0.0, 0.0, (0 until replication).map { 0 }
    )

    private fun disbalance(
            replicasDisbalance: Int,
            leadersDisbalance: Int,
            replicasDisbalancePercent: Double,
            leadersDisbalancePercent: Double,
            leadersDeepDisbalance: List<Int>
    ) = AssignmentsDisbalance(
            replicasDisbalance, replicasDisbalancePercent, leadersDisbalance, leadersDisbalancePercent, leadersDeepDisbalance
    )

    private fun Map<Partition, List<BrokerId>>.countPerBroker(): Map<BrokerId, Int> {
        return this.flatMap { (partition, brokers) -> brokers.map { it to partition } }
                .groupingBy { it.first }
                .eachCount()
    }

    @Test
    fun `re-assign unwanted preferred leader small`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(3, 1),
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = assignor.reAssignUnwantedPreferredLeaders(existingAssignments, 2)
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1, 2),
                1 to listOf(3, 2),
                2 to listOf(3, 1),
        ))
        assertThat(assignments.addedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.removedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.exLeaders.values).containsOnly(2)
        assertThat(assignments.newLeaders.values).doesNotContain(2)
        assertThat(assignor.leadersDeepDisbalance(existingAssignments, allBrokers)).containsExactly(0, 0)
        assertThat(assignor.leadersDeepDisbalance(assignments.newAssignments, allBrokers)).containsExactly(1, 1)
    }

    @Test
    fun `re-assign unwanted preferred leader big`() {
        val allBrokers = (1..48).toList()
        val existingAssignments = assignor.assignNewPartitionReplicas(
                emptyMap(), allBrokers, 128, 5, emptyMap()
        ).newAssignments
        val assignments = assignor.reAssignUnwantedPreferredLeaders(existingAssignments, 2)
        val preferredLeaders = assignments.newAssignments.mapValues { it.value.first() }
        assertThat(preferredLeaders).doesNotContainValue(2)
        assertThat(assignments.addedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.removedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.exLeaders.values).containsOnly(2)
        assertThat(assignments.newLeaders.values).doesNotContain(2)
        assertThat(assignor.leadersDeepDisbalance(existingAssignments, allBrokers))
                .hasSize(5)
                .containsOnly(0)
        assertThat(assignor.leadersDeepDisbalance(assignments.newAssignments, allBrokers))
                .hasSize(5)
                .element(0)
                .isNotEqualTo(0)
    }

    @Test(expected = KafkistryValidationException::class)
    fun `re-assign unwanted leader for replication factor = 1`() {
        val existingAssignments = mapOf(
                0 to listOf(1),
                1 to listOf(2),
                2 to listOf(3),
        )
        assignor.reAssignUnwantedPreferredLeaders(existingAssignments, 2)
    }

    @Test
    fun `re-balance preferred leader deep small`() {
        val random = Random(1)
        val allBrokers = (1..6).toList()
        val existingAssignments = assignor.assignNewPartitionReplicas(
                emptyMap(), allBrokers, 6, 4, emptyMap()
        ).newAssignments.mapValues { it.value.shuffled(random) }

        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, allBrokers)
        assertThat(assignments.addedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.removedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignor.leadersDeepDisbalance(assignments.newAssignments, allBrokers))
                .containsOnly(0)
    }

    @Test
    fun `re-balance preferred leader deep bigger`() {
        val allBrokers = (1..12).toList()
        val existingAssignments = assignor.assignNewPartitionReplicas(
                emptyMap(), allBrokers, 96, 4, emptyMap()
        ).newAssignments.mapValues { it.value.shuffled() }

        val assignments = assignor.reBalancePreferredLeaders(existingAssignments, allBrokers)
        assertThat(assignments.addedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignments.removedPartitionReplicas).`as`("re-assignment did not generate migrations").isEmpty()
        assertThat(assignor.leadersDeepDisbalance(assignments.newAssignments, allBrokers))
                .hasSize(4)
                .containsOnly(0)
    }

    @Test
    fun `re-assign removal of broker small`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(3, 1),
        )
        val allBrokers = listOf(1, 2, 3)
        val assignments = assignor.reAssignWithoutBrokers(existingAssignments, allBrokers, listOf(2), emptyMap())
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1, 3),
                1 to listOf(3, 1),
                2 to listOf(3, 1),
        ))
    }

    @Test
    fun `re-assign removal of broker bigger`() {
        val allBrokers = (1..12).toList()
        val existingAssignments = assignor.assignNewPartitionReplicas(
                emptyMap(), allBrokers, 24, 3, emptyMap()
        ).newAssignments
        val assignments = assignor.reAssignWithoutBrokers(existingAssignments, allBrokers, listOf(10, 11, 12), emptyMap())
        assignments.newAssignments.forEach { (partition, replicas) ->
            assertThat(replicas).`as`("Assignment of partition=$partition").hasSize(3).doesNotContain(10, 11, 12)
        }
        val disbalance = assignmentsDisbalance(assignments.newAssignments, allBrokers.minus(listOf(10, 11, 12)))
        assertThat(disbalance).isEqualTo(noDisbalance(3))
    }

    @Test
    fun `reduce replication factor`() {
        val existingAssignments = mapOf(
                0 to listOf(1, 2, 3),
                1 to listOf(2, 3, 1),
                2 to listOf(3, 1, 2),
        )
        val assignments = assignor.reduceReplicationFactor(existingAssignments, 2)
        assertThat(assignments.newAssignments).isEqualTo(mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 3),
                2 to listOf(3, 1),
        ))
        assertThat(assignments.addedPartitionReplicas).isEmpty()
    }

    @Test(expected = KafkistryValidationException::class)
    fun `reduce replication factor to bigger`() {
        val existingAssignments = mapOf(
                0 to listOf(1),
                1 to listOf(2),
                2 to listOf(3),
        )
        assignor.reduceReplicationFactor(existingAssignments, 3)
    }

    @Test
    fun `multi swap leaders disbalance`() {
        val allBrokers: List<BrokerId> = (1..24).toList()
        val existingAssignments = mapOf(
            0 to listOf(1, 2, 3),
            1 to listOf(2, 4, 15),
            2 to listOf(17, 15, 16),
            3 to listOf(23, 6, 10),
            4 to listOf(6, 7, 11),
            5 to listOf(7, 8, 6),
            6 to listOf(8, 9, 1),
            7 to listOf(20, 16, 21),
            8 to listOf(10, 11, 3),
            9 to listOf(22, 17, 24),
            10 to listOf(5, 12, 7),
            11 to listOf(12, 14, 19),
            12 to listOf(13, 20, 9),
            13 to listOf(21, 22, 14),
            14 to listOf(16, 21, 23),
            15 to listOf(18, 23, 22),
            16 to listOf(24, 19, 5),
            17 to listOf(18, 1, 20),
            18 to listOf(9, 13, 2),
            19 to listOf(15, 10, 8),
            20 to listOf(11, 3, 4),
            21 to listOf(4, 5, 12),
            22 to listOf(19, 18, 17),
            23 to listOf(14, 24, 13),
        )
        assertThat(assignor.replicasDisbalance(existingAssignments, allBrokers))
            .`as`("replicas disbalance before").isEqualTo(0)
        assertThat(assignor.leadersDisbalance(existingAssignments, allBrokers))
            .`as`("leaders disbalance before").isEqualTo(1)
        val assignmentsChange = assignor.reBalancePreferredLeaders(existingAssignments, allBrokers)
        assertThat(assignor.replicasDisbalance(assignmentsChange.newAssignments, allBrokers))
            .`as`("replicas disbalance after").isEqualTo(0)
        assertThat(assignor.leadersDisbalance(assignmentsChange.newAssignments, allBrokers))
            .`as`("leaders disbalance after").isEqualTo(0)
    }
    
    @Test
    fun `actual leaders disbalance example`() {
        val allBrokers: List<BrokerId> = (1..24).toList()
        val existingAssignments = mapOf(
            0 to listOf(7, 1, 3, 15),
            1 to listOf(2, 4, 6, 8),
            2 to listOf(3, 5, 7, 9),
            3 to listOf(2, 4, 6, 8),
            4 to listOf(5, 7, 9, 11),
            5 to listOf(6, 8, 10, 12),
            6 to listOf(7, 9, 11, 13),
            7 to listOf(8, 10, 12, 14),
            8 to listOf(15, 11, 13, 10),
            9 to listOf(10, 12, 14, 16),
            10 to listOf(11, 13, 15, 17),
            11 to listOf(22, 14, 16, 18),
            12 to listOf(13, 15, 17, 19),
            13 to listOf(14, 16, 18, 20),
            14 to listOf(17, 21, 19, 5),
            15 to listOf(16, 18, 20, 22),
            16 to listOf(17, 19, 21, 23),
            17 to listOf(18, 20, 22, 24),
            18 to listOf(19, 21, 23, 1),
            19 to listOf(20, 22, 24, 2),
            20 to listOf(3, 21, 23, 1),
            21 to listOf(22, 24, 2, 4),
            22 to listOf(23, 1, 3, 5),
            23 to listOf(10, 2, 4, 6),
            24 to listOf(1, 3, 5, 7),
            25 to listOf(4, 6, 8, 24),
            26 to listOf(3, 5, 7, 9),
            27 to listOf(4, 6, 8, 10),
            28 to listOf(5, 7, 9, 11),
            29 to listOf(6, 8, 10, 12),
            30 to listOf(19, 9, 11, 13),
            31 to listOf(8, 10, 12, 14),
            32 to listOf(9, 11, 13, 15),
            33 to listOf(9, 12, 14, 16),
            34 to listOf(11, 13, 15, 17),
            35 to listOf(12, 14, 16, 18),
            36 to listOf(13, 15, 17, 19),
            37 to listOf(14, 16, 18, 20),
            38 to listOf(15, 17, 19, 21),
            39 to listOf(16, 18, 20, 22),
            40 to listOf(17, 19, 21, 23),
            41 to listOf(18, 20, 22, 24),
            42 to listOf(21, 23, 1, 7),
            43 to listOf(20, 22, 24, 2),
            44 to listOf(21, 23, 1, 3),
            45 to listOf(12, 24, 2, 4),
            46 to listOf(23, 1, 3, 5),
            47 to listOf(24, 2, 4, 6),
            48 to listOf(1, 3, 5, 7),
            49 to listOf(2, 4, 6, 8),
        )
        assertThat(assignor.replicasDisbalance(existingAssignments, allBrokers))
            .`as`("replicas disbalance before").isEqualTo(0)
        assertThat(assignor.leadersDisbalance(existingAssignments, allBrokers))
            .`as`("leaders disbalance before").isEqualTo(1)
        val assignmentsChange = assignor.reBalancePreferredLeaders(existingAssignments, allBrokers)
        assertThat(assignor.replicasDisbalance(assignmentsChange.newAssignments, allBrokers))
            .`as`("replicas disbalance after").isEqualTo(0)
        assertThat(assignor.leadersDisbalance(assignmentsChange.newAssignments, allBrokers))
            .`as`("leaders disbalance after").isEqualTo(0)
    }

}
