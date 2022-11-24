package com.infobip.kafkistry.autopilot.reporting

import com.infobip.kafkistry.autopilot.binding.ActionMetadata
import com.infobip.kafkistry.autopilot.binding.AutopilotActionBlocker
import com.infobip.kafkistry.autopilot.binding.ClusterUnstable

data class ActionOutcome(
    val actionMetadata: ActionMetadata,
    val outcome: Outcome,
) {
    enum class OutcomeType(
        val order: Int,
        val doc: String,
    ) {
        CLUSTER_UNSTABLE(0, "Cluster is not fully stable recently."),
        DISABLED(1, "Particular action is disabled by Kafkistry's configuration. Won't attempt execution."),
        BLOCKED(2, "Pre-check phase detected some blockers. Won't attempt execution."),
        PENDING(3, "Action is planned for execution, now waiting pending expire period."),
        NOT_ACQUIRED(4, "Didn't acquire permission to execute action. Either previous acquiring did not yet expire or other instance of Kafkistry won acquirement race before. Won't attempt execution."),
        FAILED(5, "Action execution was attempted but resulted in failure."),
        RESOLVED(6, "Action was resolved without execution. Either other Kafkistry performed action sooner, or something external resolved action, such as manual intervention."),
        SUCCESSFUL(7, "Action was executed with success."),
    }

    data class Outcome(
        val type: OutcomeType,
        val sourceAutopilot: String,
        val timestamp: Long,
        val unstable: List<ClusterUnstable> = emptyList(),
        val blockers: List<AutopilotActionBlocker> = emptyList(),
        val executionError: String? = null,
    )
}
