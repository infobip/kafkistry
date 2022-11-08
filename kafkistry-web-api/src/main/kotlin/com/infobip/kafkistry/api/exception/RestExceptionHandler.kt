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
import javax.servlet.http.HttpServletRequest

@ControllerAdvice
class RestExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception, request: HttpServletRequest): ResponseEntity<Any>? {
        log.warn("Request ${request.method} ${request.requestURI} user: ${request.userPrincipal} encountered exception", ex)
        val status = (ex as? KafkistryException)
                ?.httpStatus
                ?.let { HttpStatus.valueOf(it) }
                ?: HttpStatus.INTERNAL_SERVER_ERROR
        val apiError = ApiError(status, ex.deepToString())
        return ResponseEntity(apiError, HttpHeaders(), apiError.status)
    }
}
