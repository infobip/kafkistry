package com.infobip.kafkistry.service.consume.masking

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.JsonPathDef

interface RecordMaskingRuleProvider {

    fun maskingSpecFor(
        topic: TopicName, clusterRef: ClusterRef
    ): List<TopicMaskingSpec>
}

class TopicMaskingSpec(
    val keyPathDefs: Set<JsonPathDef>,
    val valuePathDefs: Set<JsonPathDef>,
    val headerPathDefs: Map<String, Set<JsonPathDef>>,
) {

    companion object {
        val NONE = TopicMaskingSpec(emptySet(), emptySet(), emptyMap())
    }

    fun merge(other: TopicMaskingSpec): TopicMaskingSpec {
        return TopicMaskingSpec(
            keyPathDefs = keyPathDefs + other.keyPathDefs,
            valuePathDefs = valuePathDefs + other.valuePathDefs,
            headerPathDefs = (headerPathDefs.keys + other.headerPathDefs.keys).associateWith {
                headerPathDefs[it].orEmpty() + other.headerPathDefs[it].orEmpty()
            },
        )
    }
}