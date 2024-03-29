package com.infobip.kafkistry.webapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.session.Session
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
class SessionRecordingRequestFilter : GenericFilterBean() {


    private val log = LoggerFactory.getLogger(SessionRecordingRequestFilter::class.java)

    private val executor = Executors.newSingleThreadExecutor(CustomizableThreadFactory("session-request-recorder"))

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequest) {
            executor.submit { tryRecord(request) }  //fire and forget
        }
        chain.doFilter(request, response)
    }

    override fun destroy() {
        super.destroy()
        executor.shutdown()
    }

    private fun tryRecord(request: HttpServletRequest) {
        try {
            recordRequest(request)
        } catch (ex: Exception) {
            log.error("Encountered exception during recording request into session, ignoring", ex)
        }
    }

    private fun recordRequest(request: HttpServletRequest) {
        if ("/static/" in request.requestURI) {
            return //skip *.js and *.css resources
        }
        val session = request.getSession(false) ?: return
        val requests = session.getAttribute(LAST_REQUESTED_URLS_JSON)
            ?.let { it as? String }
            ?.let { mapper.readValue(it, SessionRecordedRequests::class.java) }
            ?: SessionRecordedRequests()
        val requestRecord = RecordedUrlRequests(
            method = request.method,
            uri = request.requestURI,
            query = request.queryString,
        )
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

    private infix fun RecordedUrlRequests.merge(other: RecordedUrlRequests) = RecordedUrlRequests(
        method, uri, query,
        firstTime = min(firstTime, other.firstTime),
        lastTime = max(lastTime, other.lastTime),
        count = count + other.count,
    )

    private infix fun RecordedUrlRequests.matches(other: RecordedUrlRequests) =
        method == other.method && uri == other.uri && query == other.query
}