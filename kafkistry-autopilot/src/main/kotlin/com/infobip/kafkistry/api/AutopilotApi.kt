package com.infobip.kafkistry.api

import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.repository.ActionFlow
import com.infobip.kafkistry.autopilot.service.AutopilotService
import com.infobip.kafkistry.autopilot.service.AutopilotStatus
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.TopicName
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@ConditionalOnProperty("app.autopilot.enabled", matchIfMissing = true)
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

    @GetMapping("/actions/find")
    fun findActions(
        @RequestParam allRequestParams: Map<String, String>
    ): List<ActionFlow> = autopilotService.findActions(attributes = allRequestParams)

    @GetMapping("/actions/find/cluster")
    fun findClusterActions(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): List<ActionFlow> = autopilotService.findClusterActions(clusterIdentifier)

    @GetMapping("/actions/find/topic")
    fun findTopicActions(
        @RequestParam("topicName") topicName: TopicName,
    ): List<ActionFlow> = autopilotService.findTopicActions(topicName)

    @GetMapping("/actions/find/topic-cluster")
    fun findTopicOnClusterActions(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): List<ActionFlow> = autopilotService.findTopicOnClusterActions(topicName, clusterIdentifier)

    @GetMapping("/actions/find/principal")
    fun findPrincipalActions(
        @RequestParam("principal") principal: PrincipalId,
    ): List<ActionFlow> = autopilotService.findPrincipalActions(principal)

}