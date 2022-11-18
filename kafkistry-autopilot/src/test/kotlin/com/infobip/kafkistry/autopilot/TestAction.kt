package com.infobip.kafkistry.autopilot

import com.infobip.kafkistry.autopilot.binding.ActionDescription
import com.infobip.kafkistry.autopilot.binding.ActionMetadata
import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier

data class TestAction(
    override val metadata: ActionMetadata
) : AutopilotAction {

    constructor(actionIdentifier: AutopilotActionIdentifier = "mock-action") : this(
        ActionMetadata(
            actionIdentifier,
            ActionDescription("TestAction", TestAction::class.java.name, "TEST", "testing mock")
        )
    )
}