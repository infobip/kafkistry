package com.infobip.kafkistry.api

import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.service.AutopilotService
import com.infobip.kafkistry.autopilot.service.AutopilotStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/autopilot")
class AutopilotApi(
    private val autopilotService: AutopilotService,
) {

    @GetMapping("/status")
    fun status(): AutopilotStatus = autopilotService.autopilotStatus()

    @GetMapping("/actions")
    fun listActionFlows(): List<ActionFlow> = autopilotService.listActionFlows()

    @GetMapping("/actions/{actionIdentifier}")
    fun getAction(
        @PathVariable("actionIdentifier") actionIdentifier: AutopilotActionIdentifier,
    ): ActionFlow = autopilotService.getActionFlow(actionIdentifier)

}