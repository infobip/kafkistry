package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/topics/oldest-record-ages")
@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
class TopicOldestRecordAgeApi(
    private val oldestRecordAgeService: OldestRecordAgeService
) {

    @GetMapping
    fun getTopicOldestRecordAges(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): Map<Partition, Long>? = oldestRecordAgeService.topicOldestRecordAges(clusterIdentifier, topicName)

}