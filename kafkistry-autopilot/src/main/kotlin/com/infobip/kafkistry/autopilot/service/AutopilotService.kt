package com.infobip.kafkistry.autopilot.service

import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.binding.AutopilotBinding
import com.infobip.kafkistry.autopilot.config.ActionsCycleProperties
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.service.KafkistryIntegrityException
import org.springframework.stereotype.Service

@Service
class AutopilotService(
    private val actionsRepository: ActionsRepository,
    private val cycleProperties: ActionsCycleProperties,
    private val enabledProperties: AutopilotEnabledFilterProperties,
    private val bindings: List<AutopilotBinding<out AutopilotAction>>,
) {

    fun listActionFlows(): List<ActionFlow> = actionsRepository.findAll()

    fun getActionFlow(actionIdentifier: AutopilotActionIdentifier): ActionFlow {
        return actionsRepository.find(actionIdentifier)
            ?: throw KafkistryIntegrityException("No action with identifier '$actionIdentifier' found in repository")
    }

    fun autopilotStatus(): AutopilotStatus {
        return AutopilotStatus(
            cyclePeriodMs = cycleProperties.repeatDelayMs,
            enableFilter = enabledProperties,
            possibleActions = bindings.flatMap { it.listCapabilities() }
        )
    }
}