package com.infobip.kafkistry.autopilot.service

import com.infobip.kafkistry.autopilot.binding.ActionDescription
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties

data class AutopilotStatus(
    val cyclePeriodMs: Long,
    val enableFilter: AutopilotEnabledFilterProperties,
    val possibleActions: List<ActionDescription>,
)