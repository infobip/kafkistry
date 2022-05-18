package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.model.*

data class OptionalValue<V>(
    val value: V?,
    val absentReason: String?
) {
    companion object {
        fun <V> of(value: V): OptionalValue<V> = OptionalValue(value, null)
        fun <V> absent(reason: String): OptionalValue<V> = OptionalValue(null, reason)
    }
}

data class ExistingValues(
    val kafkaProfiles: List<KafkaProfile>,
    val clusterIdentifiers: List<KafkaClusterIdentifier>,
    val clusterRefs: List<ClusterRef>,
    val tagClusters: Map<Tag, List<KafkaClusterIdentifier>>,
    val commonTopicConfig: ExistingConfig,
    val topicConfigDoc: Map<String, String>,
    val brokerConfigDoc: Map<String, String>,
    val owners: List<String>,
    val producers: List<String>,
    val topics: List<TopicName>,
    val consumerGroups: List<ConsumerGroupId>,
    val users: List<KafkaUser>,
)

data class RuleViolation(
    val ruleClassName: String,
    val severity: Severity,
    val message: String,
    val placeholders: Map<String, Placeholder> = emptyMap()
) {
    enum class Severity {
        NONE, MINOR, WARNING, CRITICAL, ERROR,
    }
}

data class Placeholder(
    val key: String,
    val value: Any
)