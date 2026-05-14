package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.KafkaConsumerService
import com.infobip.kafkistry.service.consume.KafkaRecord
import com.infobip.kafkistry.service.consume.UnmaskedRecordReadRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/consume")
@ConditionalOnProperty(
    name = ["app.consume.enabled", "app.consume.unmaskedRevealEnabled"],
    matchIfMissing = true,
)
class SensitiveDataConsumeApi(
    private val topicConsumer: KafkaConsumerService,
) {

    @PostMapping("/read-record-unmasked")
    fun readRecordUnmasked(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody request: UnmaskedRecordReadRequest,
    ): KafkaRecord = topicConsumer.readSingleRecordUnmasked(
        clusterIdentifier = clusterIdentifier,
        topicName = topicName,
        partition = request.partition,
        offset = request.offset,
        recordDeserialization = request.recordDeserialization,
    )

}
