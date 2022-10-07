package com.infobip.kafkistry.service.recordstructure

import com.infobip.kafkistry.model.ClusterRef
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import com.infobip.kafkistry.model.RecordsStructure
import com.infobip.kafkistry.recordstructure.AnalyzeFilter
import com.infobip.kafkistry.recordstructure.InMemoryReferenceRecordStructureAnalyzerStorage
import com.infobip.kafkistry.recordstructure.RecordAnalyzerProperties
import com.infobip.kafkistry.recordstructure.RecordStructureAnalyzer
import org.springframework.stereotype.Service
import java.util.*

@Service
class DryRunAnalyzerInspector {

    companion object {
        private val CLUSTER = ClusterRef("dry-run-cluster")
        private const val TOPIC = "dry-run-topic"
    }

    fun analyze(analyzeRecords: AnalyzeRecords): RecordsStructure? {
        return with(newAnalyzer()) {
            analyzeRecords.records.forEach {
                analyzeRecord(CLUSTER, it.toConsumerRecord(analyzeRecords.payloadEncoding))
            }
            getStructure(TOPIC)
        }
    }

    private fun newAnalyzer(): RecordStructureAnalyzer {
        val properties = RecordAnalyzerProperties()
        return RecordStructureAnalyzer(
            properties = properties,
            recordStructureAnalyzerStorage = InMemoryReferenceRecordStructureAnalyzerStorage(),
            analyzeFilter = AnalyzeFilter(properties)
        )
    }

    private fun AnalyzeRecord.toConsumerRecord(encoding: PayloadEncoding): ConsumerRecord<ByteArray?, ByteArray?> {
        return newRecord(
            payload = payload?.toBytes(encoding),
            headers = headers.mapValues { it.value?.toBytes(encoding) },
        )
    }

    private fun String.toBytes(encoding: PayloadEncoding): ByteArray {
        return when(encoding) {
            PayloadEncoding.UTF8_STRING -> toByteArray(Charsets.UTF_8)
            PayloadEncoding.BASE64 -> Base64.getDecoder().decode(this)
        }
    }

    private fun newRecord(
        payload: ByteArray? = null, headers: Map<String, ByteArray?> = emptyMap()
    ) = ConsumerRecord(
        TOPIC, 0, 0, 0, TimestampType.CREATE_TIME,
        0, 0,
        byteArrayOf(0x0), payload,
        RecordHeaders(headers.map { RecordHeader(it.key, it.value) }),
        Optional.empty(),
    )
}