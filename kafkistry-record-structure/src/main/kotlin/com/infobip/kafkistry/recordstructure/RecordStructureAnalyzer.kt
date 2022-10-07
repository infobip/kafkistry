package com.infobip.kafkistry.recordstructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PayloadType
import com.infobip.kafkistry.model.RecordsStructure
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
class RecordStructureAnalyzer(
    private val properties: RecordAnalyzerProperties,
    private val recordStructureAnalyzerStorage: RecordStructureAnalyzerStorage,
    private val analyzeFilter: AnalyzeFilter,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(RecordStructureAnalyzer::class.java)
    private val objectMapper = ObjectMapper()

    private val clusterRecordsStructures: ClusterRecordsStructuresMap = load()

    fun analyzeRecord(
        clusterRef: ClusterRef,
        consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>
    ) {
        if (!analyzeFilter.shouldAnalyze(clusterRef, consumerRecord.topic())) {
            return
        }
        AnalyzeContext(
            properties, analyzeFilter, objectMapper,
            topic = consumerRecord.topic(),
            cluster = clusterRef,
        ).analyzeRecord(consumerRecord, clusterRecordsStructures)
    }

    final fun load(): ClusterRecordsStructuresMap = recordStructureAnalyzerStorage.load()

    fun removeCluster(clusterIdentifier: KafkaClusterIdentifier) {
        clusterRecordsStructures.remove(clusterIdentifier)
        dump()
    }

    /**
     *  Dumps the collected statistic to file
     */
    fun dump(): Int {
        log.info("Dumping collected recordStructures, size: ${clusterRecordsStructures.size}")
        val numDumpedRecordsStructures =  clusterRecordsStructures.values.sumOf { it.size }
        recordStructureAnalyzerStorage.dump(clusterRecordsStructures)
        return numDumpedRecordsStructures
    }

    /**
     * Trims obsolete recordStructures entities as well as obsolete recordFields
     */
    fun trim(): Int {
        log.info("Trimming collected recordStructures")
        return with(TrimContext(properties)) {
            trimOldFields(clusterRecordsStructures)
            trimCounter.get()
        }
    }

    /**
     * Get inferred structure of records in the topic across different clusters or `null` if having no samples yet
     */
    fun getStructure(topicName: TopicName): RecordsStructure? {
        return with(MergingContext(properties)) {
            clusterRecordsStructures.values
                .flatMap { it.entries }
                .filter { it.key == topicName }
                .map { it.value.field }
                .takeIf { it.isNotEmpty() }
                ?.reduce { acc, recordStructures ->
                    acc merge recordStructures
                }
                ?.toRecordsStructure()
        }
    }

    /**
     * Get inferred structure of records in the topic on specific cluster or `null` if having no samples yet
     */
    fun getStructure(clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName): RecordsStructure? {
        return clusterRecordsStructures[clusterIdentifier]
            ?.get(topicName)
            ?.field
            ?.toRecordsStructure()
    }

    /**
     * Get inferred structure of records for all topic on cluster which have processed samples
     */
    fun getAllStructures(clusterIdentifier: KafkaClusterIdentifier): Map<TopicName, RecordsStructure> {
        return clusterRecordsStructures[clusterIdentifier]
            ?.mapValues { it.value.field.toRecordsStructure() }
            ?: emptyMap()
    }

    /**
     * Saves all the collected topicStructure on shutdown event
     */
    override fun close() {
        dump()
    }

    fun allTopicsTypes(): Map<TopicName, Map<KafkaClusterIdentifier, PayloadType>> {
        return clusterRecordsStructures
            .flatMap { (cluster, topicStructures) ->
                topicStructures.map { (topic, structure) ->
                    Triple(topic, cluster, structure.field.payloadType)
                }
             }
            .groupBy { it.first }
            .mapValues { (_, entries) ->
                entries.associate { it.second to it.third }
            }
    }
}
