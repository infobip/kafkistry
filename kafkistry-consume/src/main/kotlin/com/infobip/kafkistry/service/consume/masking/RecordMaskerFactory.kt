package com.infobip.kafkistry.service.consume.masking

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import com.infobip.kafkistry.service.consume.filter.JsonPathsTree
import com.infobip.kafkistry.service.consume.filter.parseAsTree
import org.springframework.stereotype.Component

@Component
class RecordMaskerFactory(
    private val maskingRulesProviders: List<RecordMaskingRuleProvider>,
    private val jsonPathParser: JsonPathParser,
) {

    fun createMaskerFor(topic: TopicName, clusterRef: ClusterRef): RecordMasker {
        val maskingSpec = maskingRulesProviders
            .flatMap { it.maskingSpecFor(topic, clusterRef) }
            .fold(TopicMaskingSpec.NONE, TopicMaskingSpec::merge)
        if (maskingSpec == TopicMaskingSpec.NONE) {
            return RecordMasker.NOOP
        }
        val keyTrees = maskingSpec.keyPathDefs.takeIf { it.isNotEmpty() }?.let { jsonPathParser.parseAsTree(it) }
        val valueTrees = maskingSpec.valuePathDefs.takeIf { it.isNotEmpty() }?.let { jsonPathParser.parseAsTree(it) }
        val headersTrees = maskingSpec.headerPathDefs.mapValues { jsonPathParser.parseAsTree(it.value) }
        if (keyTrees == null && valueTrees == null && headersTrees.isEmpty()) {
            return RecordMasker.NOOP
        }
        return RecordMaskerImpl(keyTrees, valueTrees, headersTrees)
    }
}

class RecordMaskerImpl(
    private val keyTrees: JsonPathsTree?,
    private val valueTrees: JsonPathsTree?,
    private val headersTrees: Map<String, JsonPathsTree>,
) : RecordMasker {

    override fun masksKey(): Boolean = keyTrees != null
    override fun masksValue(): Boolean = valueTrees != null
    override fun masksHeader(name: String): Boolean = headersTrees[name] != null

    override fun maskKey(key: Any?): Any? = key.maskWith(keyTrees)
    override fun maskValue(value: Any?): Any? = value.maskWith(valueTrees)
    override fun maskHeader(name: String, header: Any?): Any? = header.maskWith(headersTrees[name])

    private fun Any?.maskWith(jsonPathsTree: JsonPathsTree?): Any? {
        return jsonPathsTree?.replace(this) { "***MASKED***" } ?: this
    }
}