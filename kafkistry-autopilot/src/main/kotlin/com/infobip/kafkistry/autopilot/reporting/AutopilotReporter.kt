package com.infobip.kafkistry.autopilot.reporting

interface AutopilotReporter {

    /**
     * Accept given [actionOutcome] as fact that happened and report it to downstream components.
     * End goal is that [ActionOutcome] gets stored in [com.infobip.kafkistry.autopilot.repository.ActionsRepository].
     */
    fun reportOutcome(actionOutcome: ActionOutcome)
}