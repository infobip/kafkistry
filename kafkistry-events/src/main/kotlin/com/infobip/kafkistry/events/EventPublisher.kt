package com.infobip.kafkistry.events

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.topic.ITopic
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Interface which allows different parts of application to publish that something is happened without
 * worrying what other parts of application need to react to it.
 *
 * Another aspect is that implementation of this publisher can provide mechanism to deliver published
 * events to other running instances of application (HazelcastEventPublisher)
 *
 * @see EventListener
 */
interface EventPublisher {

    fun publish(event: KafkistryEvent)
}

class LoggingEventPublisher(private val delegate: EventPublisher) : EventPublisher {

    private val log = LoggerFactory.getLogger(LoggingEventPublisher::class.java)

    override fun publish(event: KafkistryEvent) {
        log.info("Publishing event $event using ${delegate.javaClass.simpleName}")
        delegate.publish(event)
    }

}

class DefaultEventPublisher(
        private val listeners: List<EventListener<out KafkistryEvent>>
) : EventPublisher {

    private val log = LoggerFactory.getLogger(DefaultEventPublisher::class.java)

    override fun publish(event: KafkistryEvent) {
        listeners.forEach {
            try {
                it.acceptEvent(event)
            } catch (ex: Exception) {
                //failure in one listener should not affect execution of other listeners
                log.error("Listener ${it.javaClass.simpleName} threw an exception for event $event", ex)
            }
        }
    }

}

class HazelcastEventPublisher(
    private val listeners: List<EventListener<out KafkistryEvent>>,
    private val hazelcastInstance: HazelcastInstance,
    private val ackTimeoutMs: Long
) : EventPublisher {

    companion object {
        const val KR_EVENTS = "kafkistry-events"
        const val KR_ACK = "kafkistry-ack"
    }

    init {
        listeners.forEach { listener ->
            eventTopic().addMessageListener { eventMessage ->
                listener.acceptEvent(eventMessage.messageObject)
                ackTopic().publish(eventMessage.messageObject.ackId())
            }
        }
    }

    override fun publish(event: KafkistryEvent) {
        val latch = CountDownLatch(expectedNumAck())
        val registration = ackTopic().addMessageListener { ackEvent ->
            if (ackEvent.messageObject == event.ackId()) {
                latch.countDown()
            }
        }
        try {
            eventTopic().publish(event)
            latch.await(ackTimeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            ackTopic().removeMessageListener(registration)
        }
    }

    private fun eventTopic(): ITopic<KafkistryEvent> = hazelcastInstance.getTopic(KR_EVENTS)
    private fun ackTopic(): ITopic<String> = hazelcastInstance.getTopic(KR_ACK)

    private fun KafkistryEvent.ackId(): String = toString().hashCode().toString()
    private fun expectedNumAck(): Int = hazelcastInstance.cluster.members.size * listeners.size

}