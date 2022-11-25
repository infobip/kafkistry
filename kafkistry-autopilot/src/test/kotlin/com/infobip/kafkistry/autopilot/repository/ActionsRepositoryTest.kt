package com.infobip.kafkistry.autopilot.repository

import com.infobip.kafkistry.autopilot.binding.ActionDescription
import com.infobip.kafkistry.autopilot.binding.ActionMetadata
import com.infobip.kafkistry.autopilot.binding.AutopilotActionBlocker
import com.infobip.kafkistry.autopilot.config.ActionsRepositoryProperties
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome.OutcomeType.*
import com.infobip.kafkistry.events.DefaultEventPublisher
import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.events.KafkistryEvent
import com.infobip.kafkistry.utils.test.newTestFolder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import kotlin.reflect.KClass

internal class ActionsRepositoryTest {

    private fun newRepo(
        dir: String? = null,
        repeatWindow: Long = 60_000,
        properties: ActionsRepositoryProperties.LimitsProperties.() -> Unit = {},
    ): ActionsRepository {
        val limitsProperties = ActionsRepositoryProperties.LimitsProperties().apply(properties)
        val inMemory = InMemoryActionsRepository(repeatWindow, limitsProperties)
        if (dir == null) {
            return inMemory
        }
        return DiskPersistedActionsRepository(inMemory, dir)
    }

    private val timeNow = System.currentTimeMillis()
    private val timeInc = AtomicLong(0)
    private fun nextTime() = timeNow + timeInc.getAndIncrement()

    private fun newOutcome(
        action: String,
        outcome: ActionOutcome.OutcomeType,
        blockers: Int = 0,
        error: String? = null,
        time: Long? = null,
    ): ActionOutcome {
        return ActionOutcome(
            actionMetadata = ActionMetadata(
                actionIdentifier = action,
                description = ActionDescription(
                    actionName = action,
                    actionClass = "acme.$action",
                    targetType = "TEST",
                    doc = "representation of mock action for tests"
                ),
            ),
            outcome = ActionOutcome.Outcome(
                type = outcome,
                sourceAutopilot = "test",
                timestamp = time ?: nextTime(),
                blockers = (0 until blockers).map { AutopilotActionBlocker("test blocker $it") },
                executionError = error,
            )
        )
    }

    @Nested
    inner class InMemoryRepositoryTest {

        @Test
        fun `nothing in empty`() {
            val repo = newRepo()
            assertThat(repo.findAll()).isEmpty()
        }

        @Test
        fun `single outcome`() {
            val repo = newRepo()
            val actionOutcome = newOutcome("single", SUCCESSFUL)
            repo.save(actionOutcome)
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(1)
                .first()
                .isEqualTo(
                    ActionFlow(
                        actionIdentifier = actionOutcome.actionMetadata.actionIdentifier,
                        metadata = actionOutcome.actionMetadata,
                        lastTimestamp = actionOutcome.outcome.timestamp,
                        outcomeType = actionOutcome.outcome.type,
                        flow = listOf(actionOutcome.outcome),
                    )
                )
        }

        @Test
        fun `different actions outcome`() {
            val repo = newRepo()
            repo.save(newOutcome("a3", DISABLED))
            repo.save(newOutcome("a1", NOT_ACQUIRED))
            repo.save(newOutcome("a2", BLOCKED, blockers = 2))
            repo.save(newOutcome("a1", SUCCESSFUL))
            repo.save(newOutcome("a3", DISABLED))
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(3)
                .extracting(
                    { it.actionIdentifier }, { it.outcomeType }, { it.flow.size }
                )
                .containsExactlyInAnyOrder(
                    Tuple.tuple("a3", DISABLED, 2),
                    Tuple.tuple("a1", SUCCESSFUL, 2),
                    Tuple.tuple("a2", BLOCKED, 1),
                )
        }

        @Test
        fun `triple outcomes merge`() {
            val repo = newRepo()
            val actionOutcome1 = newOutcome("merge", DISABLED)
            repo.save(actionOutcome1)
            val actionOutcome2 = newOutcome("merge", SUCCESSFUL)
            repo.save(actionOutcome2)
            val actionOutcome3 = newOutcome("merge", NOT_ACQUIRED)
            repo.save(actionOutcome3)
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(1)
                .first()
                .isEqualTo(
                    ActionFlow(
                        actionIdentifier = "merge",
                        metadata = actionOutcome1.actionMetadata,
                        lastTimestamp = actionOutcome2.outcome.timestamp,
                        outcomeType = SUCCESSFUL,
                        flow = listOf(actionOutcome1.outcome, actionOutcome2.outcome, actionOutcome3.outcome),
                    )
                )
        }

        @Test
        fun `repeated failure outcomes trim`() {
            val repo = newRepo {
                maxPerAction = 6
            }
            repeat(50) {
                repo.save(newOutcome("failing", FAILED, time = 2L * it))
                repo.save(newOutcome("failing", NOT_ACQUIRED, time = 2L * it + 1))
            }
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(1)
                .first()
                .isEqualTo(
                    ActionFlow(
                        actionIdentifier = "failing",
                        metadata = newOutcome("failing", FAILED).actionMetadata,
                        lastTimestamp = 98L,
                        outcomeType = FAILED,
                        flow = listOf(
                            newOutcome("failing", FAILED, time = 94).outcome,
                            newOutcome("failing", NOT_ACQUIRED, time = 95).outcome,
                            newOutcome("failing", FAILED, time = 96).outcome,
                            newOutcome("failing", NOT_ACQUIRED, time = 97).outcome,
                            newOutcome("failing", FAILED, time = 98).outcome,
                            newOutcome("failing", NOT_ACQUIRED, time = 99).outcome,
                        ),
                    )
                )
        }

        @Test
        fun `test trim by count`() {
            val repo = newRepo { maxCount = 10 }
            val now = System.currentTimeMillis()
            repeat(100) {
                repo.save(newOutcome("a-$it", SUCCESSFUL, time = now + it))
            }
            repo.cleanup()
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(10)
                .extracting(Function { it.actionIdentifier })
                .isEqualTo((99 downTo 90).map { "a-$it" })
        }

        @Test
        fun `test trim by retention`() {
            val repo = newRepo { retentionMs = 1000 }
            val now = System.currentTimeMillis()
            repeat(100) {
                repo.save(newOutcome("a-$it", SUCCESSFUL, time = if (it < 50) now - 1000 else now))
            }
            repo.cleanup()
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(50)
                .extracting(Function { it.actionIdentifier })
                .containsExactlyInAnyOrderElementsOf((99 downTo 50).map { "a-$it" })
        }

        @Test
        fun `test duplicate outcomes`() {
            val repo = newRepo()
            val actionOutcome = newOutcome("duplicate", SUCCESSFUL, time = 1)
            repo.save(actionOutcome)
            repo.save(actionOutcome)
            repo.save(actionOutcome)
            val actionFlows = repo.findAll()
            assertThat(actionFlows)
                .hasSize(1)
                .first()
                .isEqualTo(
                    ActionFlow(
                        actionIdentifier = "duplicate",
                        metadata = actionOutcome.actionMetadata,
                        lastTimestamp = actionOutcome.outcome.timestamp,
                        outcomeType = actionOutcome.outcome.type,
                        flow = listOf(actionOutcome.outcome),
                    )
                )
        }

        @Test
        fun `test same action needs repetition`() {
            val repo = newRepo()

            //first time
            repo.save(newOutcome("repeat", PENDING, time = 1000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(PENDING)
            repo.save(newOutcome("repeat", PENDING, time = 1001))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(PENDING)
            repo.save(newOutcome("repeat", NOT_ACQUIRED, time = 2000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(NOT_ACQUIRED)
            repo.save(newOutcome("repeat", SUCCESSFUL, time = 2100))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(SUCCESSFUL)
            repo.save(newOutcome("repeat", NOT_ACQUIRED, time = 2010))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(SUCCESSFUL)
            repo.save(newOutcome("repeat", RESOLVED, time = 2110))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(SUCCESSFUL)

            //after some time
            repo.save(newOutcome("repeat", BLOCKED, time = 100_000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(BLOCKED)
            repo.save(newOutcome("repeat", BLOCKED, time = 100_100))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(BLOCKED)
            repo.save(newOutcome("repeat", PENDING, time = 105_000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(PENDING)
            repo.save(newOutcome("repeat", PENDING, time = 105_100))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(PENDING)
            repo.save(newOutcome("repeat", SUCCESSFUL, time = 110_000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(SUCCESSFUL)
            repo.save(newOutcome("repeat", RESOLVED, time = 111_000))
            assertThat(repo.find("repeat")?.outcomeType).isEqualTo(SUCCESSFUL)
        }

    }

    @Nested
    inner class DiskPersistedRepositoryTest {

        @Test
        fun `nothing in empty dir`() {
            val repo = newRepo(newTestFolder("autopilot"))
            assertThat(repo.findAll()).isEmpty()
        }

        @Test
        fun `persist and load`() {
            val testDir = newTestFolder("autopilot")
            val setupRepo = newRepo(testDir)
            setupRepo.save(newOutcome("action-1", SUCCESSFUL))
            setupRepo.save(newOutcome("action-1", FAILED))
            setupRepo.save(newOutcome("action-2", SUCCESSFUL))
            setupRepo.save(newOutcome("action-3", BLOCKED))
            setupRepo.save(newOutcome("action-3", FAILED))
            setupRepo.save(newOutcome("action-3", NOT_ACQUIRED))
            val setupFlows = setupRepo.findAll()
            val repo = newRepo(testDir)
            val actionFlows = repo.findAll()
            assertThat(actionFlows).isEqualTo(setupFlows)
        }

    }

    @Nested
    inner class EventSyncedRepositoryTest {

        private val listeners = mutableListOf<EventListener<out KafkistryEvent>>()
        private val publisher = DefaultEventPublisher(listeners)

        inner class TestActionsSyncListener : EventListener<ActionsRepositorySyncEvent> {
            override val log: Logger = LoggerFactory.getLogger(TestActionsSyncListener::class.java)
            override val eventType: KClass<ActionsRepositorySyncEvent> = ActionsRepositorySyncEvent::class
            var delegate: EventListener<ActionsRepositorySyncEvent>? = null
            override fun handleEvent(event: ActionsRepositorySyncEvent) = delegate?.handleEvent(event) ?: Unit
        }

        private fun eventSyncRepo(dir: String? = null): ActionsRepository {
            val listener = TestActionsSyncListener()
            listeners.add(listener)
            val repo = EventInitializingActionsRepositoryImpl(newRepo(dir), publisher)
            return repo.also {
                listener.delegate = ActionsRepositorySyncEventListener(repo)
                repo.sendInitRequest()
            }
        }

        private fun ActionOutcome.saveInto(vararg repos: ActionsRepository) {
            repos.forEach { it.save(this) }
        }

        @Test
        fun `single repo`() {
            val repo = eventSyncRepo()
            assertThat(repo.findAll()).isEmpty()
            repo.save(newOutcome("single-repo", SUCCESSFUL))
            assertThat(repo.findAll()).hasSize(1)
        }

        @Test
        fun `two repos in sync from start`() {
            val repo = eventSyncRepo()
            assertThat(repo.findAll()).isEmpty()
            repo.save(newOutcome("two-repos-first", SUCCESSFUL))
            assertThat(repo.findAll()).hasSize(1)
            val repo2 = eventSyncRepo()
            assertThat(repo2.findAll()).hasSize(1)
            repo.save(newOutcome("two-repos-second", SUCCESSFUL))
            assertThat(repo.findAll()).hasSize(2)
            assertThat(repo2.findAll()).hasSize(1)
        }

        @Test
        fun `dir sync where it left of`() {
            val testDir = newTestFolder("autopilot-sync-dir")
            val repo1 = eventSyncRepo()
            val repo2 = eventSyncRepo(testDir)
            newOutcome("continue-1", SUCCESSFUL, time = 1).saveInto(repo1, repo2)
            newOutcome("continue-2", SUCCESSFUL, time = 2).saveInto(repo1, repo2)
            newOutcome("continue-3", SUCCESSFUL, time = 3).saveInto(repo1, repo2)
            assertThat(repo1.findAll()).hasSize(3)
            assertThat(repo2.findAll()).hasSize(3)
            newOutcome("continue-4", SUCCESSFUL, time = 4).saveInto(repo1)
            assertThat(repo1.findAll()).hasSize(4)
            assertThat(repo2.findAll()).hasSize(3)
            val repo2Synced = eventSyncRepo(testDir)
            assertThat(repo2Synced.findAll()).hasSize(4)
            val repo3Synced = eventSyncRepo()
            assertThat(repo3Synced.findAll()).hasSize(4)
        }
    }

}