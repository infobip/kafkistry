package com.infobip.kafkistry.service.generator

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.topic.propertiesForCluster
import com.infobip.kafkistry.service.quotas.quotaForCluster
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.service.tags.ClusterTagClassifier
import org.springframework.stereotype.Component

/**
 * purpose of this minimizer is to create new but equivalent TopicDescription.
 * Main goal is to reduce number of overrides by extracting most common properties into
 * global section.
 */
@Component
class OverridesMinimizer(
    private val clusterTagClassifier: ClusterTagClassifier,
) {

    fun minimizeOverrides(topic: TopicDescription, allClusterRefs: List<ClusterRef>): TopicDescription {
        val minimizedProperties = minimizeProperties(allClusterRefs, topic.properties) {
            topic.propertiesForCluster(it)
        }
        val minimizedConfigs = minimizeConfig(allClusterRefs, topic)
        return topic.copy(
                properties = minimizedProperties.base,
                perClusterProperties = minimizedProperties.clusterOverrides,
                perTagProperties = minimizedProperties.tagOverrides,
                config = minimizedConfigs.base,
                perClusterConfigOverrides = minimizedConfigs.clusterOverrides,
                perTagConfigOverrides = minimizedConfigs.tagOverrides,
        )
    }

    fun minimizeOverrides(quotaDescription: QuotaDescription, allClusterRefs: List<ClusterRef>): QuotaDescription {
        val minimizedProperties = minimizeProperties(allClusterRefs, quotaDescription.properties) {
            quotaDescription.quotaForCluster(it)
        }
        return quotaDescription.copy(
            properties = minimizedProperties.base,
            clusterOverrides = minimizedProperties.clusterOverrides,
            tagOverrides = minimizedProperties.tagOverrides,
        )
    }

    private fun <P> minimizeProperties(
        allClusterRefs: List<ClusterRef>, globalProperties: P, propertiesForCluster: (ClusterRef) -> P,
    ): BaseAndOverrides<P> {
        val clustersProperties = allClusterRefs.associateWith { propertiesForCluster(it) }
        val propertiesCounts = clustersProperties.values
                .groupingBy { it }
                .eachCount()
        val maxPropertiesFreq = propertiesCounts.values.maxOrNull()
                ?: return BaseAndOverrides(globalProperties, emptyMap(), emptyMap())
        val topFrequentProperties = propertiesCounts.filterValues { it == maxPropertiesFreq }.map { it.key }
        val finalProperties = if (globalProperties in topFrequentProperties) {
            globalProperties
        } else {
            topFrequentProperties.first()
        }
        val perClusterProperties = clustersProperties
            .filterValues { it != finalProperties }
            .mapKeys { it.key.identifier }

        val clustersTags = allClusterRefs.clustersTags()
        val propertiesClusters = perClusterProperties.map { it }
            .groupBy ({ it.value }, { it.key })
            .mapValues { (_, values) -> values.toSet() }
        val propertiesTags = propertiesClusters.mapValues { clustersTags[it.value] }
        val unmatchedPerClusterProperties = perClusterProperties.filterValues {
            propertiesTags[it] == null   //keep only the ones which will not be overridden by tag
        }
        val tagProperties = propertiesTags.mapNotNull { (properties, tags) ->
            tags?.firstOrNull()?.let { it to properties }
        }.toMap()

        val partiallyCoveredClusters = mutableSetOf<KafkaClusterIdentifier>()
        val partialTagProperties = propertiesClusters
            .filterKeys { it != finalProperties }   //don't take global default
            .filterValues { clusters ->
                clusters.all { it in unmatchedPerClusterProperties.keys }
            }
            .mapNotNull { (properties, clusters) ->
                clustersTags.entries
                    .filter { it.value.isNotEmpty() && clusters.containsAll(it.key) }
                    .map { (tagClusters, tags) -> tags.first() to tagClusters }
                    .maxByOrNull { it.second.size }    //pick tag covering as much as possible
                    ?.let {
                        partiallyCoveredClusters.addAll(it.second)
                        it.first to properties
                    }
            }
            .toMap()

        return BaseAndOverrides(
            base = finalProperties,
            clusterOverrides = unmatchedPerClusterProperties.filterKeys { it !in partiallyCoveredClusters },
            tagOverrides = tagProperties + partialTagProperties,
        )
    }

    private fun minimizeConfig(allClusterRefs: List<ClusterRef>, topic: TopicDescription): BaseAndOverrides<TopicConfigMap> {
        val clustersConfigs = allClusterRefs.associate { it.identifier to topic.configForCluster(it) }
        val commonConfigKeys = clustersConfigs.values
                .map { it.keys }
                .takeIf { it.isNotEmpty() }
                ?.reduce { s1, s2 -> s1.intersect(s2) }
                ?: topic.config.keys
        val finalBaseConfig = commonConfigKeys.associateWith { key ->
            val valueCounts = clustersConfigs.values
                    .mapNotNull { it[key] }
                    .groupingBy { it }
                    .eachCount()
            val maxValueFreq = valueCounts.values.maxOrNull()
                ?: return BaseAndOverrides(topic.config, emptyMap(), emptyMap())
            val topFrequentValues = valueCounts.filterValues { it == maxValueFreq }.map { it.key }
            val currentBaseValue = topic.config[key]
            val finalValue = if (currentBaseValue != null && currentBaseValue in topFrequentValues) {
                currentBaseValue
            } else {
                topFrequentValues.first()
            }
            finalValue
        }
        val perClusterEntries = clustersConfigs
                .mapValues { (_, clusterConfig) ->
                    clusterConfig.filter { (key, value) -> value != finalBaseConfig[key] }
                }
                .flatMap { (cluster, configs) ->
                    configs.map { Triple(cluster, it.key, it.value) }
                }

        val clustersTags = allClusterRefs.clustersTags()
        val entryClusters = perClusterEntries
            .groupBy({ it.second to it.third }, { it.first })
            .mapValues { it.value.toSet() }
        val entryTags = entryClusters.mapValues { clustersTags[it.value] }
        val unmatchedPerClusterOverrides = perClusterEntries
            .filter {
                entryTags[it.second to it.third] == null   //keep only the ones which will ot be overridden by tag
            }
            .groupBy { it.first }
            .mapValues { (_, entries) ->
                entries.associate { it.second to it.third }
            }
        val tagOverrides = entryTags
            .mapNotNull { (entry, tags) ->
                tags?.firstOrNull()?.let { it to entry }
            }
            .groupBy({ it.first },{it.second})
            .mapValues { (_, entries) -> entries.toMap() }

        val partiallyCoveredClusterEntries = mutableSetOf<Pair<KafkaClusterIdentifier, String>>()
        val partialTagConfigs = entryClusters
            .filterKeys { it.first !in finalBaseConfig.keys }   //don't take global default
            .filterValues { clusters ->
                clusters.all { it in unmatchedPerClusterOverrides.keys }
            }
            .mapNotNull { (entry, clusters) ->
                clustersTags.entries
                    .filter { it.value.isNotEmpty() && clusters.containsAll(it.key) }
                    .map { (tagClusters, tags) -> tags.first() to tagClusters }
                    .maxByOrNull { it.second.size }    //pick tag covering as much as possible
                    ?.let { (tag, clusters) ->
                        partiallyCoveredClusterEntries.addAll(clusters.map { it to entry.first })
                        tag to entry
                    }
            }
            .groupBy({ it.first },{it.second})
            .mapValues { (_, entries) -> entries.toMap() }

        return BaseAndOverrides(
            base = finalBaseConfig,
            clusterOverrides = unmatchedPerClusterOverrides
                .mapValues { (cluster, configs) ->
                    configs.filterKeys { (cluster to it) !in partiallyCoveredClusterEntries }
                }
                .filterValues { it.isNotEmpty() },
            tagOverrides = tagOverrides mergeWith partialTagConfigs,
        )
    }

    private fun List<ClusterRef>.clustersTags(): Map<Set<KafkaClusterIdentifier>, List<Tag>> {
        val allOverrideTags = flatMap { it.tags }
            .distinct()
            .filter { clusterTagClassifier.isOverride(it) }
        return allOverrideTags.groupBy { tag ->
            filter { tag in it.tags }.map { it.identifier }.toSet()
        }
    }

    private infix fun Map<Tag, TopicConfigMap>.mergeWith(other: Map<Tag, TopicConfigMap>): Map<Tag, TopicConfigMap> {
        return entries.plus(other.entries)
            .groupBy ({ it.key }, {it.value})
            .mapValues { (_, values) ->
                if (values.size == 1) values[0] else values[0].plus(values[1])
            }
    }

    private data class BaseAndOverrides<T>(
        val base: T,
        val clusterOverrides: Map<KafkaClusterIdentifier, T>,
        val tagOverrides: Map<Tag, T>,
    )
}