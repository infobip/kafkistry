package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.model.*

object NamedTypeDoc {
    const val OK = "Everything is as expected"
    const val MISSING = "It should exist on kafka cluster but it is not found"
    const val UNEXPECTED =  "Is exists on kafka cluster but not in kafkistry"
    const val NOT_PRESENT_AS_EXPECTED = "It's configured not to exist on this cluster and it actually does not exist"
    const val UNKNOWN = "It exist on kafka cluster but not in kafkistry"
    const val CLUSTER_DISABLED = "Specific cluster is disabled by configuration and it is not attempted to read data from it"
    const val CLUSTER_UNREACHABLE = "Last attempt to collect metadata from cluster was not successful"
    const val UNAVAILABLE = "Requested combination of this resource + cluster is not available, meaning that it does not exist in registry nor it does not exist as UNKNOWN on specified cluster"
}

data class NamedTypeQuantity<T : NamedType, Q>(
    val type: T,
    val quantity: Q,
)

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
        NONE, MINOR, WARNING, ERROR, CRITICAL,
    }
}

data class Placeholder(
    val key: String,
    val value: Any
)

data class NamedTypeCauseDescription<T : NamedType>(
    val type: T,
    val message: String,
    val placeholders: Map<String, Placeholder> = emptyMap(),
)