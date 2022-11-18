package com.infobip.kafkistry.autopilot

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilter
import com.infobip.kafkistry.autopilot.fencing.ActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.LocalActionAcquireFencing
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome.OutcomeType.*
import com.infobip.kafkistry.autopilot.reporting.AutopilotReporter
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.hostname.HostnameProperties
import com.infobip.kafkistry.hostname.HostnameResolver
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

        private val autopilot = Autopilot(
            bindings = listOf(mockBinding),
            enabledFilter = mockFilter,
            checkingCache = CheckingCache(),
            autopilotUser = AutopilotUser(
                hostnameResolver = HostnameResolver(HostnameProperties()),
                userResolver = CurrentRequestUserResolver(),
            ),
            backgroundIssues = BackgroundJobIssuesRegistry(),
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
        reset(mockBinding, mockFilter, mockReporting, mockRepository)
        mockFencing.set(LocalActionAcquireFencing(1_000))
        whenever(mockRepository.findAll()).thenReturn(emptyList())
    }

    @AfterEach
    fun checkMocks() {
        verify(mockBinding, times(1)).actionsToProcess()
        verify(mockFilter, atMost(1)).isEnabled(any(), any())
        verify(mockBinding, atMost(1)).checkBlockers(any())
        verifyNoMoreInteractions(mockReporting, mockBinding)
    }

    private fun testAction(): AutopilotAction {
        val testMethod = Exception().stackTrace[1].methodName
        return TestAction(testMethod)
    }

    @Test
    fun `don't perform disabled action`() {
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(testAction()))
        whenever(mockFilter.isEnabled(mockBinding, testAction())).thenReturn(false)
        whenever(mockBinding.checkBlockers(testAction())).thenReturn(emptyList())
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(testAction())
        verify(mockReporting).reportOutcome(argThat { outcome.type == DISABLED })
    }

    @Test
    fun `don't perform blocked action`() {
        val mockBlockers = listOf(AutopilotActionBlocker("test block"))
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(testAction()))
        whenever(mockFilter.isEnabled(mockBinding, testAction())).thenReturn(true)
        whenever(mockBinding.checkBlockers(testAction())).thenReturn(mockBlockers)
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(testAction())
        verify(mockReporting).reportOutcome(argThat { outcome.type == BLOCKED && outcome.blockers == mockBlockers })
    }

    @Test
    fun `don't perform non-acquired action`() {
        mockFencing.get().acquireActionExecution(testAction())
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(testAction()))
        whenever(mockFilter.isEnabled(mockBinding, testAction())).thenReturn(true)
        autopilot.cycle()
        verify(mockBinding, times(0)).processAction(testAction())
        verify(mockReporting).reportOutcome(argThat { outcome.type == NOT_ACQUIRED })
    }

    @Test
    fun `successfully perform action`() {
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(testAction()))
        whenever(mockFilter.isEnabled(mockBinding, testAction())).thenReturn(true)
        whenever(mockBinding.checkBlockers(testAction())).thenReturn(emptyList())
        autopilot.cycle()
        verify(mockBinding, times(1)).processAction(testAction())
        verify(mockReporting).reportOutcome(argThat { outcome.type == SUCCESSFUL })
    }

    @Test
    fun `failure during perform action`() {
        whenever(mockBinding.actionsToProcess()).thenReturn(listOf(testAction()))
        whenever(mockFilter.isEnabled(mockBinding, testAction())).thenReturn(true)
        whenever(mockBinding.checkBlockers(testAction())).thenReturn(emptyList())
        whenever(mockBinding.processAction(testAction())).thenThrow(RuntimeException("mock-failure"))
        autopilot.cycle()
        verify(mockBinding, times(1)).processAction(testAction())
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
        verify(mockBinding, never()).processAction(testAction())
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
}