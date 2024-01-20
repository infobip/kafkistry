package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.ConsumeApi
import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.service.consume.*
import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.service.consume.deserialize.JsonKafkaDeserializer
import com.infobip.kafkistry.service.consume.deserialize.KafkaDeserializer
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.webapp.url.ConsumeRecordsUrls.Companion.CONSUME
import com.infobip.kafkistry.webapp.url.ConsumeRecordsUrls.Companion.CONSUME_READ_TOPIC
import com.infobip.kafkistry.webapp.url.ConsumeRecordsUrls.Companion.CONSUME_READ_TOPIC_CONTINUE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*

@Controller
@RequestMapping("\${app.http.root-path}$CONSUME")
class ConsumeRecordsController(
    private val consumeApiOpt: Optional<ConsumeApi>,
    private val inspectApi: InspectApi,
    private val existingValuesApi: ExistingValuesApi,
    private val clusterEnabledFilter: ClusterEnabledFilter,
    private val kafkaDeserializers: List<KafkaDeserializer>,
) : BaseController() {

    private fun consumeApi(): ConsumeApi? = consumeApiOpt.orElse(null)

    private fun disabled() = ModelAndView("featureDisabled", mapOf(
        "featureName" to "Consume records",
        "propertyToEnable" to listOf("CONSUME_ENABLED", "app.consume.enabled")
    ))

    @GetMapping
    fun showConsumePage(
        @RequestParam(name = "topicName", required = false) topicName: TopicName?,
        @RequestParam(name = "clusterIdentifier", required = false) clusterIdentifier: KafkaClusterIdentifier?,
        @RequestParam(name = "numRecords", required = false) numRecords: Int?,
        @RequestParam(name = "maxWaitMs", required = false) maxWaitMs: Long?,
        @RequestParam(name = "waitStrategy", required = false) waitStrategy: WaitStrategy?,
        @RequestParam(name = "offsetType", required = false) offsetType: OffsetType?,
        @RequestParam(name = "offset", required = false) offset: Long?,
        @RequestParam(name = "partitions", required = false) partitions: String?,
        @RequestParam(name = "notPartitions", required = false) notPartitions: String?,
        @RequestParam(name = "keyDeserializerType", required = false) keyDeserializerType: DeserializerType?,
        @RequestParam(name = "valueDeserializerType", required = false) valueDeserializerType: DeserializerType?,
        @RequestParam(name = "headersDeserializerType", required = false) headersDeserializerType: DeserializerType?,
        @RequestParam(name = "readFilterJson", required = false) readFilterJson: String?,
        @RequestParam(name = "readOnlyCommitted", required = false) readOnlyCommitted: Boolean?,
        @RequestParam(name = "autoContinuation", required = false) autoContinuation: Boolean?,
        @RequestParam(name = "autoContinuationAfterEnd", required = false) autoContinuationAfterEnd: Boolean?,
    ): ModelAndView {
        if (consumeApi() == null) {
            return disabled()
        }
        readFilterJson?.takeIf { it.isNotBlank() }?.run {
            JsonKafkaDeserializer().deserialize(encodeToByteArray())
                ?: //prevent injection of arbitrary javascript
                throw IllegalArgumentException("Given recordFilter must be a valid json, got: '$readFilterJson'")
        }
        val clusterTopics = inspectApi.inspectTopicsOnClusters().associate { clusterTopics ->
            clusterTopics.cluster.identifier to (clusterTopics.statusPerTopics ?: emptyList())
                .filter { it.status.exists ?: false }
                .map { it.topicName }
        }
        val clusterIdentifiers = existingValuesApi.all().clusterRefs
            .filter { clusterEnabledFilter.enabled(it) }
            .map { it.identifier }
        return ModelAndView(
            "consume/consume", mapOf(
                "clusterTopics" to clusterTopics,
                "allClusters" to clusterIdentifiers,
                "topicName" to topicName,
                "clusterIdentifier" to clusterIdentifier,
                "numRecords" to numRecords,
                "maxWaitMs" to maxWaitMs,
                "waitStrategy" to waitStrategy,
                "offsetType" to offsetType,
                "offset" to offset,
                "partitions" to partitions,
                "notPartitions" to notPartitions,
                "availableDeserializerTypes" to kafkaDeserializers.map { it.typeName() },
                "keyDeserializerType" to keyDeserializerType,
                "valueDeserializerType" to valueDeserializerType,
                "headersDeserializerType" to headersDeserializerType,
                "readFilterJson" to readFilterJson,
                "readOnlyCommitted" to readOnlyCommitted,
                "autoContinuation" to autoContinuation,
                "autoContinuationAfterEnd" to autoContinuationAfterEnd,
            )
        )
    }

    @PostMapping(CONSUME_READ_TOPIC)
    fun showReadRecords(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody readConfig: ReadConfig
    ): ModelAndView {
        val consumeApi = consumeApi() ?: return disabled()
        val recordsResult = consumeApi.readTopic(clusterIdentifier, topicName, readConfig)
        return ModelAndView(
            "consume/records", mapOf(
                "recordsResult" to recordsResult
            )
        )
    }

    @PostMapping(CONSUME_READ_TOPIC_CONTINUE)
    fun showReadRecordsContinue(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("topicName") topicName: TopicName,
        @RequestBody continuedReadConfig: ContinuedReadConfig,
    ): ModelAndView {
        val consumeApi = consumeApi() ?: return disabled()
        val continuedRecordsResult = consumeApi.readTopicContinued(clusterIdentifier, topicName, continuedReadConfig)
        return ModelAndView(
            "consume/records", mapOf(
                "recordsResult" to continuedRecordsResult.recordsResult,
                "overallReadCount" to continuedRecordsResult.overallReadCount,
                "overallSkipCount" to continuedRecordsResult.overallSkipCount,
                "overallPartitions" to continuedRecordsResult.overallPartitions,
            )
        )
    }


}