package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.service.kafkastreams.KStreamsAppsProvider
import com.infobip.kafkistry.service.kafkastreams.KafkaStreamsApp
import com.infobip.kafkistry.service.kafkastreams.TopicKStreamsInvolvement
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/kstream-apps")
class KStreamAppsApi(
    private val kStreamApps: KStreamsAppsProvider,
) {

    @GetMapping
    fun allClustersKStreamApps(): Map<KafkaClusterIdentifier, List<KafkaStreamsApp>> =
        kStreamApps.allClustersKStreamApps()

    @GetMapping("/topic")
    fun topicKStreamApps(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): TopicKStreamsInvolvement = kStreamApps.topicKStreamAppsInvolvement(clusterIdentifier, topicName)

    @GetMapping("/consumer-group")
    fun consumerGroupKStreamApps(
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): KafkaStreamsApp? = kStreamApps.consumerGroupKStreamApp(clusterIdentifier, consumerGroupId)

    @GetMapping("/app")
    fun kStreamApp(
        @RequestParam("kStreamAppId") kStreamAppId: KStreamAppId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): KafkaStreamsApp = kStreamApps.kStreamApp(clusterIdentifier, kStreamAppId)

}