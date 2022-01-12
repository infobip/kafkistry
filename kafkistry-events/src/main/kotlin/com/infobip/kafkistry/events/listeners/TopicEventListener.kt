package com.infobip.kafkistry.events.listeners

import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.events.TopicEvent
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Use case for this TopicEventListener is to refresh latest seen configuration state of a cluster.
 * This refresh is done after some TopicEvent is fired, which means that some operation on topic is performed
 * on kafka.
 */
@Component
class TopicEventListener(
        private val kafkaStateProvider: KafkaClustersStateProvider
) : EventListener<TopicEvent> {

    override val log = LoggerFactory.getLogger(TopicEventListener::class.java)!!
    override val eventType = TopicEvent::class

    override fun handleEvent(event: TopicEvent) {
        kafkaStateProvider.refreshClusterState(event.clusterIdentifier)
    }
}