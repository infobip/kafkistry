package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.RecordFieldType
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.JsonPathDef
import com.infobip.kafkistry.service.consume.filter.*
import com.infobip.kafkistry.utils.ClusterTopicFilter
import org.springframework.stereotype.Component

@Component
class AnalyzeFilter(
    private val properties: RecordAnalyzerProperties
) {

    private val enabledOnFilter = ClusterTopicFilter(properties.enabledOn)
    private val valueSamplingEnabledOnFilter = ClusterTopicFilter(properties.valueSampling.enabledOn)

    private val jsonPathParser = JsonPathParser()

    private val includedFields = properties.valueSampling.includedFields.asNonEmptyJsonPaths()
    private val excludedFields = properties.valueSampling.excludedFields.asNonEmptyJsonPaths()

    private fun Set<JsonPathDef>.asNonEmptyJsonPaths(): JsonPathsTree? = this
        .takeIf { it.isNotEmpty() }
        ?.let { jsonPathParser.parseAsTree(it) }

    fun shouldAnalyze(
        clusterRef: ClusterRef, topicName: TopicName,
    ): Boolean = enabledOnFilter(clusterRef, topicName)

    fun shouldSampleValues(
        clusterRef: ClusterRef, topicName: TopicName,
    ): Boolean = properties.valueSampling.enabled && valueSamplingEnabledOnFilter(clusterRef, topicName)

    fun shouldSampleValuesForPath(
        jsonPath: List<Pair<RecordFieldType, String?>>,
    ): Boolean {
        val pathParts = jsonPath.map { it.second }
        if (includedFields != null && pathParts !in includedFields) {
            return false
        }
        if (excludedFields != null && pathParts in excludedFields) {
            return false
        }
        return true
    }

}