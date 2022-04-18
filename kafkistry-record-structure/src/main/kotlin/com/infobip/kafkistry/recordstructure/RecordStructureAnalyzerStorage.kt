package com.infobip.kafkistry.recordstructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.commons.io.FileUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface RecordStructureAnalyzerStorage {

    fun load(): ClusterRecordsStructuresMap

    fun dump(recordsStructuresMap: ClusterRecordsStructuresMap)
}

class InMemoryReferenceRecordStructureAnalyzerStorage : RecordStructureAnalyzerStorage {

    private val ref = AtomicReference<ClusterRecordsStructuresMap>(ConcurrentHashMap())

    override fun load(): ClusterRecordsStructuresMap = ref.get()

    override fun dump(recordsStructuresMap: ClusterRecordsStructuresMap) {
        ref.set(recordsStructuresMap)
    }

}

@Component
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
class DirectoryRecordStructureAnalyzerStorage(
    properties: RecordAnalyzerProperties,
) : RecordStructureAnalyzerStorage {
    private val storage = File(properties.storageDir)

    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    /**
     *  Loads the collected statistic
     */
    override fun load(): ClusterRecordsStructuresMap {
        if (!storage.exists()) {
            storage.mkdirs()
        }
        val clusterRecordsStructures: ClusterRecordsStructuresMap = ConcurrentHashMap()
        storage.listFiles()?.forEach { kafkaClusterIdDirectory ->
            if (!kafkaClusterIdDirectory.isDirectory) {
                return@forEach
            }
            val clusterTopicsStructures = clusterRecordsStructures
                .computeIfAbsent(kafkaClusterIdDirectory.name) { ConcurrentHashMap() }
            kafkaClusterIdDirectory.listFiles()?.forEach { topicStructuresFile ->
                val topicName = topicStructuresFile.name.removeSuffix(".json")
                try {
                    clusterTopicsStructures[topicName] = readRecordsStructure(topicStructuresFile)
                } catch (ex: Exception) {
                    //IGNORE
                }
            }
        }
        return clusterRecordsStructures
    }

    /**
     *  Dumps the collected statistic
     */
    override fun dump(recordsStructuresMap: ClusterRecordsStructuresMap) {
        for ((kafkaClusterId, topicStructures) in recordsStructuresMap) {
            val kafkaClusterIdStorage = File(storage, kafkaClusterId)
            kafkaClusterIdStorage.mkdir()
            topicStructures.forEach { (topicName, timestampWrappedRecordsStructure) ->
                val topicStructuresFile = File(kafkaClusterIdStorage, "$topicName.json")
                writeRecordsStructure(timestampWrappedRecordsStructure, topicStructuresFile)
            }
            kafkaClusterIdStorage.listFiles().orEmpty()
                .filter { it.name.removeSuffix(".json") !in topicStructures.keys }
                .forEach { it.delete() }
        }
        storage.listFiles().orEmpty()
            .filter { it.name !in recordsStructuresMap.keys }
            .forEach { FileUtils.deleteDirectory(it) }
    }

    /**
     * Serialize and write topic structure to the topic structure file
     */
    private fun writeRecordsStructure(
        wrappedRecordsStructure: TimestampWrapper<TimestampWrappedRecordsStructure>,
        topicStructuresFile: File
    ) {
        objectMapper.writeValue(topicStructuresFile, wrappedRecordsStructure)
    }

    /**
     * Deserialize and read topic structure from the topic structure file
     */
    private fun readRecordsStructure(topicStructuresFile: File): TimestampWrapper<TimestampWrappedRecordsStructure> {
        return objectMapper.readValue(
            topicStructuresFile,
            object : TypeReference<TimestampWrapper<TimestampWrappedRecordsStructure>>() {})
    }
}
