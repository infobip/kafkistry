package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import com.infobip.kafkistry.service.consume.filter.KeyPathElement
import com.infobip.kafkistry.service.consume.filter.ListIndexPathElement
import com.infobip.kafkistry.service.consume.filter.MapKeyPathElement
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.RecordFieldType
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.JsonPathDef
import com.infobip.kafkistry.utils.ClusterTopicFilter
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class AnalyzeFilter(
    private val properties: RecordAnalyzerProperties
) {

    private val enabledOnFilter = ClusterTopicFilter(properties.enabledOn)
    private val valueSamplingEnabledOnFilter = ClusterTopicFilter(properties.valueSampling.enabledOn)

    private val jsonPathParser = JsonPathParser()

    private val includedFields = properties.valueSampling.includedFields.asNonEmptyJsonPaths()
    private val excludedFields = properties.valueSampling.excludedFields.asNonEmptyJsonPaths()

    private fun Set<JsonPathDef>.asNonEmptyJsonPaths(): JsonPaths? =
        takeIf { it.isNotEmpty() }
        ?.map { jsonPathParser.parseJsonKeyPath(it) }
        ?.fold(JsonPaths()) { acc: JsonPaths, path: List<KeyPathElement> -> acc.apply { add(path) } }

    fun shouldAnalyze(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName,
    ): Boolean = enabledOnFilter(clusterIdentifier, topicName)

    fun shouldSampleValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
    ): Boolean = properties.valueSampling.enabled && valueSamplingEnabledOnFilter(clusterIdentifier, topicName)

    fun shouldSampleValuesForPath(
        jsonPath: List<Pair<RecordFieldType, String?>>,
    ): Boolean {
        if (includedFields != null && jsonPath !in includedFields) {
            return false
        }
        if (excludedFields != null && jsonPath in excludedFields) {
            return false
        }
        return true
    }

    class JsonPaths {

        private var leaf = false
        private val tree = ConcurrentHashMap<KeyPathElement, JsonPaths>()

        fun add(path: List<KeyPathElement>) {
            if (path.isEmpty()) {
                leaf = true
                return
            }
            tree.computeIfAbsent(path.first()) { JsonPaths() }.add(path.subList(1, path.size))
        }

        operator fun contains(jsonPath: List<Pair<RecordFieldType, String?>>): Boolean {
            if (jsonPath.isEmpty()) {
                return leaf
            }
            val keyCandidates = jsonPath.first().let { (_, name) ->
                listOfNotNull(
                    name?.let { MapKeyPathElement(it) },
                    MapKeyPathElement(null),
                    ListIndexPathElement(null)
                )
            }
            val subPath = jsonPath.subList(1, jsonPath.size)
            return keyCandidates.any { key ->
                val subTree = tree[key] ?: return@any false
                subPath in subTree
            }
        }
    }

}