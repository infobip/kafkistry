package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.service.consume.JsonPathDef
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConfigurationProperties(prefix = "app.record-analyzer")
class RecordAnalyzerProperties {

    var enabled = true
    var storageDir = ""

    var timeWindow: Long = TimeUnit.DAYS.toMillis(1)
    var cardinalityDiffThreshold = 5
    var cardinalityMagnitudeFactorThreshold = 10
    var cardinalityRequiringCommonFields = false

    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()

    @NestedConfigurationProperty
    var executor = ExecutorProperties()

    @NestedConfigurationProperty
    var valueSampling = ValueSamplingProperties()

    class ExecutorProperties {
        var trimAndDumpRate = TimeUnit.MINUTES.toMillis(2)
        var concurrency = 1
        var maxQueueSize = 20_000
    }

    class ValueSamplingProperties {
        var enabled = true
        var maxCardinality = 25
        var maxStringLength = 50
        var maxNumberAbs = 1_000_000L

        @NestedConfigurationProperty
        var enabledOn = ClusterTopicFilterProperties()

        var includedFields: Set<JsonPathDef> = emptySet()
        var excludedFields: Set<JsonPathDef> = emptySet()
    }
}