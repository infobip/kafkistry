package com.infobip.kafkistry.autopilot.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.KafkistryEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

sealed interface ActionsRepositorySyncEvent : KafkistryEvent

data class ActionsRepositorySyncRequestEvent(
    val syncId: Long,
    val newerThanTimestamp: Long,
) : ActionsRepositorySyncEvent

data class ActionsRepositorySyncResponseEvent(
    val syncId: Long,
    val actionFlowsJsons: List<String>,
) : ActionsRepositorySyncEvent {
    override fun toString(): String {
        return "ActionsRepositorySyncResponseEvent(syncId=$syncId, actionFlowsJsons=[size=${actionFlowsJsons.size}])"
    }
}

class EventInitializingActionsRepositoryImpl(
    private val delegate: ActionsRepository,
    private val eventPublisher: EventPublisher,
) : ActionsRepository by delegate, EventInitializingActionsRepository {

    private val syncId = System.currentTimeMillis()
    private val mapper = jacksonObjectMapper()

    override fun sendInitRequest() {
        val newestTimestamp = findAll().maxOfOrNull { it.lastTimestamp } ?: 0L
        eventPublisher.publish(ActionsRepositorySyncRequestEvent(syncId, newestTimestamp))
    }

    override fun respondOnSyncInitRequest(requestEvent: ActionsRepositorySyncRequestEvent) {
        val actionFlowsJsons = findAll()
            .filter { it.lastTimestamp >= requestEvent.newerThanTimestamp }
            .map { mapper.writeValueAsString(it) }
        val response = ActionsRepositorySyncResponseEvent(requestEvent.syncId, actionFlowsJsons)
        eventPublisher.publish(response)
    }

    override fun acceptSyncResponse(responseEvent: ActionsRepositorySyncResponseEvent) {
        if (responseEvent.syncId != syncId) return
        val actionFlows = responseEvent.actionFlowsJsons.map { mapper.readValue(it, ActionFlow::class.java) }
        delegate.putAll(actionFlows)
    }

}

class ActionsRepositorySyncEventListener(
    private val eventSyncedRepository: EventInitializingActionsRepository,
) : EventListener<ActionsRepositorySyncEvent> {

    override val log: Logger = LoggerFactory.getLogger(ActionsRepositorySyncEventListener::class.java)
    override val eventType = ActionsRepositorySyncEvent::class

    override fun handleEvent(event: ActionsRepositorySyncEvent) {
        when (event) {
            is ActionsRepositorySyncRequestEvent -> eventSyncedRepository.respondOnSyncInitRequest(event)
            is ActionsRepositorySyncResponseEvent -> eventSyncedRepository.acceptSyncResponse(event)
        }
    }

}