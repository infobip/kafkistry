package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.model.*

interface NamedType {
    val name: String
    val level: StatusLevel
    val valid: Boolean
}

data class NamedTypeQuantity<T : NamedType, Q>(
    val type: T,
    val quantity: Q,
)

enum class StatusLevel {
    SUCCESS,
    IMPORTANT,
    IGNORE,
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}

interface SubjectStatus<T : NamedType> {
    val ok: Boolean
    val statusCounts: List<NamedTypeQuantity<T, Int>>

    companion object {
        fun <T : NamedType, S : SubjectStatus<T>> from(
            statuses: List<T>,
            constructor: (ok: Boolean, statusCounts: List<NamedTypeQuantity<T, Int>>) -> S
        ): S = constructor(
            statuses.map { it.valid }.fold(true, Boolean::and),
            statuses.groupingBy { it }
                .eachCountDescending()
                .map { NamedTypeQuantity(it.key, it.value) }
        )
    }
}

fun <T : NamedType, S : SubjectStatus<T>> S.merge(
    other: S,
    constructor: (ok: Boolean, statusCounts: List<NamedTypeQuantity<T, Int>>) -> S
): S = constructor(
    ok && other.ok,
    (statusCounts.asSequence() + other.statusCounts.asSequence())
        .groupBy({ it.type }, { it.quantity })
        .mapValues { (_, counts) -> counts.sum() }
        .sortedByValueDescending()
        .map { NamedTypeQuantity(it.key, it.value) }
)

fun <T : NamedType, S : SubjectStatus<T>> Iterable<S>.aggregateStatusTypes(
    empty: S,
    constructor: (ok: Boolean, statusCounts: List<NamedTypeQuantity<T, Int>>) -> S
): S = fold(empty) { s1,s2-> s1.merge(s2, constructor) }

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