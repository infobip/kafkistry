package com.infobip.kafkistry.service

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.AclInspectionResultType.*
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.validation.rules.Placeholder
import com.infobip.kafkistry.service.topic.validation.rules.RuleViolation

fun <T, R : Any> Iterable<T>.mostFrequentElement(extractor: (T) -> R?): R? =
        this.mapNotNull(extractor)
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

fun List<ClusterRef>.clustersTags(): Map<Set<KafkaClusterIdentifier>, List<Tag>> {
    val allTags = flatMap { it.tags }.distinct()
    return allTags.groupBy { tag ->
        filter { tag in it.tags }.map { it.identifier }.toSet()
    }
}

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

fun AclRule.toKafkaAclRule(principal: PrincipalId) = KafkaAclRule(
        principal = principal,
        host = host,
        resource = resource,
        operation = operation
)

fun KafkaAclRule.toAclRule(presence: Presence) = AclRule(
        presence = presence,
        host = host,
        resource = resource,
        operation = operation
)

fun PrincipalAclsInspection.transpose(): PrincipalAclsClustersPerRuleInspection {
    val ruleStatuses = clusterInspections
            .flatMap { clusterStatuses -> clusterStatuses.statuses.map { clusterStatuses.clusterIdentifier to it } }
            .groupBy { (_, ruleStatus) -> ruleStatus.rule }
            .map { (rule, statuses) ->
                AclRuleClustersInspection(
                        aclRule = rule,
                        clusterStatuses = statuses.associate { it },
                        status = AclStatus.from(statuses.map { (_, ruleStatus) -> ruleStatus }),
                        availableOperations = statuses.mergeAvailableOps { it.second.availableOperations }
                )
            }
    val clusterAffectingQuotaEntities = clusterInspections.associate {
        it.clusterIdentifier to it.affectingQuotaEntities
    }
    return PrincipalAclsClustersPerRuleInspection(
            principal = principal,
            principalAcls = principalAcls,
            statuses = ruleStatuses,
            status = ruleStatuses.map { it.status }.aggregate(),
            availableOperations = availableOperations,
            clusterAffectingQuotaEntities = clusterAffectingQuotaEntities,
            affectingQuotaEntities = affectingQuotaEntities,
    )
}

fun <T> Iterable<T>.mergeAvailableOps(extractor: (T) -> List<AvailableAclOperation>): List<AvailableAclOperation> =
        flatMap(extractor).distinct().sorted()

fun AclInspectionResultType.availableOperations(
        principalExists: Boolean
): List<AvailableAclOperation> =
        when (this) {
            OK, NOT_PRESENT_AS_EXPECTED, CLUSTER_UNREACHABLE, SECURITY_DISABLED, CLUSTER_DISABLED, UNAVAILABLE -> emptyList()
            MISSING -> listOf(AvailableAclOperation.CREATE_MISSING_ACLS, AvailableAclOperation.EDIT_PRINCIPAL_ACLS)
            UNEXPECTED -> listOf(AvailableAclOperation.DELETE_UNWANTED_ACLS, AvailableAclOperation.EDIT_PRINCIPAL_ACLS)
            UNKNOWN -> listOf(
                    AvailableAclOperation.DELETE_UNWANTED_ACLS,
                    if (principalExists) {
                        AvailableAclOperation.EDIT_PRINCIPAL_ACLS
                    } else {
                        AvailableAclOperation.IMPORT_PRINCIPAL
                    }
            )
        }


private val RULE_VIOLATION_REGEX = Regex("%(\\w+)%")

private fun String.renderMessage(placeholders: Map<String, Placeholder>): String {
    return RULE_VIOLATION_REGEX.replace(this) { match ->
        val key = match.groupValues[1]
        placeholders[key]?.value.toString()
    }
}

fun RuleViolation.renderMessage(): String = message.renderMessage(placeholders)
fun RuleViolationIssue.renderMessage(): String = message.renderMessage(placeholders)

fun Iterable<DataMigration>.merge() = DataMigration(
        reAssignedPartitions = sumOf { it.reAssignedPartitions },
        totalIOBytes = sumOf { it.totalIOBytes },
        totalAddBytes = sumOf { it.totalAddBytes },
        totalReleaseBytes = sumOf { it.totalReleaseBytes },
        perBrokerTotalIOBytes = map { it.perBrokerTotalIOBytes }.sumPerValue(),
        perBrokerInputBytes = map { it.perBrokerInputBytes }.sumPerValue(),
        perBrokerOutputBytes = map { it.perBrokerOutputBytes }.sumPerValue(),
        perBrokerReleasedBytes = map { it.perBrokerReleasedBytes }.sumPerValue(),
        maxBrokerIOBytes = 0L
).run {
    copy(maxBrokerIOBytes = (perBrokerInputBytes.values + perBrokerOutputBytes.values).maxOrNull() ?: 0L)
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