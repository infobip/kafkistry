package com.infobip.kafkistry.autopilot.repository

import com.infobip.kafkistry.autopilot.binding.ActionMetadata
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.scheduling.annotation.Scheduled

/**
 * Storage for action's outcomes to be able to see what's going on with Autopilot's activity.
 */
interface ActionsRepository {

    /**
     * Accepts new [actionOutcome] that's emitted by Autopilot and saves it in [ActionFlow]
     * which can be then queried by [find] or [findAll].
     * Multiple outcomes of same action (same [AutopilotActionIdentifier]) can be received by this repository
     * which are internally all contained within single [ActionFlow] representation.
     */
    fun save(actionOutcome: ActionOutcome)

    /**
     * Load all [actionFlows] into ths repository. Intended to use during initialization so that this repository
     * can be pre-populated with data.
     */
    fun putAll(actionFlows: List<ActionFlow>)

    /**
     * Returns all [ActionFlow]s currently stored in this repository ordered from newest to oldest.
     */
    fun findAll(): List<ActionFlow>

    /**
     * Finds and return [ActionFlow] with [actionIdentifier] or returns `null` if not found.
     */
    fun find(actionIdentifier: AutopilotActionIdentifier): ActionFlow?

    /**
     * Finds and return [List] of [ActionFlow] matching given [filter].
     */
    fun findBy(filter: (ActionMetadata) -> Boolean): List<ActionFlow>

    /**
     * Periodically called to perform cleanup.
     * Repository is intended to store limited amount of data with limited time retention.
     * This function deletes data which is too old or evicted because of count limit.
     */
    @Scheduled(fixedDelay = 60_000)
    fun cleanup()
}

data class ActionFlow(
    val actionIdentifier: AutopilotActionIdentifier,
    val metadata: ActionMetadata,
    val lastTimestamp: Long,
    val outcomeType: ActionOutcome.OutcomeType,
    val flow: List<ActionOutcome.Outcome>,
)

fun ActionOutcome.toActonFlow() = ActionFlow(
    actionIdentifier = actionMetadata.actionIdentifier,
    metadata = actionMetadata,
    lastTimestamp = outcome.timestamp,
    outcomeType = outcome.type,
    flow = listOf(outcome),
)

/**
 * Extension of [ActionsRepository] which is capable of syncing with other instances/deployment of Kafkistry
 * (if there are any at all) during application startup.
 */
interface EventInitializingActionsRepository : ActionsRepository, ApplicationListener<ContextRefreshedEvent> {

    override fun onApplicationEvent(event: ContextRefreshedEvent) = sendInitRequest()

    /**
     * Application just started, ask other instances/deployments of Kafkistry for their contents of [ActionsRepository].
     * This function emits new [ActionsRepositorySyncRequestEvent] which will be handled by [respondOnSyncInitRequest]
     */
    fun sendInitRequest()

    /**
     * Other Kafkistry instance/deployment asked for content, now this method is responsible for responding to
     * [requestEvent] by emitting new [ActionsRepositorySyncResponseEvent] which will have contents of this repository.
     * Request [requestEvent] was previously emitted by [sendInitRequest]. Output of this method will be handled
     * by [acceptSyncResponse].
     */
    fun respondOnSyncInitRequest(requestEvent: ActionsRepositorySyncRequestEvent)

    /**
     * Accepts previously emitted [responseEvent] by [respondOnSyncInitRequest]. Contents of [responseEvent]
     * will be used to populate this repository with data using [putAll].
     */
    fun acceptSyncResponse(responseEvent: ActionsRepositorySyncResponseEvent)
}