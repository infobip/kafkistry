package com.infobip.kafkistry.autopilot.repository

import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.config.ActionsRepositoryProperties
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import java.util.concurrent.ConcurrentHashMap

class InMemoryActionsRepository(
    private val repeatWindowMs: Long,
    private val properties: ActionsRepositoryProperties.LimitsProperties,
) : ActionsRepository {


    private val all = ConcurrentHashMap<AutopilotActionIdentifier, ActionFlow>()

    override fun save(actionOutcome: ActionOutcome) {
        val singularFlow = actionOutcome.toActonFlow()
        save(singularFlow)
    }

    private fun save(actionFlow: ActionFlow) {
        all.merge(actionFlow.actionIdentifier, actionFlow) { currentFlow, newFlow ->
            currentFlow merge newFlow
        }
    }

    private infix fun ActionFlow.merge(newFlow: ActionFlow): ActionFlow {
        val resultingFlow = when {
            newFlow.lastTimestamp > lastTimestamp + repeatWindowMs -> newFlow
            newFlow.outcomeType.order > outcomeType.order -> newFlow
            newFlow.outcomeType == outcomeType -> maxOf(newFlow, this, Comparator.comparingLong { it.lastTimestamp })
            else -> this
        }
        return copy(
            lastTimestamp = resultingFlow.lastTimestamp,
            outcomeType = resultingFlow.outcomeType,
            flow = (flow + newFlow.flow).distinct().sortedBy { it.timestamp }.maybeCollapse(),
        )
    }

    /**
     * Defensive action to avoid possible memory exhaustion if action continues to repeat without success/resolving
     */
    private fun List<ActionOutcome.Outcome>.maybeCollapse(): List<ActionOutcome.Outcome> {
        if (size <= properties.maxPerAction) {
            return this
        }
        val digit = Regex("\\d")
        fun ActionOutcome.Outcome.distinctionKey(): Any = listOf(
            type, sourceAutopilot,
            unstable.map { it.stateTypeName }.toSet(),
            blockers.map { it.copy(placeholders = emptyMap()) },
            executionError?.replace(digit, "_"),
        )
        val distinctionGroups = reversed().groupBy { it.distinctionKey() }.values
        val collapsed = sequence {
            val maxGroupLength = distinctionGroups.maxOfOrNull { it.size } ?: 0
            repeat(maxGroupLength) { index ->
                distinctionGroups.forEach {
                    if (index in it.indices) {
                        yield(it[index])
                    }
                }
            }
        }.take(properties.maxPerAction).toList()
        return collapsed.sortedBy { it.timestamp }

    }

    override fun putAll(actionFlows: List<ActionFlow>) {
        actionFlows.forEach { save(it) }
    }

    override fun findAll(): List<ActionFlow> {
        return all.values.sortedByDescending { it.lastTimestamp }
    }

    override fun find(actionIdentifier: AutopilotActionIdentifier): ActionFlow? {
        return all[actionIdentifier]
    }

    override fun cleanup() {
        val minTimestamp = System.currentTimeMillis() - properties.retentionMs
        all.entries.removeIf { it.value.lastTimestamp < minTimestamp }
        if (all.size > properties.maxCount) {
            findAll().takeLast(all.size - properties.maxCount).forEach {
                all.remove(it.actionIdentifier)
            }
        }
    }
}