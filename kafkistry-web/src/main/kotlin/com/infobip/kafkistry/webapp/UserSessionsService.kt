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

data class UserSessionsAndStats(
    val userSessions: List<UserSessions>,
    val stats: List<RecordedRequestStats>,
)

data class RecordedRequestStats(
    val request: RecordedRequest,
    val metrics: RecordedRequestMetrics,
)

data class RecordedRequest(
    val method: String,
    val uri: String,
    val query: String?,
)

data class RecordedRequestMetrics(
    val firstTime: Long,
    val lastTime: Long,
    val count: Int,
    val usernames: List<String>,
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

    fun currentUsersSessionsAndStats(): UserSessionsAndStats {
        val usersSessions = currentUsersSessions()
        val stats = usersSessions
            .flatMap { userSessions ->
                userSessions.sessions.flatMap { session ->
                    session.recordedRequests?.urlRequests.orEmpty()
                        .map {
                            Triple(RecordedRequest(it.method, it.uri, it.query), userSessions.user, it)
                        }
                }
            }
            .groupBy({ it.first }, { it.second to it.third })
            .map { (request, userRequests) ->
                RecordedRequestStats(
                    request = request,
                    metrics = RecordedRequestMetrics(
                        firstTime = userRequests.minOf { it.second.firstTime },
                        lastTime = userRequests.maxOf { it.second.lastTime },
                        count = userRequests.sumOf { it.second.count },
                        usernames = userRequests.map { it.first.username }.distinct(),
                    ),
                )
            }
            .sortedByDescending { it.metrics.count }
        return UserSessionsAndStats(usersSessions, stats)
    }

    fun expireSession(sessionId: String) {
        sessionRegistry.getSessionInformation(sessionId)?.expireNow()
    }

}