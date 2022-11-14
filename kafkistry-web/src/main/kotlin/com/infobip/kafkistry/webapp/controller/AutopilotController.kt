package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.AutopilotApi
import com.infobip.kafkistry.webapp.url.AutopilotUrls.Companion.AUTOPILOT
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$AUTOPILOT")
class AutopilotController(
    private val autopilotApi: AutopilotApi,
) : BaseController() {

    @GetMapping
    fun showAutopilotAll(): ModelAndView {
        val autopilotStatus = autopilotApi.status()
        val actionFlows = autopilotApi.listActionFlows()
        return ModelAndView("autopilot/all", mapOf(
            "autopilotStatus" to autopilotStatus,
            "actionFlows" to actionFlows,
        ))
    }
}