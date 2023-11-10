package com.infobip.kafkistry.webapp

import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.User
import org.springframework.security.core.session.SessionRegistry
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.stereotype.Service

data class UserSessions(
    val user: User,
    val currentUser: Boolean,
    val sessions: List<SessionInfo>,
)

data class SessionInfo(
    val sessionId: String,
    val expired: Boolean,
    val lastRequestTime: Long,
    val recordedRequests: SessionRecordedRequests?,
)

@Service
class UserSessionsService(
    private val sessionRepository: SessionRepository<Session>,
    private val sessionRegistry: SessionRegistry,
    private val currentRequestUserResolver: CurrentRequestUserResolver,
) {

    fun currentUsersSessions(): List<UserSessions> {
        val currentUser = currentRequestUserResolver.resolveUser()
        return sessionRegistry.allPrincipals
            .filterIsInstance<User>()
            .map { user ->
                val sessions = sessionRegistry.getAllSessions(user, true)
                    .map {
                        val recordedRequests = sessionRepository.findById(it.sessionId)
                            ?.readSessionRecordedRequests()
                        SessionInfo(it.sessionId, it.isExpired, it.lastRequest.time, recordedRequests)
                    }
                    .sortedByDescending { it.lastRequestTime }
                UserSessions(
                    user = user,
                    currentUser = user.username == currentUser?.username,
                    sessions = sessions,
                )
            }
            .sortedByDescending { it.sessions.firstOrNull()?.lastRequestTime ?: 0L }
    }

    fun expireSession(sessionId: String) {
        sessionRegistry.getSessionInformation(sessionId)?.expireNow()
    }

}