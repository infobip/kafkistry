package com.infobip.kafkistry.autopilot

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilter
import com.infobip.kafkistry.autopilot.fencing.ActionAcquireFencing
import com.infobip.kafkistry.autopilot.fencing.ClusterStableFencing
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome.OutcomeType.*
import com.infobip.kafkistry.autopilot.reporting.AutopilotReporter
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.utils.deepToString
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
class Autopilot(
    private val bindings: List<AutopilotBinding<out AutopilotAction>>,
    private val enabledFilter: AutopilotEnabledFilter,
    private val checkingCache: CheckingCache,
    private val autopilotUser: AutopilotUser,
    private val backgroundIssues: BackgroundJobIssuesRegistry,
    private val clusterStableFencing: ClusterStableFencing,
    private val fencing: ActionAcquireFencing,
    private val reporter: AutopilotReporter,
    private val repository: ActionsRepository,
) {

    private val backgroundJob = BackgroundJob.of(
        "Autopilot discover/check/execute actions cycle", "autopilot", "cycle"
    )

    @Scheduled(
        fixedDelayString = "#{autopilotCycle.repeatDelayMs()}",
        initialDelayString = "#{autopilotCycle.afterStartupDelayMs()}"
    )
    fun cycle() = backgroundIssues.doCapturingException(backgroundJob) {
        clusterStableFencing.refresh()
        val discoveredActions = bindings.flatMap { it.discover() }
        val actions = discoveredActions.filterStableClustersActions()
        actions.forEach { it.handle().also(reporter::reportOutcome) }   //report ASAP as handing is completed
        alreadyResolved(discoveredActions).forEach(reporter::reportOutcome)
    }

    private fun <A : AutopilotAction> AutopilotBinding<A>.discover(): List<ActionCtx<A>> =
        checkingCache.withThreadLocalCache {
            actionsToProcess().map {
                ActionCtx(it, this, enabledFilter.isEnabled(this, it), checkBlockers(it))
            }
        }

    private fun ClusterRef.unstableStates(): List<ClusterUnstable> =
        clusterStableFencing.recentUnstableStates(identifier)

    private fun List<ActionCtx<out AutopilotAction>>.filterStableClustersActions(): List<ActionCtx<out AutopilotAction>> {
        return filter {
            val unstableStates = it.action.metadata.clusterRef?.unstableStates()
                ?: return@filter true //action not specific to cluster
            if (unstableStates.isNotEmpty()) {
                reporter.reportOutcome(it.outcome(CLUSTER_UNSTABLE, unstable = unstableStates))
            }
            unstableStates.isEmpty()
        }
    }

    private fun <A : AutopilotAction> ActionCtx<A>.handle(): ActionOutcome {
        if (!enabled) {
            return outcome(DISABLED)
        }
        if (blockers.isNotEmpty()) {
            return outcome(BLOCKED)
        }
        val acquired = fencing.acquireActionExecution(action)
        if (!acquired) {
            return outcome(NOT_ACQUIRED)
        }
        return try {
            autopilotUser.execAsAutopilot { execute() }
            outcome(SUCCESSFUL)
        } catch (ex: Throwable) {
            outcome(FAILED, failedCause = ex)
        }
    }

    private fun alreadyResolved(actions: List<ActionCtx<out AutopilotAction>>): List<ActionOutcome> {
        val activeActions = actions.map { it.action.actionIdentifier }.toSet()
        return repository.findAll().asSequence()
            .filter { it.actionIdentifier !in activeActions }
            .filter { it.outcomeType != SUCCESSFUL && it.outcomeType != RESOLVED }
            .filter { it.metadata.clusterRef?.unstableStates().orEmpty().isEmpty() }
            .map {
                ActionOutcome(
                    actionMetadata = it.metadata,
                    outcome = ActionOutcome.Outcome(
                        type = RESOLVED,
                        timestamp = System.currentTimeMillis(),
                        sourceAutopilot = thisAutopilot(),
                    )
                )
            }
            .toList()
    }

    private fun thisAutopilot(): String = autopilotUser.user.attributes.getValue("hostname").toString()

    private inner class ActionCtx<A : AutopilotAction>(
        val action: A,
        val binding: AutopilotBinding<A>,
        val enabled: Boolean,
        val blockers: List<AutopilotActionBlocker>,
    ) {

        fun execute() = binding.processAction(action)

        fun outcome(
            outcomeType: ActionOutcome.OutcomeType,
            failedCause: Throwable? = null,
            unstable: List<ClusterUnstable> = emptyList(),
        ) = ActionOutcome(
            actionMetadata = action.metadata,
            outcome = ActionOutcome.Outcome(
                type = outcomeType,
                sourceAutopilot = thisAutopilot(),
                timestamp = System.currentTimeMillis(),
                unstable = unstable,
                blockers = blockers,
                executionError = failedCause?.deepToString(),
            )
        )
    }
}
