package com.infobip.kafkistry.webapp.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.error.ErrorController as SpringErrorController

@Controller
@RequestMapping("\${app.http.root-path}")
class ErrorController : BaseController(), SpringErrorController {

    @RequestMapping("/error")
    fun handleError(
        request: HttpServletRequest, response: HttpServletResponse, exception: Exception
    ): ModelAndView = handleKafkistryException(request, response, exception)
}