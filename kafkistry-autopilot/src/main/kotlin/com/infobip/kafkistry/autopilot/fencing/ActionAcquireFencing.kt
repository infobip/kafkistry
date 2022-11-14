package com.infobip.kafkistry.autopilot.fencing

import com.infobip.kafkistry.autopilot.binding.AutopilotAction

/**
 * Barrier for preventing concurrent action execution (in case of multi-instance Kafkistry deployment).
 * It also limits how much time needs to pass in order to get subsequent acquire for same [AutopilotAction].
 */
interface ActionAcquireFencing {

    /**
     * Try to acquire/get-permission for executing given [action].
     * @return `true` - acquired; `false` - didn't acquire
     */
    fun acquireActionExecution(action: AutopilotAction): Boolean

}