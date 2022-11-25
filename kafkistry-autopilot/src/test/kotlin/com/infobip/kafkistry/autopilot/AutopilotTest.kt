package com.infobip.kafkistry.autopilot

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.autopilot.config.AutopilotRootProperties
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilter
import com.infobip.kafkistry.autopilot.fencing.ActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.ClusterStableFencing
import com.infobip.kafkistry.autopilot.fencing.LocalActionAcquireFencing
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome.OutcomeType.*
import com.infobip.kafkistry.autopilot.reporting.AutopilotReporter
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.autopilot.repository.toActonFlow
import com.infobip.kafkistry.hostname.HostnameProperties
import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.nhaarman.mockitokotlin2.*
import io.kotlintest.mock.mock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class AutopilotTest {

    companion object {
        private val mockBinding = mock<AutopilotBinding<AutopilotAction>>()
        private val mockFilter = mock<AutopilotEnabledFilter>()
        private val mockFencing = AtomicReference<ActionAcquireFencing>()
        private val mockReporting = mock<AutopilotReporter>()
        private val mockRepository = mock<ActionsRepository>()
        private val mockStableFencing = mock<ClusterStableFencing>()

        private val autopilot = Autopilot(
            properties = AutopilotRootProperties().apply {
                pendingDelayMs = 5_000L
                attemptDelayMs = 15_000L
            },
            bindings = listOf(mockBinding),
            enabledFilter = mockFilter,
            checkingCache = CheckingCache(),
            autopilotUser = AutopilotUser(
                hostnameResolver = HostnameResolver(HostnameProperties()),
                userResolver = CurrentRequestUserResolver(),
            ),
            backgroundIssues = BackgroundJobIssuesRegistry(),
            clusterStableFencing = mockStableFencing,
            fencing = object : ActionAcquireFencing {
                override fun acquireActionExecution(action: AutopilotAction): Boolean {
                    return mockFencing.get().acquireActionExecution(action)
                }
            },
            reporter = mockReporting,
            repository = mockRepository,
        )
    }

    @BeforeEach
    fun resetMocks() {
        reset(mockBinding, mockFilter, mockReporting, mockRepository, mockStableFencing)
        mockFencing.set(LocalActionAcquireFencing(1_000))
        whenever(mockRepository.findAll()).thenReturn(emptyList())
        whenever(mockStableFencing.recentUnstableStates(any())).thenReturn(emptyList())
    }

    @AfterEach
    fun checkMocks() {
        verify(mockBinding, times(1)).actionsToProcess()
        verify(mockFilter, atMost(1)).isEnabled(any(), any())
        verify(mockBinding, atMost(1)).checkBlockers(any())
        verify(mockStableFencing, times(1)).refresh()
        verifyNoMoreInteractions(mockReporting, mockBinding, mockStableFencing)
    }

    private fun testAction(cluster: KafkaClusterIdentifier? = null): AutopilotAction {
        val testMethod = Exception().stackTrace[1].methodName
        val action = TestAction(testMethod)
        return if (cluster != null) {
            action.copy(metadata = action.metadata.copy(clusterRef = ClusterRef(cluster)))
        } else {
            action
        }
    }

    private fun AutopilotAction.mockActionFlow(
        outcomeType: ActionOutcome.OutcomeType,
        timestampBefore: Long = 0,
    ): ActionFlow {
        val outcome = ActionOutcome(
            actionMetadata = metadata,
            outcome = ActionOutcome.Outcome(
                type = outcomeType,
                sourceAutopilot = "test-autopilot",
                timestamp = System.currentTimeMillis() - timestampBefore,
            )
        )
        return outcome.toActonFlow()
    }

    @Test
    fun `don't perform disabled action`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(false)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == DISABLED })
    }

    @Test
    fun `don't perform blocked action`() {
        val action = testAction()
        val mockBlockers = listOf(AutopilotActionBlocker("test block"))
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(mockBlockers)
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == BLOCKED && outcome.blockers == mockBlockers })
    }

    @Test
    fun `don't perform non-acquired action`() {
        val action = testAction()
        mockFencing.get().acquireActionExecution(action)
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockRepository.find(action.actionIdentifier)).thenReturn(
            action.mockActionFlow(PENDING, timestampBefore = 30_000L)
        )
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == NOT_ACQUIRED })
    }

    @Test
    fun `successfully schedule pending action`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        autopilot.cycle()
        verify(mockReporting).reportOutcome(argThat { outcome.type == PENDING })
        verify(mockBinding, never()).processAction(action)
    }

    @Test
    fun `don't execute non-expired pending action`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        whenever(mockRepository.find(action.actionIdentifier)).thenReturn(
            action.mockActionFlow(PENDING, timestampBefore = 2_000L)
        )
        autopilot.cycle()
        verify(mockReporting).reportOutcome(argThat { outcome.type == PENDING })
        verify(mockBinding, never()).processAction(action)
    }

    @Test
    fun `successfully perform action`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        whenever(mockRepository.find(action.actionIdentifier)).thenReturn(
            action.mockActionFlow(PENDING, timestampBefore = 30_000L)
        )
        autopilot.cycle()
        verify(mockBinding, times(1)).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == SUCCESSFUL })
    }

    @Test
    fun `failure during perform action`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        whenever(mockRepository.find(action.actionIdentifier)).thenReturn(
            action.mockActionFlow(PENDING, timestampBefore = 30_000L)
        )
        whenever(mockBinding.processAction(action)).thenThrow(RuntimeException("mock-failure"))
        autopilot.cycle()
        verify(mockBinding, times(1)).processAction(action)
        verify(mockReporting).reportOutcome(argThat {
            outcome.type == FAILED && outcome.executionError.toString().contains("mock-failure")
        })
    }

    @Test
    fun `resolved action without active execution`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(emptyList())
        whenever(mockRepository.findAll()).thenReturn(listOf(
            ActionFlow(action.actionIdentifier, action.metadata, 1, DISABLED, emptyList())
        ))
        autopilot.cycle()
        verify(mockBinding, never()).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == RESOLVED })
    }

    @Test
    fun `don't mark resolved action that's successfully completed`() {
        val action = testAction()
        whenever(mockBinding.actionsToProcess()).thenReturn(emptyList())
        whenever(mockRepository.findAll()).thenReturn(listOf(
            ActionFlow(action.actionIdentifier, action.metadata, 1, SUCCESSFUL, emptyList())
        ))
        autopilot.cycle()
        verify(mockBinding, never()).processAction(any())
        verify(mockReporting, never()).reportOutcome(any())
    }

    @Test
    fun `reject recent unstable cluster action`() {
        val action = testAction("unstable-cluster")
        val unstableReasons = listOf(ClusterUnstable(StateType.UNREACHABLE, "mock", 123L))
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        whenever(mockStableFencing.recentUnstableStates("unstable-cluster")).thenReturn(unstableReasons)
        autopilot.cycle()
        verify(mockStableFencing).recentUnstableStates("unstable-cluster")
        verify(mockReporting).reportOutcome(argThat {
            outcome.type == CLUSTER_UNSTABLE && outcome.unstable == unstableReasons
        })
    }

    @Test
    fun `accept recent stable cluster action`() {
        val action = testAction("stable-cluster")
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockBinding.checkBlockers(action)).thenReturn(emptyList())
        whenever(mockStableFencing.recentUnstableStates("stable-cluster")).thenReturn(emptyList())
        whenever(mockRepository.find(action.actionIdentifier)).thenReturn(
            action.mockActionFlow(PENDING, timestampBefore = 30_000L)
        )
        autopilot.cycle()
        verify(mockStableFencing).recentUnstableStates("stable-cluster")
        verify(mockBinding, times(1)).processAction(action)
        verify(mockReporting).reportOutcome(argThat { outcome.type == SUCCESSFUL })
    }

    @Test
    fun `don't auto resolve unstable cluster action`() {
        val action = testAction("unstable-cluster")
        val unstableReasons = listOf(ClusterUnstable(StateType.UNREACHABLE, "mock", 123L))
        whenever(mockStableFencing.recentUnstableStates("unstable-cluster")).thenReturn(unstableReasons)
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(action))
        whenever(mockFilter.isEnabled(mockBinding, action)).thenReturn(true)
        whenever(mockRepository.findAll()).thenReturn(listOf(
            ActionFlow(action.actionIdentifier, action.metadata, 1, DISABLED, emptyList())
        ))
        autopilot.cycle()
        verify(mockReporting).reportOutcome(argThat {
            outcome.type == CLUSTER_UNSTABLE && outcome.unstable == unstableReasons
        })
        verify(mockStableFencing).recentUnstableStates("unstable-cluster")
    }

    @Test
    fun `don't auto resolve unstable cluster and non-reported action`() {
        val action = testAction("down-cluster")
        whenever(mockBinding.actionsToProcess()).thenReturn(emptyList())
        whenever(mockRepository.findAll()).thenReturn(listOf(
            ActionFlow(action.actionIdentifier, action.metadata, 1, DISABLED, emptyList())
        ))
        whenever(mockStableFencing.recentUnstableStates("down-cluster")).thenReturn(
            listOf(ClusterUnstable(StateType.UNREACHABLE, "mock", 123L))
        )
        autopilot.cycle()
        verify(mockBinding, never()).processAction(action)
        verify(mockStableFencing).recentUnstableStates("down-cluster")
    }


}