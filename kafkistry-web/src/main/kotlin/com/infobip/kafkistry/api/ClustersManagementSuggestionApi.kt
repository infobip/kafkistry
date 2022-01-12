package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.ThrottleBrokerTopicPartitions
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpSession

/**
 * API for submitting data into http session to be used for future requests
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/clusters-management-suggestion")
class ClustersManagementSuggestionApi(
    private val suggestionApi: SuggestionApi
) {

    companion object {
        const val THROTTLE_BROKER_TOPIC_PARTITIONS = "THROTTLE_BROKER_TOPIC_PARTITIONS"
    }

    @PostMapping("/submit-throttle-broker-topic-partitions")
    fun submitThrottleBrokerTopicPartitions(
        @RequestBody throttleBrokerTopicPartitions: ThrottleBrokerTopicPartitions,
        session: HttpSession,
    ) {
        val suggestion = suggestionApi.generateBrokerTopicsPartitionsThrottle(throttleBrokerTopicPartitions)
        session.setAttribute(THROTTLE_BROKER_TOPIC_PARTITIONS, suggestion)
    }

}