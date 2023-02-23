package com.infobip.kafkistry.service.generator.balance

import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.generator.balance.BalancePriority.*
import org.junit.Ignore
import org.junit.Test
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlin.test.fail

class BalancerTest {

    private val balancerService = GlobalBalancerService(
        clustersStateProvider = mock(),
        reAssignmentsProvider = mock(),
        consumerGroupsProvider = mock(),
        replicaDirsService = mock(),
        topicOffsetsService = mock()
    )

    private val assignor = PartitionsReplicasAssignor()

    @Test
    fun `test balancing state1 by disk size`() {
        val proposedMigrations = performBalancing(state1(), BalanceObjective.of(SIZE))
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds })
            .containsExactlyInAnyOrder(
                tuple("t1", 4, listOf(1), listOf(0)),
                tuple("t1", 3, listOf(0), listOf(1)),
            )
    }

    @Test
    fun `test balancing state1 by ALL`() {
        val proposedMigrations = performBalancing(state1(), BalanceObjective.EQUAL)
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds }
            )
            .containsExactlyInAnyOrder(
                tuple("t1", 4, listOf(1), listOf(0)),
                tuple("t1", 3, listOf(0), listOf(1)),
            )
    }

    @Test
    fun `test balancing state1 by ALL multi iteration no more than one (1st iteration leads to optimal)`() {
        val proposedMigrations = performBalancing(state1(), BalanceObjective.EQUAL, iterations = 1000)
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds }
            )
            .containsExactlyInAnyOrder(
                tuple("t1", 4, listOf(1), listOf(0)),
                tuple("t1", 3, listOf(0), listOf(1)),
            )
    }

    @Test
    fun `test balancing state2 by ALL`() {
        val proposedMigrations = performBalancing(state2(), BalanceObjective.EQUAL)
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds }
            )
            .containsExactlyInAnyOrder(
                tuple("t2", 1, listOf(1), listOf(0)),
                tuple("t2", 0, listOf(0), listOf(1)),
            )
    }

    @Test
    fun `test balancing state2 by ALL multiple iterations`() {
        val proposedMigrations = performBalancing(state2(), BalanceObjective.EQUAL, iterations = 2)
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds }
            )
            .contains(
                tuple("t2", 1, listOf(1), listOf(0)),
                tuple("t2", 0, listOf(0), listOf(2)),
                tuple("t2", 2, listOf(2), listOf(1)),
            )
    }

    @Test
    fun `test balancing state3 by disk ALL`() {
        val proposedMigrations = performBalancing(state3(), BalanceObjective.EQUAL)
        assertThat(proposedMigrations.migrations)
            .extracting(
                { it.topicPartition.topic },
                { it.topicPartition.partition },
                { it.fromBrokerIds },
                { it.toBrokerIds })
            .containsExactlyInAnyOrder(
                tuple("t2", 0, listOf(0), listOf(1)),
            )
    }

    @Test
    fun `test balancing random by ALL one iteration, respect time limit`() {
        val randomSeed = System.currentTimeMillis()
        val duration = measureTimeMillis {
            val proposedMigrations = performBalancing(
                stateRandom(randomSeed),
                BalanceObjective.EQUAL,
                iterations = 1,
                iterationTimeoutMs = 3_000,
                seed = randomSeed,
            )
            proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL, randomSeed)
        }
        assertThat(duration).`as`("execution duration; seed = [$randomSeed]").isLessThan(4_000)
    }

    @Test
    fun `test balancing random by ALL 2 iterations, respect time limit`() {
        val randomSeed = System.currentTimeMillis()
        val state = stateRandom(randomSeed)
        val duration = measureTimeMillis {
            val proposedMigrations = performBalancing(
                state, BalanceObjective.EQUAL,
                iterations = 2,
                iterationTimeoutMs = 2_000,
                seed = randomSeed,
            )
            proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL, randomSeed)
        }
        assertThat(duration).`as`("execution duration; seed = [$randomSeed]").isLessThan(5_000)
    }

    @Test
    fun `test balancing random by different objectives`() {
        val randomSeed = System.currentTimeMillis()
        val state = stateRandom(randomSeed)
        BalancePriority.values().forEach { balancePriority ->
            val objective = BalanceObjective.of(balancePriority)
            val duration = measureTimeMillis {
                val proposedMigrations = performBalancing(
                    state, objective, iterations = 1, iterationTimeoutMs = 1_000, seed = randomSeed
                )
                proposedMigrations.assertScoreBetterThanBeforeOrOptimal(objective, randomSeed)
            }
            assertThat(duration).`as`("execution duration; seed = [$randomSeed]").isLessThan(1_500)
        }
    }

    @Test
    fun `test state4 by disk`() {
        val proposedMigrations = performBalancing(state4(), BalanceObjective.of(SIZE))
        assertThat(proposedMigrations.migrations).isNotEmpty
        proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.of(SIZE))
        proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL)
    }

    @Test
    fun `test state4 by ALL`() {
        val proposedMigrations = performBalancing(state4(), BalanceObjective.EQUAL)
        assertThat(proposedMigrations.migrations).isNotEmpty
        proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL)
    }

    @Test
    fun `test state5 no migration, just leaders change`() {
        val proposedMigrations = performBalancing(state5(), BalanceObjective.EQUAL)
        assertThat(proposedMigrations.clusterBalanceBefore.brokersLoadDiff)
            .extracting({ it.replicas }, { it.leaders })
            .isEqualTo(listOf(0.0, 2.0))
        assertThat(proposedMigrations.clusterBalanceAfter.brokersLoadDiff)
            .extracting({ it.replicas }, { it.leaders })
            .isEqualTo(listOf(0.0, 0.0))
        assertThat(proposedMigrations.migrations).isNotEmpty
        assertThat(proposedMigrations.dataMigration.totalIOBytes).isZero
        proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL)
    }

    @Test
    fun `test balance every topic balanced but uneven impact per broker`() {
        val objective = BalanceObjective.of(REPLICAS_COUNT)
        val proposedMigrations = performBalancing(stateEachTopicUnevenImpactPerBroker(), objective, iterations = 2)
        with(proposedMigrations) {
            println("diff before: " + clusterBalanceBefore.combinedLoadDiff)
            println("diff after: " + clusterBalanceAfter.combinedLoadDiff)
            println("diff improve: " + (clusterBalanceAfter.combinedLoadDiff.mapValues { it.value - clusterBalanceBefore.combinedLoadDiff.getValue(it.key) }))
        }
        proposedMigrations.assertScoreBetterThanBeforeOrOptimal(objective)
        assertThat(proposedMigrations.migrations).isNotEmpty
    }

    @Test
    @Ignore("slow execution - used for profiling")
    fun `test balancing performance`() {
        fun execute(seed: Long): Long {
            val state = stateRandom(randomSeed = seed)
            return measureTimeMillis {
                val proposedMigrations = performBalancing(
                    state,
                    BalanceObjective.EQUAL,
                    iterations = 1,
                    seed = seed,
                )
                proposedMigrations.assertScoreBetterThanBeforeOrOptimal(BalanceObjective.EQUAL, seed)
            }
        }
        println("Warmup...")
        val warmup = execute(System.currentTimeMillis())
        println("Warmup in ${warmup / 1000.0} sec")

        val durations = (1L..10L).map { seed ->
            println("Executing seed=$seed")
            execute(seed).also {
                println("Seed=$seed in ${it / 1000.0} sec")
            }
        }

        println("Min: " + durations.minOrNull()?.div(1000.0))
        println("Max: " + durations.maxOrNull()?.div(1000.0))
        println("Dev: " + durations.run { maxOrNull()?.minus(minOrNull() ?: 0) }?.div(1000.0))
        println("Avg: " + durations.average().div(1000.0))
    }

    private fun performBalancing(
        state: GlobalState,
        objective: BalanceObjective = BalanceObjective.EQUAL,
        iterations: Int = 1,
        iterationTimeoutMs: Long = Long.MAX_VALUE,
        maxMigrationBytes: Long = Long.MAX_VALUE,
        seed: Long? = null,
    ): ProposedMigrations {
        val totalTimeLimitMs = (iterationTimeoutMs * iterations).takeIf { it > 0 } ?: Long.MAX_VALUE
        val proposeMigrations = balancerService.proposeMigrations(
            state,
            BalanceSettings(
                objective, iterations, maxMigrationBytes, iterationTimeoutMs, totalTimeLimitMs,
                includeTopicNamePattern = null, excludeTopicNamePattern = null,
            )
        )
        with(proposeMigrations) {
            assertValidAssignments(state, seed)
            assertScoreBetterOrEqual(objective, seed)
        }
        return proposeMigrations
    }

    private fun ProposedMigrations.assertValidAssignments(state: GlobalState, seed: Long?) {
        topicsAssignmentChanges.forEach { (topic, assignmentsChange) ->
            val topicProperties = state.assignments[topic]
                ?.let { TopicProperties(it.partitionAssignments.size, it.partitionAssignments.values.first().size) }
                ?: fail("Missing topic $topic [seed = $seed]")
            val validation = assignor.validateAssignments(
                assignmentsChange.newAssignments, state.brokerIds, topicProperties
            )
            if (!validation.valid) {
                fail("Invalid assignment for topic $topic: $validation [seed = $seed]")
            }
        }
    }

    private fun ClusterBalanceStatus.score(objective: BalanceObjective): Double =
        brokersLoadDiff.normalize(brokersAverageLoad, objective)

    private fun ProposedMigrations.assertScoreBetterOrEqual(objective: BalanceObjective, seed: Long?) {
        val after = clusterBalanceAfter.score(objective)
        val before = clusterBalanceBefore.score(objective)
        assertThat(after)
            .`as`("Score by $objective after should be better or equal than before; [seed = $seed]")
            .isLessThanOrEqualTo(before)
    }

    private fun ProposedMigrations.assertScoreBetterThanBeforeOrOptimal(objective: BalanceObjective, seed: Long? = null) {
        if (objective.priorities.size == 1) {
            if (objective.priorities[0] == REPLICAS_COUNT && clusterBalanceAfter.brokersLoadDiff.replicas.closeTo(1.0)) {
                return  //can't do better if total number of all replicas is not multiple of broker count
            }
            if (objective.priorities[0] == LEADERS_COUNT && clusterBalanceAfter.brokersLoadDiff.leaders.closeTo(1.0)) {
                return  //can't do better if total number of all topic partitions is not multiple of broker count
            }
        }
        val after = clusterBalanceAfter.score(objective)
        val before = clusterBalanceBefore.score(objective)
        if (after == 0.0) {
            return  //it's already perfect
        }
        assertThat(after)
            .`as`("Score by $objective after should be strictly better than before [seed=$seed]")
            .isLessThan(before)
    }

    private fun Double.closeTo(other: Double, maxDiff: Double = 0.0001): Boolean {
        return abs(this - other) < maxDiff
    }
}
