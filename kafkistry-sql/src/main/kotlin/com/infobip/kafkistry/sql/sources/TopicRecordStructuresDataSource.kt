@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.recordstructure.RecordStructureAnalyzer
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.sql.SqlDataSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.Base64
import jakarta.persistence.*
import com.infobip.kafkistry.model.RecordsStructure as RecordsStructureModel
import com.infobip.kafkistry.model.RecordField as RecordFieldModel

@Component
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
class TopicRecordStructuresDataSource(
    private val recordStructureAnalyzer: RecordStructureAnalyzer,
    private val clustersRegistry: ClustersRegistryService,
) : SqlDataSource<RecordsStructure> {
    override fun modelAnnotatedClass(): Class<RecordsStructure> = RecordsStructure::class.java

    override fun supplyEntities(): List<RecordsStructure> {
        val allClusterRefs = clustersRegistry.listClustersRefs()
        val clusterTopicRecordsStructures = allClusterRefs.associateWith {
            recordStructureAnalyzer.getAllStructures(it.identifier)
        }
        return clusterTopicRecordsStructures.flatMap { (cluster, topicStructures) ->
            topicStructures.map { (topic, structure) ->
                mapRecordStructure(cluster.identifier, topic, structure)
            }
        }
    }

    private fun mapRecordStructure(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        recordsStructure: RecordsStructureModel
    ): RecordsStructure {
        return RecordsStructure().apply {
            id = ClusterTopicId().apply {
                topic = topicName
                cluster = clusterIdentifier
            }
            payloadType = recordsStructure.payloadType
            nullable = recordsStructure.nullable
            jsonFields = recordsStructure.jsonFields?.flatMap { mapRecordField(it) }
            headerFields = recordsStructure.headerFields?.flatMap { mapRecordField(it) }
            sizes = with(recordsStructure.size) {
                key.mapToEmbedded("KEY") + value.mapToEmbedded("VALUE") + headers.mapToEmbedded("HEADERS")
            }
        }
    }

    private fun SizeOverTime.mapToEmbedded(property: String): List<PropertySizeOverTime> {
        return listOfNotNull(
            last15Min?.mapToEmbedded(property, SizeOverTime::last15Min.name),
            lastHour?.mapToEmbedded(property, SizeOverTime::lastHour.name),
            last6Hours?.mapToEmbedded(property, SizeOverTime::last6Hours.name),
            lastDay?.mapToEmbedded(property, SizeOverTime::lastDay.name),
            lastWeek?.mapToEmbedded(property, SizeOverTime::lastWeek.name),
            lastMonth?.mapToEmbedded(property, SizeOverTime::lastMonth.name),
        )
    }

    private fun SizeStatistic.mapToEmbedded(property: String, bucket: String): PropertySizeOverTime {
        return PropertySizeOverTime().apply {
            this.property = property
            this.bucket = bucket
            this.samples = count
            this.avg = this@mapToEmbedded.avg
            this.max = this@mapToEmbedded.max
            this.min = this@mapToEmbedded.min
        }
    }

    private fun mapRecordField(field: RecordFieldModel): List<RecordField> {
        val thisField = RecordField().apply {
            fieldName = field.name
            fullFieldName = field.fullName
            fieldType = field.type
            nullable = field.nullable
            highCardinality = field.value?.highCardinality
            tooBig = field.value?.tooBig
            valueSet = field.value?.valueSet?.takeIf { it.isNotEmpty() }?.joinToString(",") {
                when (it) {
                    is ByteArray -> Base64.getEncoder().encodeToString(it)
                    else -> it.toString()
                }
            }
        }
        val children: List<RecordField> = field.children
            ?.flatMap { mapRecordField(it) }
            ?: emptyList()
        return listOf(thisField) + children
    }


}

@Entity
@Table(name = "RecordsStructures")
class RecordsStructure {

    @EmbeddedId
    lateinit var id: ClusterTopicId

    @Enumerated(EnumType.STRING)
    lateinit var payloadType: PayloadType

    var nullable: Boolean? = null

    @ElementCollection
    @JoinTable(name = "RecordStructures_RecordJsonFields")
    var jsonFields: List<RecordField>? = null

    @ElementCollection
    @JoinTable(name = "RecordStructures_RecordHeaderFields")
    var headerFields: List<RecordField>? = null

    @ElementCollection
    @JoinTable(name = "RecordStructures_Sizes")
    var sizes: List<PropertySizeOverTime>? = null

}

@Embeddable
class RecordField {

    var fieldName: String? = null
    var fullFieldName: String? = null

    @Enumerated(EnumType.STRING)
    lateinit var fieldType: RecordFieldType

    var nullable: Boolean? = null

    var highCardinality: Boolean? = null
    var tooBig: Boolean? = null
    var valueSet: String? = null

}

@Embeddable
class PropertySizeOverTime {
    lateinit var property: String
    lateinit var bucket: String
    var samples: Int? = null
    var avg: Int? = null
    var max: Int? = null
    var min: Int? = null
}
