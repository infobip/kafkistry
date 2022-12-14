package com.infobip.kafkistry.webapp.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.session.SessionRepository
import org.springframework.session.hazelcast.HazelcastIndexedSessionRepository

@Configuration
class SessionRegistryConfig {

    @Bean
    fun sessionRegistry(
        sessionRepository: SessionRepository<*>
    ): SessionRegistry {
        return if (sessionRepository is HazelcastIndexedSessionRepository) {
            SpringSessionHazelcastBackedSessionRegistry(sessionRepository)
        } else {
            SessionRegistryImpl()
        }
    }


}