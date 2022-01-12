package com.infobip.kafkistry.service.topic.compare

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.InspectionResultType

data class ComparingSubject(
    val topicName: TopicName?,
    val onClusterIdentifier: KafkaClusterIdentifier?,
    val type: ComparingSubjectType?
)

enum class ComparingSubjectType {
    ACTUAL, CONFIGURED
}

data class ComparingRequest(
        val source: ComparingSubject,
        val targets: List<ComparingSubject>
)

data class ComparingResult(
        val inspectionStatusTypes: TopicInspectionStatusTypes,
        val partitionCount: ComparedProperty,
        val replicationFactor: ComparedProperty,
        val config: Map<String, ComparedProperty>
)

data class ComparedProperty(
        val source: ComparedValue,
        val targets: List<ComparedValue>
)

data class ComparedValue(
        val value: String? = null,
        val default: Boolean = false,
        val configDefined: Boolean = false,
        val status: PropertyStatus
) {
    companion object {
          fun notPresent() = ComparedValue(null, false, false, PropertyStatus.NOT_PRESENT)
    }
}

data class TopicInspectionStatusTypes(
        val sourceTypes: List<InspectionResultType>,
        val targetsTypes: List<List<InspectionResultType>>
)

enum class PropertyStatus {
    NOT_PRESENT,
    MISMATCH,
    MATCHING,
    HAS_MISMATCH,
    UNDEFINED
}