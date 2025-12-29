package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.api.ProduceApi
import com.infobip.kafkistry.api.RecordsStructureApi
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PayloadType
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.produce.ProduceRequest
import com.infobip.kafkistry.service.produce.config.ProduceProperties
import com.infobip.kafkistry.service.produce.serialize.ValueSerializer
import com.infobip.kafkistry.webapp.url.ProduceRecordsUrls.Companion.PRODUCE
import com.infobip.kafkistry.webapp.url.ProduceRecordsUrls.Companion.PRODUCE_SEND_RECORD
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*

@Controller
@RequestMapping("\${app.http.root-path}$PRODUCE")
class ProduceRecordsController(
    private val produceApiOpt: Optional<ProduceApi>,
    private val recordsStructureApiOpt: Optional<RecordsStructureApi>,
    private val produceProperties: ProduceProperties,
    private val inspectApi: InspectApi,
    private val existingValuesApi: ExistingValuesApi,
    private val clusterEnabledFilter: ClusterEnabledFilter,
    private val valueSerializers: List<ValueSerializer>,
    private val sampleDataExtractor: SampleDataExtractor,
) : BaseController() {

    private fun produceApi(): ProduceApi? = produceApiOpt.orElse(null)

    private fun disabled() = ModelAndView("featureDisabled", mapOf(
        "featureName" to "Produce records",
        "propertyToEnable" to listOf("PRODUCE_ENABLED", "app.produce.enabled")
    ))

    @GetMapping
    fun showProducePage(
        @RequestParam(name = "topicName", required = false) topicName: TopicName?,
        @RequestParam(name = "clusterIdentifier", required = false) clusterIdentifier: KafkaClusterIdentifier?,
    ): ModelAndView {
        if (produceApi() == null) {
            return disabled()
        }
        val clusterTopics = inspectApi.inspectTopicsOnClusters().associate { clusterTopics ->
            clusterTopics.cluster.identifier to (clusterTopics.statusPerTopics ?: emptyList())
                .filter { it.topicClusterStatus.status.exists ?: false }
                .map { it.topicName }
        }
        val clusterIdentifiers = existingValuesApi.all().clusterRefs
            .filter { clusterEnabledFilter.enabled(it) }
            .map { it.identifier }

        // Detect default serializers from RecordStructure analysis if available
        val (defaultKeySerializer, defaultValueSerializer) = detectDefaultSerializers(clusterIdentifier, topicName)

        // Extract sample data from RecordStructure if available
        val sampleData = sampleDataExtractor.extractSampleData(clusterIdentifier, topicName)

        return ModelAndView(
            "produce/produce", mapOf(
                "clusterTopics" to clusterTopics,
                "allClusters" to clusterIdentifiers,
                "topicName" to topicName,
                "clusterIdentifier" to clusterIdentifier,
                "availableSerializerTypes" to valueSerializers.map { it.type },
                "defaultKeySerializer" to defaultKeySerializer,
                "defaultValueSerializer" to defaultValueSerializer,
                "defaultHeaderSerializer" to produceProperties.defaultHeaderSerializer(),
                "sampleKey" to sampleData.key,
                "sampleValue" to sampleData.value,
                "sampleHeaders" to sampleData.headers,
            )
        )
    }

    private fun detectDefaultSerializers(
        clusterIdentifier: KafkaClusterIdentifier?,
        topicName: TopicName?
    ): Pair<String, String> {
        // If cluster and topic are specified, try to detect from RecordStructure
        if (clusterIdentifier != null && topicName != null) {
            recordsStructureApiOpt.orElse(null)?.let { api ->
                val structure = api.structureOfTopicOnCluster(topicName, clusterIdentifier)
                if (structure != null) {
                    val valueSerializer = when (structure.payloadType) {
                        PayloadType.JSON -> "JSON"
                        PayloadType.NULL -> produceProperties.defaultValueSerializer()
                        PayloadType.UNKNOWN -> produceProperties.defaultValueSerializer()
                    }
                    // Key is typically STRING unless we have key-specific analysis in the future
                    return produceProperties.defaultKeySerializer() to valueSerializer
                }
            }
        }

        // Fall back to configured defaults
        return produceProperties.defaultKeySerializer() to produceProperties.defaultValueSerializer()
    }

    @GetMapping("/topic-metadata")
    @ResponseBody
    fun getTopicMetadata(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName
    ): TopicProduceMetadata {
        // Detect default serializers
        val (defaultKeySerializer, defaultValueSerializer) = detectDefaultSerializers(clusterIdentifier, topicName)

        // Extract sample data
        val sampleData = sampleDataExtractor.extractSampleData(clusterIdentifier, topicName)

        // Get partition count
        val partitionCount = try {
            inspectApi.inspectTopicOnCluster(topicName, clusterIdentifier)
                .existingTopicInfo
                ?.properties
                ?.partitionCount
        } catch (_: Exception) {
            null
        }

        return TopicProduceMetadata(
            defaultKeySerializer = defaultKeySerializer,
            defaultValueSerializer = defaultValueSerializer,
            sampleKey = sampleData.key,
            sampleValue = sampleData.value,
            sampleHeaders = sampleData.headers,
            partitionCount = partitionCount,
        )
    }

    @PostMapping(PRODUCE_SEND_RECORD)
    fun sendRecord(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody produceRequest: ProduceRequest
    ): ModelAndView {
        val produceApi = produceApi() ?: return disabled()
        val result = produceApi.sendRecord(clusterIdentifier, topicName, produceRequest)
        return ModelAndView(
            "produce/result", mapOf(
                "result" to result,
                "topicName" to topicName,
                "clusterIdentifier" to clusterIdentifier
            )
        )
    }
}

data class TopicProduceMetadata(
    val defaultKeySerializer: String,
    val defaultValueSerializer: String,
    val sampleKey: String?,
    val sampleValue: String?,
    val sampleHeaders: List<SampleHeader>,
    val partitionCount: Int?
)
