package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.events.ClusterEvent
import com.infobip.kafkistry.events.EventListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Use case for this ClusterEventListener is to refresh latest seen configuration state of a cluster.
 * This refresh is done after some ClusterEvent is fired, which means that some operation on cluster is performed
 * on kafka.
 */
@Component
class ClusterEventListener(
        private val kafkaStateProvider: KafkaClustersStateProvider
) : EventListener<ClusterEvent> {

    override val log = LoggerFactory.getLogger(ClusterEventListener::class.java)!!
    override val eventType = ClusterEvent::class

    override fun handleEvent(event: ClusterEvent) {
        kafkaStateProvider.refreshClusterState(event.clusterIdentifier)
    }
}