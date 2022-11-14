package com.infobip.kafkistry.autopilot.binding

/**
 * Plugin point for autopilot's functionality.
 * [com.infobip.kafkistry.autopilot.Autopilot] uses this [AutopilotAction] to discover/check/execute actions.
 * Functions in this binding will be invoked periodically by autopilot.
 */
interface AutopilotBinding<A : AutopilotAction> {

    /**
     * List [ActionDescription]s of **all** actions that this binding implementation could return as result
     * of [actionsToProcess]. This it called to collect possible actions from all bindings and display them in UI.
     */
    fun listCapabilities(): List<ActionDescription>

    /**
     * Report actions that should/could be performed by autopilot.
     * @return a [List] of [AutopilotAction] that need processing. Returned list should contain all actions that should
     * be executed regardless if will have some blockers.
     * @see checkBlockers
     */
    fun actionsToProcess(): List<A>

    /**
     * Check are there any **blockers** for [action].
     * Returning non-empty list will prevent action execution.
     * @param action to check for blockers
     * @return List of current blockers indicating reasons why [action] should not be executed.
     */
    fun checkBlockers(action: A): List<AutopilotActionBlocker>

    /**
     * Execute/process [action].
     * Autopilot will invoke this function only after all pre-checks are indicating that [action] can and should be performed.
     * This function is supposed to _block_ until completion of [action] execution.
     * Any failure is expected to raise any [Exception] which will indicate failure cause.
     * @param action to perform
     * @throws [Exception] indicating unsuccessful execution
     */
    @Throws(Exception::class)
    fun processAction(action: A)

}
