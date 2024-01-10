package com.infobip.kafkistry.api.exception

import com.infobip.kafkistry.service.KafkistryException
import com.infobip.kafkistry.utils.deepToString
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatusCode
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest

@ControllerAdvice
class RestExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception, request: HttpServletRequest): ResponseEntity<Any>? {
        log.warn("Request ${request.method} ${request.requestURI} user: ${request.userPrincipal} encountered exception", ex)
        return createResponse(ex)
    }

    override fun handleExceptionInternal(
        ex: java.lang.Exception, body: Any?, headers: HttpHeaders, statusCode: HttpStatusCode, request: WebRequest
    ): ResponseEntity<Any>? {
        val servletRequest = (request as? ServletWebRequest)?.request
        log.warn("Request ${servletRequest?.method.orEmpty()} ${servletRequest?.requestURI.orEmpty()} user: ${request.userPrincipal} encountered exception", ex)
        return createResponse(ex, statusCode)
    }

    private fun createResponse(ex: java.lang.Exception, statusCode: HttpStatusCode? = null): ResponseEntity<Any> {
        val status = (ex as? KafkistryException)
            ?.httpStatus
            ?.let { HttpStatus.valueOf(it) }
            ?: (statusCode as? HttpStatus)
            ?: HttpStatus.INTERNAL_SERVER_ERROR
        val apiError = ApiError(status, ex.deepToString())
        return ResponseEntity(apiError, HttpHeaders(), apiError.status)
    }
}
