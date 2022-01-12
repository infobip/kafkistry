package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.RecordsStructureApi
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.recordstructure.AnalyzeRecords
import com.infobip.kafkistry.webapp.url.RecordsStructureUrls.Companion.RECORDS_STRUCTURE
import com.infobip.kafkistry.webapp.url.RecordsStructureUrls.Companion.RECORDS_STRUCTURE_DRY_RUN
import com.infobip.kafkistry.webapp.url.RecordsStructureUrls.Companion.RECORDS_STRUCTURE_DRY_RUN_INSPECT
import com.infobip.kafkistry.webapp.url.RecordsStructureUrls.Companion.RECORDS_STRUCTURE_TOPIC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*

@Controller
@RequestMapping("\${app.http.root-path}$RECORDS_STRUCTURE")
class RecordsStructureController(
    private val recordsStructureApiOpt: Optional<RecordsStructureApi>,
    private val existingValuesApi: ExistingValuesApi,
    private val clusterEnabledFilter: ClusterEnabledFilter
) : BaseController() {

    private fun recordsStructureApi(): RecordsStructureApi? = recordsStructureApiOpt.orElse(null)

    private fun disabled() = ModelAndView("featureDisabled", mapOf(
        "featureName" to "Record structure analyzer",
        "propertyToEnable" to listOf("RECORD_ANALYZER_ENABLED", "app.record-analyzer.enabled")
    ))

    @GetMapping
    fun showRecordsStructureMenu(): ModelAndView {
        val recordsStructureApi = recordsStructureApi() ?: return disabled()
        val existingValues = existingValuesApi.all()
        val clusterIdentifiers = existingValues.clusterIdentifiers.filter { clusterEnabledFilter.enabled(it) }
        val topicTypes = recordsStructureApi.allTopicsTypes()
        return ModelAndView("recordsStructure/index", mapOf(
            "clusterIdentifiers" to clusterIdentifiers,
            "topicNames" to existingValues.topics,
            "topicTypes" to topicTypes,
        ))
    }

    @GetMapping(RECORDS_STRUCTURE_TOPIC)
    fun showRecordsStructureTopic(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam(name = "clusterIdentifier", required = false) clusterIdentifier: KafkaClusterIdentifier?,
    ): ModelAndView {
        val recordsStructureApi = recordsStructureApi() ?: return disabled()
        val recordsStructure = if (clusterIdentifier == null) {
            recordsStructureApi.structureOfTopic(topicName)
        } else {
            recordsStructureApi.structureOfTopicOnCluster(topicName, clusterIdentifier)
        }
        return ModelAndView("recordsStructure/structure", mapOf(
                "topicName" to topicName,
                "clusterIdentifier" to clusterIdentifier,
                "recordsStructure" to recordsStructure,
        ))
    }

    @GetMapping(RECORDS_STRUCTURE_DRY_RUN)
    fun showDryRunInspect(): ModelAndView {
         return ModelAndView("recordsStructure/dryRun")
    }

    @PostMapping(RECORDS_STRUCTURE_DRY_RUN_INSPECT)
    fun showDryRunInspect(
        @RequestBody analyzeRecords: AnalyzeRecords
    ): ModelAndView {
        val recordsStructureApi = recordsStructureApi() ?: return disabled()
        val recordsStructure = recordsStructureApi.dryRunInspect(analyzeRecords)
        return ModelAndView("recordsStructure/dryRunInspect", mutableMapOf(
            "recordsStructure" to recordsStructure,
        ))
    }
}