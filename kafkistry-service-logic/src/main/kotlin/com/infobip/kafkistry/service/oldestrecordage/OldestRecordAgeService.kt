package com.infobip.kafkistry.service.oldestrecordage

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
class OldestRecordAgeService(
    private val oldestRecordAgeProvider: KafkaOldestRecordAgeProvider
) {

    fun topicOldestRecordAges(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ): Map<Partition, Long>? = oldestRecordAgeProvider.getLatestState(clusterIdentifier)
        ?.earliestRecordAges
        ?.get(topicName)

    fun clusterTopicOldestRecordAges(
        clusterIdentifier: KafkaClusterIdentifier
    ): Map<TopicName, Map<Partition, Long>>? = oldestRecordAgeProvider.getLatestState(clusterIdentifier)
        ?.earliestRecordAges

    fun allClustersTopicOldestRecordAges(): Map<KafkaClusterIdentifier, Map<TopicName, Map<Partition, Long>>?> {
        return oldestRecordAgeProvider.getAllLatestStates()
            .mapValues { (_, oldestRecordAgesState) ->
                oldestRecordAgesState.earliestRecordAges
            }
    }

}