package com.infobip.kafkistry.service.topic.compare

import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.topic.compare.ComparingSubjectType.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.*
import org.springframework.stereotype.Service

@Service
class TopicConfigComparatorService(
    private val topicsRegistryService: TopicsRegistryService,
    private val topicsInspectionService: TopicsInspectionService,
    private val clustersRegistryService: ClustersRegistryService,
) {

    fun compareTopicConfigurations(comparingRequest: ComparingRequest): ComparingResult {
        val source = comparingRequest.source.resolveSubject()
        val targets = comparingRequest.targets.map { it.resolveSubject() }
        val allConfigKeys = targets.asSequence().map { it.properties }.plus(source.properties)
                .filterNotNull()
                .map { it.config.keys }
                .flatten()
                .distinct()
                .sorted()
                .toList()
        return ComparingResult(
                inspectionStatusTypes = TopicInspectionStatusTypes(
                        sourceTypes = source.status.status.types,
                        targetsTypes = targets.map { it.status.status.types }
                ),
                partitionCount = analyzeProperty(source, targets) { it.partitionCount },
                replicationFactor = analyzeProperty(source, targets) { it.replicationFactor },
                config = allConfigKeys.associate { key ->
                    key to analyzeProperty(source, targets) { it.config[key] }
                }
        )
    }

    private fun ComparingSubject.resolveSubject(): Subject {
        if (topicName == null || onClusterIdentifier == null || type == null) {
            return Subject(TopicClusterStatus.unavailable(), null)
        }
        val onClusterRef = clustersRegistryService.getCluster(onClusterIdentifier).ref()
        val topicDescription = topicsRegistryService.findTopic(topicName)
        val topicClusterStatus = topicsInspectionService.inspectTopicOnCluster(topicName, onClusterIdentifier)
        val subjectProperties = when (type) {
            ACTUAL -> topicClusterStatus.existingTopicInfo?.toSourceProperties()
            CONFIGURED -> topicDescription?.toSourceProperties(onClusterRef, topicClusterStatus)
        }
        return Subject(topicClusterStatus, subjectProperties)
    }

    private fun ExistingTopicInfo.toSourceProperties(): SubjectProperties {
        return SubjectProperties(
                properties.partitionCount.toValue(byConfigDefined = false),
                properties.replicationFactor.toValue(byConfigDefined = false),
                config.mapValues { it.toValue() }
        )
    }

    private fun TopicDescription.toSourceProperties(
        clusterRef: ClusterRef,
        inspectionStatus: TopicClusterStatus
    ): SubjectProperties? {
        if (!presence.needToBeOnCluster(clusterRef)) {
            return null
        }
        val properties = propertiesForCluster(clusterRef)
        val configForCluster = configForCluster(clusterRef)
        val expectedConfig = inspectionStatus.configEntryStatuses
                ?.mapValues { it.value.toValue(byConfigDefined = configForCluster.containsKey(it.key)) }
                ?: configForCluster.mapValues { it.value.toValue(byConfigDefined = true) }
        return SubjectProperties(
                properties.partitionCount.toValue(),
                properties.replicationFactor.toValue(),
                expectedConfig
        )
    }

    private fun analyzeProperty(
            source: Subject,
            targets: List<Subject>,
            extractor: (SubjectProperties) -> Value?
    ): ComparedProperty {
        val sourceValue = source.properties?.let(extractor)
        val targetVals = targets.map { target ->
            target.properties
                    ?.let(extractor)
                    ?.let {
                        when {
                            sourceValue == null -> it.toComparedValue(PropertyStatus.UNDEFINED)
                            it.value == sourceValue.value -> it.toComparedValue(PropertyStatus.MATCHING)
                            else -> it.toComparedValue(PropertyStatus.MISMATCH)
                        }
                    }
                    ?: ComparedValue.notPresent()
        }
        val someTargetMismatches = targetVals.any { it.status == PropertyStatus.MISMATCH }
        val sourceVal = sourceValue
                ?.toComparedValue(when (someTargetMismatches) {
                    true -> PropertyStatus.HAS_MISMATCH
                    false -> PropertyStatus.MATCHING
                })
                ?: ComparedValue.notPresent()
        return ComparedProperty(sourceVal, targetVals)
    }

    private data class Subject(
        val status: TopicClusterStatus,
        val properties: SubjectProperties?
    )

    private data class SubjectProperties(
            val partitionCount: Value,
            val replicationFactor: Value,
            val config: Map<String, Value>
    )

    private data class Value(
            val value: String?,
            val clustersDefault: Boolean,
            val byConfigDefined: Boolean
    ) {
        fun toComparedValue(status: PropertyStatus) = ComparedValue(
                value, clustersDefault, byConfigDefined, status
        )
    }

    private fun Int.toValue(clustersDefault: Boolean = false, byConfigDefined: Boolean = true) = Value(toString(), clustersDefault, byConfigDefined)
    private fun String?.toValue(clustersDefault: Boolean = false, byConfigDefined: Boolean) = Value(this, clustersDefault, byConfigDefined)
    private fun Map.Entry<String, ConfigValue>.toValue() = Value(value.value, value.default, false)
    private fun ValueInspection.toValue(byConfigDefined: Boolean) = Value(expectedValue, expectingClusterDefault, byConfigDefined)

}