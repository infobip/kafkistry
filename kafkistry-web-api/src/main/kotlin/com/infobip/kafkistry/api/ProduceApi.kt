package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.produce.ProduceRequest
import com.infobip.kafkistry.service.produce.ProduceResult
import com.infobip.kafkistry.service.produce.KafkaProducerService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/produce")
@ConditionalOnProperty("app.produce.enabled", matchIfMissing = false)
class ProduceApi(
    private val producerService: KafkaProducerService
) {

    @PostMapping("/send-record")
    fun sendRecord(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody produceRequest: ProduceRequest,
    ): ProduceResult = producerService.produceRecord(clusterIdentifier, topicName, produceRequest)
}
