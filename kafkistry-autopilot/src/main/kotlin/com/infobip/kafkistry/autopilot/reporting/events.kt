package com.infobip.kafkistry.autopilot.reporting

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.autopilot.repository.ActionsRepository
import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.KafkistryEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

data class AutopilotActionOutcomeEvent(
    val actionOutcomeJson: String
): KafkistryEvent

private object ActionOutcomeMapper {
    private val mapper = jacksonObjectMapper()

    fun ActionOutcome.toEvent() =
        AutopilotActionOutcomeEvent(mapper.writeValueAsString(this))

    fun AutopilotActionOutcomeEvent.toOutcome(): ActionOutcome =
        mapper.readValue(actionOutcomeJson, ActionOutcome::class.java)
}

@Component
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
class EventPublishingAutopilotReporter(
    private val eventPublisher: EventPublisher,
): AutopilotReporter {

    override fun reportOutcome(actionOutcome: ActionOutcome) {
        with(ActionOutcomeMapper) {
            //emit event HERE
            eventPublisher.publish(actionOutcome.toEvent())
        }
    }
}

@Component
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
class ActionOutcomeEventListener(
    @Lazy private val actionsRepository: ActionsRepository,
) : EventListener<AutopilotActionOutcomeEvent> {

    override val log: Logger = LoggerFactory.getLogger(ActionOutcomeEventListener::class.java)
    override val eventType = AutopilotActionOutcomeEvent::class

    override fun handleEvent(event: AutopilotActionOutcomeEvent) {
        //receive event HERE
        with(ActionOutcomeMapper) {
            actionsRepository.save(event.toOutcome())
        }
    }
}

