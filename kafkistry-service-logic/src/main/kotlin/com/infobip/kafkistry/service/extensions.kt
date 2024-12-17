package com.infobip.kafkistry.service

import com.infobip.kafkistry.model.*

fun <T, R : Any> Iterable<T>.mostFrequentElement(extractor: (T) -> R?): R? =
        this.mapNotNull(extractor)
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

fun List<ClusterRef>.computePresence(
    presentOnClusters: List<KafkaClusterIdentifier>,
    disabledClusters: List<KafkaClusterIdentifier> = emptyList()
): Presence {
    val presentAndDisabledSet = (presentOnClusters + disabledClusters).toSet()
    val includedClusters = presentAndDisabledSet.toList()
    val allClusterIdentifiers = map { it.identifier }

    if (includedClusters.containsAll(allClusterIdentifiers)) {
        return Presence.ALL
    }

    val allTags = flatMap { it.tags }.distinct()
    val tagClusters = allTags.associateWith { tag ->
        filter { tag in it.tags }.map { it.identifier }.toSet()
    }
    val presentSet = presentOnClusters.toSet()
    tagClusters.filterValues { it == presentSet }.keys.firstOrNull()?.run {
        return Presence(PresenceType.TAGGED_CLUSTERS, tag = this)
    }
    tagClusters.filterValues { it == presentAndDisabledSet }.keys.firstOrNull()?.run {
        return Presence(PresenceType.TAGGED_CLUSTERS, tag = this)
    }

    return if (includedClusters.size <= allClusterIdentifiers.size / 2) {
        Presence(PresenceType.INCLUDED_CLUSTERS, includedClusters)
    } else {
        Presence(PresenceType.EXCLUDED_CLUSTERS, allClusterIdentifiers.filter { it !in includedClusters })
    }
}

fun Presence.withClusterIncluded(clusterRef: ClusterRef): Presence {
    return if (needToBeOnCluster(clusterRef)) {
        return this
    } else {
        when (type) {
            PresenceType.ALL_CLUSTERS -> this
            PresenceType.INCLUDED_CLUSTERS -> copy(kafkaClusterIdentifiers = kafkaClusterIdentifiers?.plus(clusterRef.identifier))
            PresenceType.EXCLUDED_CLUSTERS -> copy(kafkaClusterIdentifiers = kafkaClusterIdentifiers?.minus(clusterRef.identifier))
            PresenceType.TAGGED_CLUSTERS -> throw KafkistryIllegalStateException(
                "Can't suggest fix of configuration by changing topic's config, topic presence is $this, " +
                        "cluster '${clusterRef.identifier}' should be tagged with '$tag', currently it's only tagged with ${clusterRef.tags}"
            )
        }
    }
}


fun <K> Iterable<Map<K, Long>>.sumPerValue(): Map<K, Long> = reducePerValue { v1, v2 -> v1 + v2}

fun <K, V : Any> Iterable<Map<K, V>>.reducePerValue(reduce: (V, V) -> V): Map<K, V> {
    val result = mutableMapOf<K, V>()
    forEach { map ->
        map.forEach { (k, v) ->
            result.merge(k, v) { v1, v2 -> reduce(v1, v2) }
        }
    }
    return result
}

private val RULE_VIOLATION_PLACEHOLDER_REGEX = Regex("%(\\w+)%")

private fun String.renderMessage(placeholders: Map<String, Placeholder>): String {
    return RULE_VIOLATION_PLACEHOLDER_REGEX.replace(this) { match ->
        val key = match.groupValues[1]
        placeholders[key]?.value.toString()
    }
}

fun RuleViolation.renderMessage(): String = message.renderMessage(placeholders)
fun NamedTypeCauseDescription<*>.renderMessage(): String = message.renderMessage(placeholders)

fun <K> Map<K, Int>.sortedByValueDescending(): Map<K, Int> = sortedDescending { it.value }

fun <K, V, C : Comparable<C>> Map<K, V>.sortedDescending(
    by: (Map.Entry<K, V>) -> C
): Map<K, V> = entries.sortedByDescending { by(it) }.associate { it.toPair() }

fun <K> Grouping<*, K>.eachCountDescending(): Map<K, Int> = eachCount().sortedByValueDescending()

fun <T : Any> List<T>.filterEnabled(enabled: List<Class<in T>>): List<T> {
    if (enabled.isEmpty()) return this
    return filter { it.javaClass in enabled }
}

fun <T : Any> List<T>.filterDisabled(disabled: List<Class<in T>>): List<T> {
    if (disabled.isEmpty()) return this
    return filter { it.javaClass !in disabled }
}
