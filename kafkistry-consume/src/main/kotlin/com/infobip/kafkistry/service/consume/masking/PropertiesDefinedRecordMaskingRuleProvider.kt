package com.infobip.kafkistry.service.consume.masking

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.JsonPathDef
import com.infobip.kafkistry.utils.ClusterTopicFilter
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap


@Component
@ConfigurationProperties("app.masking")
class RecordMaskingRulesProperties {

    @NestedConfigurationProperty
    var rules: Map<String, RecordMaskingRuleProperties> = mutableMapOf()
}

class RecordMaskingRuleProperties {
    var target = ClusterTopicFilterProperties()
    var keyJsonPaths: List<JsonPathDef> = mutableListOf()
    var valueJsonPaths: List<JsonPathDef> = mutableListOf()
    var headersJsonPaths: Map<String, List<JsonPathDef>> = mutableMapOf()
}

@Component
class PropertiesDefinedRecordMaskingRuleProvider(
    properties: RecordMaskingRulesProperties,
) : RecordMaskingRuleProvider {

    private data class TargetSpec(
        val clusterTopicFilter: ClusterTopicFilter,
        val maskingSpec: TopicMaskingSpec,
    )

    private val specs: List<TargetSpec> = properties.rules.values.map { rule ->
        TargetSpec(
            clusterTopicFilter = ClusterTopicFilter(rule.target),
            maskingSpec = TopicMaskingSpec(
                keyPathDefs = rule.keyJsonPaths.toSet(),
                valuePathDefs = rule.valueJsonPaths.toSet(),
                headerPathDefs = rule.headersJsonPaths.mapValues { it.value.toSet() },
            )
        )
    }

    private val topicClusterSpecs = ConcurrentHashMap<Pair<TopicName, ClusterRef>, List<TopicMaskingSpec>>()

    override fun maskingSpecFor(topic: TopicName, clusterRef: ClusterRef): List<TopicMaskingSpec> {
        return topicClusterSpecs.computeIfAbsent(topic to clusterRef) {
            specs.filter { it.clusterTopicFilter.filter(clusterRef, topic) }.map { it.maskingSpec }
        }
    }

}