package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.offsets.TopicOffsets
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/topics/offsets")
class TopicOffsetsApi(
    private val topicOffsetsService: TopicOffsetsService
) {

    @GetMapping
    fun getTopicOffsets(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): TopicOffsets? = topicOffsetsService.topicOffsets(clusterIdentifier, topicName)

}