package com.infobip.kafkistry.events.listeners

import com.infobip.kafkistry.events.ConsumerGroupEvent
import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * React to event of consumer group, refresh consumer groups for cluster that event is about
 */
@Component
class ConsumerGroupEventListener(
    private val consumerGroupsStateProvider: KafkaClustersStateProvider
) : EventListener<ConsumerGroupEvent> {

    override val log: Logger = LoggerFactory.getLogger(ConsumerGroupEventListener::class.java)

    override val eventType = ConsumerGroupEvent::class

    override fun handleEvent(event: ConsumerGroupEvent) {
        consumerGroupsStateProvider.refreshClusterState(event.clusterIdentifier)
    }
}