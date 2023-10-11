package com.infobip.kafkistry.repository.events

import com.infobip.kafkistry.events.EventListener
import com.infobip.kafkistry.events.RepositoryEvent
import com.infobip.kafkistry.repository.storage.git.GitRefreshTrigger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * Main reason for reacting to RepositoryEvent is situation when some git operation is performed on one instance,
 * event will bee emitted from that instance, and it will be received by all instances to refresh git repository.
 *
 * This mechanism allows consistent view of git repository when requests incoming from UI are load balanced across different
 * instances of application.
 */
@Component
@ConditionalOnBean(GitRefreshTrigger::class)
class RepositoryEventListener(
        private val gitRefreshTrigger: GitRefreshTrigger
) : EventListener<RepositoryEvent> {

    override val log = LoggerFactory.getLogger(RepositoryEventListener::class.java)!!
    override val eventType = RepositoryEvent::class

    override fun handleEvent(event: RepositoryEvent) {
        gitRefreshTrigger.trigger()
    }
}