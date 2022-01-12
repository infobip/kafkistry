package com.infobip.kafkistry.webapp.security

import com.hazelcast.map.IMap
import org.springframework.security.core.context.SecurityContext
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.MapSession
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.session.hazelcast.Hazelcast4IndexedSessionRepository
import org.springframework.session.security.SpringSessionBackedSessionRegistry
import java.util.concurrent.atomic.AtomicReference

class SpringSessionHazelcast4BackedSessionRegistry private constructor(
    private val sessionRepo: SessionsRepo,
) : SpringSessionBackedSessionRegistry<Session>(sessionRepo) {

    constructor(hazelcast4SessionRepository: Hazelcast4IndexedSessionRepository) : this(
        @Suppress("UNCHECKED_CAST")
        SessionsRepo(hazelcast4SessionRepository as FindByIndexNameSessionRepository<Session>)
    )

    companion object {
        private fun sessionsOf(hazelcast4SessionRepository: SessionRepository<*>): IMap<String, MapSession> {
            //antonio-tomac
            // - guilty as charged for reflection, expect possible breakage when upgrading spring-session-hazelcast
            // - this was the simplest way to probe directly to hazelcast's map of all sessions
            // - otherwise, proper implementation would require listening for various session events (as SessionRegistryImpl does),
            //   keeping maintaining state based on seen session events,
            // - another problem with event based approach is restarting node in cluster,
            //   such node would have no state from previous events, so yet another mechanism would be needed for
            //   initialization of sessions state
            return Hazelcast4IndexedSessionRepository::class.java
                .getDeclaredField("sessions")
                .apply { trySetAccessible() }
                .get(hazelcast4SessionRepository)
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as IMap<String, MapSession>
                }
        }

        private fun userOf(session: Session): User? = session
            .getAttribute<SecurityContext>("SPRING_SECURITY_CONTEXT")
            ?.authentication?.principal?.let { it as? User }

    }

    private class SessionsRepo(
        val delegate: FindByIndexNameSessionRepository<Session>,
    ) : FindByIndexNameSessionRepository<Session> by delegate {

        private val sessions = sessionsOf(delegate)

        private val principalSessions = AtomicReference<Map<String, PrincipalSessions>>()

        data class PrincipalSessions(
            val principal: User,
            val sessions: Map<String, Session>,
        )

        fun refreshCaches() {
            sessions.entries
                .mapNotNull { (sessionId, session) ->
                    val user = userOf(session) ?: return@mapNotNull null
                    Triple(user, sessionId, session)
                }
                .groupBy { it.first.username }
                .mapValues { (_, sessions) ->
                    val user = sessions.first().first
                    val sessionsMap = sessions.associate { it.second to it.third }
                    PrincipalSessions(user, sessionsMap)
                }
                .also { principalSessions.set(it) }
        }

        override fun findByPrincipalName(principalName: String?): Map<String, Session> {
            if (principalSessions.get() == null) {
                refreshCaches()
            }
            return principalSessions.get().orEmpty()[principalName]?.sessions.orEmpty()
        }

        fun getAllPrincipals(): List<Any> {
            refreshCaches()
            return principalSessions.get().orEmpty().values.map { it.principal }
        }
    }

    override fun getAllPrincipals(): List<Any> = sessionRepo.getAllPrincipals()

}