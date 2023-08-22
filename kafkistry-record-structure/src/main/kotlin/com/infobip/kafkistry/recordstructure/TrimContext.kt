package com.infobip.kafkistry.recordstructure

import java.util.concurrent.atomic.AtomicInteger

class TrimContext(
    properties: RecordAnalyzerProperties,
    now: Long = generateTimestamp(),
    val trimCounter: AtomicInteger = AtomicInteger(0)
) : ProcessingContext(properties, now) {

    /**
     * Trims obsolete recordStructures entities as well as obsolete recordFields
     */
    fun trimOldFields(clusterRecordsStructures: ClusterRecordsStructuresMap) {
        for (topicStructures in clusterRecordsStructures.values) {
            val entriesCountBeforeTrim = topicStructures.entries.size
            topicStructures.values.removeIf { topicStructure ->
                topicStructure.timestamp.tooOld() || topicStructure.field.timestampWrappedJsonFields?.size == 0
            }
            trimCounter.addAndGet(entriesCountBeforeTrim - topicStructures.entries.size)
            for ((topic, topicStructure) in topicStructures) {
                topicStructures[topic] = topicStructure.withField {
                    it.copy(
                        timestampWrappedJsonFields = it.timestampWrappedJsonFields?.trim(),
                        size = it.size.roll(),
                    )
                }
            }
        }
    }

    private fun List<TimestampWrapper<TimestampWrappedRecordField>>.trim(): List<TimestampWrapper<TimestampWrappedRecordField>> {
        val sizeBefore = size
        return this
            .filter { !it.timestamp.tooOld() }
            .map { field ->
                field.withField {
                    it.copy(
                        children = it.children?.trim(),
                        value = it.value.takeIf { properties.valueSampling.enabled }
                    )
                }
            }
            .also { trimCounter.addAndGet(sizeBefore - it.size) }
    }

    private fun RecordTimedSize.roll(): RecordTimedSize {
        return RecordTimedSize(
            keySize = keySize.maybeRollover(now),
            valueSize = valueSize.maybeRollover(now),
            headersSize = headersSize.maybeRollover(now),
        )
    }

}
