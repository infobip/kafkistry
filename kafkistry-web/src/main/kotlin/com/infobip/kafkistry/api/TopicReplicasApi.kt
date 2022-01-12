package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/topic-replicas")
class TopicReplicasApi(
    private val replicaDirsService: ReplicaDirsService
) {

    @GetMapping("/cluster/topic")
    fun getTopicReplicas(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): TopicReplicaInfos? = replicaDirsService.topicReplicaInfos(clusterIdentifier, topicName)

    @GetMapping("/cluster")
    fun getClusterTopicsReplicas(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): Map<TopicName, TopicReplicaInfos> = replicaDirsService.clusterTopicReplicaInfos(clusterIdentifier)

}