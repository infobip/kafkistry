package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.webapp.CompositeRequestInterceptor
import com.infobip.kafkistry.webapp.menu.MenuItemsInjector
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.ModelAndView
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Controller
abstract class BaseController {

    private val log = LoggerFactory.getLogger(BaseController::class.java)

    @Autowired
    private lateinit var compositeInterceptor: CompositeRequestInterceptor

    @Autowired
    private lateinit var menuItemsInjector: MenuItemsInjector

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleKafkistryException(
        request: HttpServletRequest, response: HttpServletResponse, exception: Exception
    ): ModelAndView {
        response.setHeader("exception-html", "true")
        val ajaxRequest = request.getHeader("ajax")?.let { it == "true" } ?: false
        val httpStatus = HttpStatus.resolve(response.status)
            .takeIf { it != HttpStatus.OK }
            ?: HttpStatus.INTERNAL_SERVER_ERROR
        val servletException = request.getAttribute("jakarta.servlet.error.exception") as? Exception
        val error = exception.takeUnless {
            it.javaClass == java.lang.Exception::class.java && it.cause == null && it.message == null
        } ?: servletException ?: exception
        val exceptionMessage = when (httpStatus) {
            HttpStatus.NOT_FOUND -> "Invalid url path: '" + request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) + "'"
            HttpStatus.FORBIDDEN -> "Access denied for '" + request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) + "'"
            else -> error.deepToString()
        }
        return ModelAndView(
            if (ajaxRequest) "registryExceptionAlert" else "registryException",
            mutableMapOf(
                "httpStatus" to httpStatus,
                "exceptionMessage" to exceptionMessage,
            )
        ).also {
            log.error("Exception occurred on {} '{}'", request.method, request.requestURI, exception)
            //injecting model to view because injecting interceptor is not invoked on error handling
            compositeInterceptor.injectModel(it, request)
            menuItemsInjector.injectMenuItems(it)
        }
    }

}
