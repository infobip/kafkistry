package com.infobip.kafkistry.service.kafkastreams

import com.infobip.kafkistry.model.TopicName

typealias KStreamAppId = String

data class KafkaStreamsApp(
    val kafkaStreamAppId: KStreamAppId,
    val inputTopics: List<TopicName>,
    val kStreamInternalTopics: List<TopicName>,
)

data class TopicKStreamsInvolvement(
    val inputOf: List<KafkaStreamsApp>,
    val internalIn: KafkaStreamsApp?,
) {
    companion object {
        val NONE = TopicKStreamsInvolvement(emptyList(), null)
    }
}