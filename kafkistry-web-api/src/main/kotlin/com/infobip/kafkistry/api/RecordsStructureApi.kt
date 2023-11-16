package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PayloadType
import com.infobip.kafkistry.model.RecordsStructure
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.recordstructure.RecordStructureAnalyzer
import com.infobip.kafkistry.service.recordstructure.AnalyzeRecords
import com.infobip.kafkistry.service.recordstructure.DryRunAnalyzerInspector
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/records-structure")
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
class RecordsStructureApi(
    private val recordStructureAnalyzer: RecordStructureAnalyzer,
    private val dryRunAnalyzerInspector: DryRunAnalyzerInspector,
) {

    @GetMapping("/topic/types")
    fun allTopicsTypes(): Map<TopicName, Map<KafkaClusterIdentifier, PayloadType>> =
        recordStructureAnalyzer.allTopicsTypes()

    @GetMapping("/topic-cluster")
    fun structureOfTopicOnCluster(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): RecordsStructure? = recordStructureAnalyzer.getStructure(clusterIdentifier, topicName)


    @GetMapping("/topic/all")
    fun structuresOfTopics(): Map<TopicName, RecordsStructure> = recordStructureAnalyzer.getAllStructures()

    @GetMapping("/topic")
    fun structureOfTopic(
        @RequestParam("topicName") topicName: TopicName,
    ): RecordsStructure? = recordStructureAnalyzer.getStructure(topicName)

    @GetMapping("/cluster")
    fun structureOfTopicsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): Map<TopicName, RecordsStructure> = recordStructureAnalyzer.getAllStructures(clusterIdentifier)

    @PostMapping("/dry-run-inspect")
    fun dryRunInspect(
        @RequestBody analyzeRecords: AnalyzeRecords
    ): RecordsStructure? = dryRunAnalyzerInspector.analyze(analyzeRecords)

}
