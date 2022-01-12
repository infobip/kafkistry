package com.infobip.kafkistry.events

import org.slf4j.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * An interface which can be implemented by components which want to react when some specific event happens
 *
 * @see EventPublisher
 */
interface EventListener<E : KafkistryEvent> {

    val log: Logger
    val eventType: KClass<E>

    fun acceptEvent(event: KafkistryEvent) {
        if (eventType.java.isAssignableFrom(event.javaClass)) {
            log.info("Handling event $event")
            handleEvent(eventType.cast(event))
        }
    }

    fun handleEvent(event: E)
}

