package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.ClustersApi
import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.topic.TopicInspectionResultType
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.service.topic.compare.ComparedValue
import com.infobip.kafkistry.service.topic.compare.ComparingRequest
import com.infobip.kafkistry.service.topic.compare.ComparingSubjectType
import com.infobip.kafkistry.service.topic.compare.PropertyStatus
import com.infobip.kafkistry.webapp.url.CompareUrls.Companion.COMPARE
import com.infobip.kafkistry.webapp.url.CompareUrls.Companion.COMPARE_RESULT
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$COMPARE")
class CompareController(
    private val topicsRegistry: TopicsRegistryService,
    private val inspectApi: InspectApi,
    private val existingValuesApi: ExistingValuesApi,
    private val clustersApi: ClustersApi,
    private val clusterEnabledFilter: ClusterEnabledFilter
) : BaseController() {

    @GetMapping
    fun showComparePage(
        @RequestParam(name = "topicName", required = false) topicName: TopicName?,
        @RequestParam(name = "clusterIdentifier", required = false) clusterIdentifier: KafkaClusterIdentifier?,
        @RequestParam(name = "compareType", required = false) comparingSubjectType: ComparingSubjectType?
    ): ModelAndView {
        val clusterIdentifiers = when {
            clusterIdentifier != null -> listOf(clusterIdentifier)
            topicName != null -> {
                val presence = topicsRegistry.findTopic(topicName)?.presence
                if (presence == null) {
                    inspectApi.inspectTopic(topicName).statusPerClusters
                        .filter { TopicInspectionResultType.UNKNOWN in it.status.types }
                        .map { it.clusterIdentifier }
                } else {
                    clustersApi.listClusters()
                        .map { it.ref() }
                        .filter { presence.needToBeOnCluster(it) }
                        .sortedBy {
                            when (clusterEnabledFilter.enabled(it.identifier)) {
                                true -> 0
                                false -> 1  //disabled clusters last
                            }
                        }
                        .map { it.identifier }
                }
            }
            else -> null
        }
        val allTopicNames = topicsRegistry.listTopics().map { it.name } + inspectApi.inspectUnknownTopics().map { it.topicName }
        return ModelAndView("compare/compare", mapOf(
                "allTopics" to allTopicNames,
                "allClusters" to existingValuesApi.all().clusterIdentifiers,
                "topicName" to topicName,
                "clusterIdentifiers" to clusterIdentifiers,
                "compareType" to comparingSubjectType,
        ))
    }

    @PostMapping(COMPARE_RESULT)
    fun showCompareResult(
            @RequestBody compareRequest: ComparingRequest
    ): ModelAndView {
        val result = inspectApi.compareTopics(compareRequest)
        return ModelAndView("compare/result", mapOf(
                "compareRequest" to compareRequest,
                "result" to result,
                "legend" to mapOf(
                        "compare matching between source and target" to ComparedValue(value = "12345", status = PropertyStatus.MATCHING),
                        "target value different than source" to ComparedValue(value = "12345", status = PropertyStatus.MISMATCH),
                        "source has target that differs" to ComparedValue(value = "12345", status = PropertyStatus.HAS_MISMATCH),
                        "value not present" to ComparedValue(value = "12345", status = PropertyStatus.NOT_PRESENT),
                        "status undefined (no source value to compare against)" to ComparedValue(value = "12345", status = PropertyStatus.UNDEFINED),
                        "bold text value defined by config in registry" to ComparedValue(value = "12345", status = PropertyStatus.MATCHING, configDefined = true)
                ),
                "topicConfigDoc" to existingValuesApi.all().topicConfigDoc,
        ))
    }


}