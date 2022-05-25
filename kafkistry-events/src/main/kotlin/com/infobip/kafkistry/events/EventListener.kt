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

    fun handleEvent(event: E)
}

object EventListenerAdapter {

    fun <E : KafkistryEvent> maybeInvoke(listener: EventListener<E>, event: KafkistryEvent) {
        if (shouldInvoke(listener, event)) {
            invoke(listener, event)
        }
    }

    fun <E : KafkistryEvent> shouldInvoke(listener: EventListener<E>, event: KafkistryEvent): Boolean {
        return listener.eventType.java.isAssignableFrom(event.javaClass)
    }

    fun <E : KafkistryEvent> invoke(listener: EventListener<E>, event: KafkistryEvent) {
        return with(listener) {
            log.info("Handling event $event")
            handleEvent(eventType.cast(event))
        }
    }
}

