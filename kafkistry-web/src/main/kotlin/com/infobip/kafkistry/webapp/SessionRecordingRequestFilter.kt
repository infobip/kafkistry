package com.infobip.kafkistry.webapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.eclipse.jetty.ee10.servlet.SessionHandler
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.session.SessionRegistry
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

data class SessionRecordedRequests(
    val createdTime: Long = System.currentTimeMillis(),
    val totalCount: Int = 0,
    val urlRequests: List<RecordedUrlRequests> = emptyList(),
)

data class RecordedUrlRequests(
    val method: String,
    val uri: String,
    val query: String?,
    val firstTime: Long = System.currentTimeMillis(),
    val lastTime: Long = firstTime,
    val count: Int = 1,
)

private val mapper = jacksonMapperBuilder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

fun SessionRecordedRequests.toJson(): String = mapper.writeValueAsString(this)
fun Session.readSessionRecordedRequests(): SessionRecordedRequests? = getAttribute<String?>(LAST_REQUESTED_URLS_JSON)
    ?.let { mapper.readValue(it, SessionRecordedRequests::class.java) }

const val LAST_REQUESTED_URLS_JSON = "RECORDED_REQUESTS_JSON"
const val MAX_RECORDS_PER_SESSION = 50

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class SessionRecordingRequestFilter(
    private val currentUserResolver: CurrentRequestUserResolver,
    private val sessionRepository: SessionRepository<Session>,
    private val sessionRegistry: SessionRegistry,
) : GenericFilterBean() {


    private val log = LoggerFactory.getLogger(SessionRecordingRequestFilter::class.java)

    private val executor = Executors.newSingleThreadExecutor(CustomizableThreadFactory("session-request-recorder"))

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        request.maybeRecord()
        chain.doFilter(request, response)
    }

    override fun destroy() {
        super.destroy()
        executor.shutdown()
    }

    private fun ServletRequest.maybeRecord() {
        if (this !is HttpServletRequest) {
            return
        }
        val uri = requestURI
        if ("/static/" in uri) {
            return //skip *.js and *.css resources
        }
        val attrSession = resolveSession() ?: return
        val requestRecord = RecordedUrlRequests(method = method, uri = uri, query = queryString)
        executor.submit { tryRecord(requestRecord, attrSession) }  //fire and forget
    }

    private fun tryRecord(requestRecord: RecordedUrlRequests, attrSession: AttributeSession) {
        try {
            recordRequest(requestRecord, attrSession)
        } catch (ex: Exception) {
            log.error("Encountered exception during recording request into session, ignoring", ex)
        }
    }

    private fun recordRequest(requestRecord: RecordedUrlRequests, session: AttributeSession) {
        val requests = session.getAttribute(LAST_REQUESTED_URLS_JSON)
            ?.let { it as? String }
            ?.let { mapper.readValue(it, SessionRecordedRequests::class.java) }
            ?: SessionRecordedRequests()
        val existingRecord = requests.urlRequests.find { it matches requestRecord }
        val updatedRequestsList = if (existingRecord == null) {
            listOf(requestRecord) + requests.urlRequests.take(MAX_RECORDS_PER_SESSION - 1)
        } else {
            listOf(existingRecord merge requestRecord) + requests.urlRequests.minus(existingRecord)
        }
        val updatedRequests = requests.copy(
            totalCount = requests.totalCount + 1,
            urlRequests = updatedRequestsList,
        )
        session.setAttribute(LAST_REQUESTED_URLS_JSON, mapper.writeValueAsString(updatedRequests))
    }

    private fun HttpServletRequest.resolveSession(): AttributeSession? {
        val httpSession = getSession(false)?.takeIf {
            //don't take sessions that are not created by SessionRepositoryFilter
            it !is SessionHandler.ServletSessionApi
        }
        if (httpSession != null) {
            return AttributeSession.HttpAttrSession(httpSession)
        }
        val user = currentUserResolver.resolveUser() ?: return null
        val existingSessionId = sessionRegistry.getAllSessions(user, true)
            .maxByOrNull { it.lastRequest }?.sessionId
        val existingSession = existingSessionId?.let { sessionRepository.findById(it) }
        if (existingSession != null) {
            log.info("Found existing session id=${existingSessionId} for user $user")
            return AttributeSession.SessionAttrSession(existingSession)
        }
        val newSession = sessionRepository.createSession()
        newSession.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext())
        log.info("Created new session id=${newSession.id} for user $user")
        return AttributeSession.SessionAttrSession(newSession)
    }

    private interface AttributeSession {

        fun getAttribute(key: String): Any?
        fun setAttribute(key: String, value: Any?)

        class HttpAttrSession(val s: HttpSession) : AttributeSession {
            override fun getAttribute(key: String): Any? = s.getAttribute(key)
            override fun setAttribute(key: String, value: Any?) = s.setAttribute(key, value)
        }

        class SessionAttrSession(val s: Session) : AttributeSession {
            override fun getAttribute(key: String): Any? = s.getAttribute(key)
            override fun setAttribute(key: String, value: Any?) = s.setAttribute(key, value)
        }
    }

    private infix fun RecordedUrlRequests.merge(other: RecordedUrlRequests) = RecordedUrlRequests(
        method, uri, query,
        firstTime = min(firstTime, other.firstTime),
        lastTime = max(lastTime, other.lastTime),
        count = count + other.count,
    )

    private infix fun RecordedUrlRequests.matches(other: RecordedUrlRequests) =
        method == other.method && uri == other.uri && query == other.query
}