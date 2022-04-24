package com.infobip.kafkistry.service.topic

import org.apache.kafka.clients.admin.ConfigEntry
import com.infobip.kafkistry.events.*
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicConfigMap
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.InspectionResultType.*
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service

@Service
class TopicManagementService(
    private val topicsRegistry: TopicsRegistryService,
    private val clientProvider: KafkaClientProvider,
    private val clustersRegistry: ClustersRegistryService,
    private val inspectionService: TopicsInspectionService,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val partitionsReplicasAssignor: PartitionsReplicasAssignor,
    private val configValueInspector: ConfigValueInspector,
    private val eventPublisher: EventPublisher
) {

    fun applyMissingTopicCreation(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val properties = topicDescription.propertiesForCluster(kafkaCluster.ref())
        val config = topicDescription.configForCluster(kafkaCluster.ref())
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, kafkaCluster.ref())
        if (MISSING !in inspectionResult.status.types) {
            throw KafkistryIllegalStateException("Topic '$topicName' is not MISSING on cluster '$clusterIdentifier', can't be created, topic status: ${inspectionResult.status}")
        }
        val clusterData = kafkaStateProvider.getLatestClusterStateValue(clusterIdentifier)
        val clusterBrokersLoad = inspectionService.inspectClusterBrokersLoad(clusterData)
        val assignments = partitionsReplicasAssignor.assignNewPartitionReplicas(
                existingAssignments = emptyMap(),
                allBrokers = clusterData.clusterInfo.nodeIds,
                numberOfNewPartitions = properties.partitionCount,
                replicationFactor = properties.replicationFactor,
                existingPartitionLoads = inspectionResult.existingTopicInfo
                        ?.partitionsAssignments
                        ?.toPartitionReplicasMap()
                        ?.partitionLoads(inspectionResult.currentTopicReplicaInfos)
                        ?: emptyMap(),
                clusterBrokersLoad = clusterBrokersLoad
        )
        checkRuleViolations(inspectionResult.status)
        clientProvider.doWithClient(kafkaCluster) {
            it.createTopic(KafkaTopicConfiguration(topicName, assignments.newAssignments, config)).get()
        }
        kafkaCluster.ensureRepeatedlyReportsExisting(topicName)
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicCreatedEvent(clusterIdentifier, topicName))
    }

    private fun KafkaCluster.ensureRepeatedlyReportsExisting(
        topicName: TopicName,
        timeoutMs: Long = 15_000, delayMs: Long = 500, requiredInRowCount: Int = 3
    ) {
        // This nasty loop exist because successful completion of AdminClient.createTopics()
        // DOES NOT mean that topic creation fully propagated across all brokers in cluster.
        // Subsequent calls of AdminClient.describeTopics() or AdminClient.describeConfigs() might result in
        // org.apache.kafka.common.errors.UnknownTopicOrPartitionException: This server does not host this topic-partition.
        // This loop is working wait for topic to be describe-able several times in row with success.
        val start = System.currentTimeMillis()
        var okInRow = 0
        while (true) {
            val exist = clientProvider.doWithClient(this) { it.topicFullyExists(topicName).get() }
            if (exist) okInRow++ else okInRow = 0
            if (okInRow >= requiredInRowCount || System.currentTimeMillis() - start > timeoutMs) {
                break
            }
            Thread.sleep(delayMs)
        }
    }

    fun applyUnexpectedOrUnknownTopicDeletion(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) {
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicName, clusterIdentifier)
        if (UNEXPECTED !in inspectionResult.status.types && UNKNOWN !in inspectionResult.status.types) {
            throw KafkistryIllegalStateException("Topic '$topicName' state on cluster '$clusterIdentifier' is ${inspectionResult.status}, can't be deleted safely")
        }
        doDeleteTopic(topicName, clusterIdentifier)
    }

    fun forceDeleteTopic(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = doDeleteTopic(topicName, clusterIdentifier)

    private fun doDeleteTopic(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        clientProvider.doWithClient(kafkaCluster) {
            it.deleteTopic(topicName).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicDeletedEvent(clusterIdentifier, topicName))
    }

    fun applyWrongTopicConfigUpdate(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        expectedTopicConfig: TopicConfigMap,
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val config = topicDescription.configForCluster(kafkaCluster.ref())
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, kafkaCluster.ref())
        val finalConfigToSet = resolveTopicConfigToUpdate(
            clusterIdentifier, topicName, inspectionResult, expectedTopicConfig
        )
        config.forEach { (key, expectedValue) ->
            val valueToSet = finalConfigToSet[key]
            if (valueToSet != expectedValue) {
                throw KafkistryIllegalStateException(
                    "Topic '$topicName' on cluster '$clusterIdentifier', has different expected value for property='$key' " +
                            " expected='$expectedValue' settingValue='$valueToSet' (configuration changed in meantime?)"
                )
            }
        }
        if (WRONG_CONFIG !in inspectionResult.status.types) {
            throw KafkistryIllegalStateException(
                "Topic '$topicName' is not WRONG_CONFIG on cluster '$clusterIdentifier', can't be updated, " +
                        "topic status: ${inspectionResult.status}"
            )
        }
        checkRuleViolations(inspectionResult.status)
        clientProvider.doWithClient(kafkaCluster) {
            it.updateTopicConfig(topicName, finalConfigToSet).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun applyAddTopicPartitions(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        addedPartitionsAssignments: Map<Partition, List<BrokerId>>
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        if (WRONG_PARTITION_COUNT !in inspectionResult.status.types) {
            throw KafkistryIllegalStateException(
                    "Topic '$topicName' is not WRONG_PARTITION_COUNT on cluster '$clusterIdentifier'," +
                            " can't be updated, topic status: ${inspectionResult.status}"
            )
        }
        val partitionCountChange = inspectionService.inspectTopicPartitionPropertiesChanges(
                topicDescription, cluster.ref()
        ).partitionCountChange
        if (partitionCountChange.type != PropertiesChangeType.CHANGE) {
            throw KafkistryIllegalStateException(
                    "Topic '$topicName' change option is ${partitionCountChange.type} on cluster '$clusterIdentifier'," +
                            " not valid to create new partitions"
            )
        }
        partitionCountChange.change?.also {
            //we don't need such strict check, for start, it's the simplest validation check
            if (it.addedPartitionReplicas != addedPartitionsAssignments) {
                throw KafkistryIllegalStateException("Rejecting add topic partitions operation because recommended assignment has been changed")
            }
        }
        checkRuleViolations(inspectionResult.status)
        val targetPartitionCount = topicDescription.propertiesForCluster(cluster.ref()).partitionCount
        clientProvider.doWithClient(cluster) {
            it.addTopicPartitions(topicName, targetPartitionCount, addedPartitionsAssignments).get()
        }
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun applyIncreaseReplicationFactor(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        newPartitionsAssignments: Map<Partition, List<BrokerId>>,
        throttleBytesPerSec: Int
    ) = applyReplicationFactorChange(topicName, clusterIdentifier, newPartitionsAssignments, throttleBytesPerSec)

    fun applyReduceReplicationFactor(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        newPartitionsAssignments: Map<Partition, List<BrokerId>>
    ) = applyReplicationFactorChange(topicName, clusterIdentifier, newPartitionsAssignments, 0)

    private fun applyReplicationFactorChange(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        newPartitionsAssignments: Map<Partition, List<BrokerId>>,
        throttleBytesPerSec: Int
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        if (WRONG_REPLICATION_FACTOR !in inspectionResult.status.types) {
            throw KafkistryIllegalStateException(
                    "Topic '$topicName' is not WRONG_REPLICATION_FACTOR on cluster '$clusterIdentifier'," +
                            " can't be updated, topic status: ${inspectionResult.status}"
            )
        }
        val replicationFactorChange = inspectionService.inspectTopicPartitionPropertiesChanges(
                topicDescription, cluster.ref()
        ).replicationFactorChange
        if (replicationFactorChange.type != PropertiesChangeType.CHANGE) {
            throw KafkistryIllegalStateException(
                    "Topic '$topicName' change option is ${replicationFactorChange.type} on cluster '$clusterIdentifier'," +
                            " not valid to create new partitions"
            )
        }
        replicationFactorChange.change?.also {
            //we don't need such strict check, for start, it's the simplest validation check
            if (it.newAssignments != newPartitionsAssignments) {
                throw KafkistryIllegalStateException("Rejecting change of replication factor operation because recommended assignment has been changed, comparing to generated")
            }
        }
        checkRuleViolations(inspectionResult.status)
        clientProvider.doWithClient(cluster) {
            it.reAssignPartitions(topicName, newPartitionsAssignments, throttleBytesPerSec).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun applyNewPartitionReplicaAssignments(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        newPartitionsAssignments: Map<Partition, List<BrokerId>>,
        throttleBytesPerSec: Int
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        val validation = partitionsReplicasAssignor.validateAssignments(
                assignments = newPartitionsAssignments,
                allBrokers = kafkaStateProvider.getLatestClusterStateValue(clusterIdentifier).clusterInfo.nodeIds,
                topicProperties = topicDescription.propertiesForCluster(cluster.ref())
        )
        if (!validation.valid) {
            throw KafkaClusterManagementException("Aborting re-assignment of partition replicas, got invalid assignments $validation")
        }
        checkRuleViolations(inspectionResult.status)
        clientProvider.doWithClient(cluster) {
            it.reAssignPartitions(topicName, newPartitionsAssignments, throttleBytesPerSec).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun applyTopicsBulkPartitionReplicaAssignments(
        clusterIdentifier: KafkaClusterIdentifier,
        topicPartitionReAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>,
        throttleBytesPerSec: Int
    ) {
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val topicDescriptions = topicsRegistry.listTopics().associateBy { it.name }

        val topicInspectionResults = inspectionService.inspectCluster(cluster)
                .let {
                    it.statusPerTopics ?: throw KafkistryIllegalStateException(
                            "Unable to perform topics re-assignments when cluster state is: " + it.clusterState
                    )
                }
                .associateBy { it.topicName }
        topicPartitionReAssignments.forEach { (topicName, newPartitionsAssignments) ->
            val topicDescription = topicDescriptions[topicName]
            val inspectionResult = topicInspectionResults[topicName]
            if (inspectionResult?.existingTopicInfo == null) {
                throw KafkistryIllegalStateException(
                        "Can't do validation of topic assignments for topic '$topicName' which is missing kafka cluster"
                )
            }
            val topicProperties = topicDescription?.propertiesForCluster(cluster.ref())
                    ?: inspectionResult.existingTopicInfo.properties
            val validation = partitionsReplicasAssignor.validateAssignments(
                    assignments = newPartitionsAssignments,
                    allBrokers = kafkaStateProvider.getLatestClusterStateValue(clusterIdentifier).clusterInfo.nodeIds,
                    topicProperties = topicProperties
            )
            if (!validation.valid) {
                throw KafkaClusterManagementException("Aborting re-assignment of partition replicas for topic '$topicName', got invalid assignments $validation")
            }
            checkRuleViolations(inspectionResult.status)
        }
        clientProvider.doWithClient(cluster) {
            it.reAssignPartitions(topicPartitionReAssignments, throttleBytesPerSec).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(ClusterTopicsReAssignmentEvent(clusterIdentifier, topicPartitionReAssignments.keys.toList()))
    }

    fun cancelPartitionReplicasReAssignments(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        if (RE_ASSIGNMENT_IN_PROGRESS !in inspectionResult.status.types) {
            throw KafkaClusterManagementException("Aborting cancel or re-assignments, topic '$topicName' has no re-assigning partitions in progress")
        }
        clientProvider.doWithClient(cluster) {
            it.cancelReAssignments(topicName, inspectionResult.currentReAssignments.keys.toList())
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun verifyPartitionReplicaAssignments(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ): String {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        val existingAssignments = inspectionResult.existingTopicInfo
                ?.partitionsAssignments
                ?.toPartitionReplicasMap()
                ?: throw KafkistryIllegalStateException(
                        "Topic's '$topicName' assignments can't be read on cluster '$clusterIdentifier'," +
                                " can't be verified, topic status: ${inspectionResult.status}"
                )
        return clientProvider.doWithClient(cluster) {
            it.verifyReAssignPartitions(topicName, existingAssignments)
        }.also {
            kafkaStateProvider.refreshClusterState(clusterIdentifier)
            eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
        }
    }

    fun runPreferredReplicaElections(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) {
        val topicDescription = topicsRegistry.getTopic(topicName)
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val inspectionResult = inspectionService.inspectTopicOnCluster(topicDescription, cluster.ref())
        val partitionsToReElect = inspectionResult.existingTopicInfo
                ?.partitionsAssignments
                ?.partitionsToReElectLeader()
                ?: throw KafkistryIllegalStateException(
                        "Topic's '$topicName' assignments can't be read on cluster '$clusterIdentifier'," +
                                " partition leaders can't be re-elected, topic status: ${inspectionResult.status}"
                )
        clientProvider.doWithClient(cluster) {
            it.runPreferredReplicaElection(topicName, partitionsToReElect)
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    fun setTopicConfigOnCluster(clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName, topicConfigToSet: TopicConfigMap) {
        val cluster = clustersRegistry.getCluster(clusterIdentifier)
        val topicInspection = inspectionService.inspectTopicOnCluster(topicName, clusterIdentifier)
        val finalConfig = resolveTopicConfigToUpdate(
            clusterIdentifier, topicName, topicInspection, topicConfigToSet
        )
        clientProvider.doWithClient(cluster) {
            it.updateTopicConfig(topicName, finalConfig).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(TopicUpdatedEvent(clusterIdentifier, topicName))
    }

    private fun resolveTopicConfigToUpdate(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
        topicInspection: TopicClusterStatus,
        topicConfigToSet: TopicConfigMap,
    ): Map<String, String?> {
        val clusterConfig = kafkaStateProvider.getLatestClusterStateValue(clusterIdentifier).clusterInfo.config
        val currentConfig = topicInspection.let {
                it.existingTopicInfo?.config ?: throw KafkistryIllegalStateException(
                    "Topic '$topicName' does not exist on cluster '$clusterIdentifier'"
                )
            }
        val finalConfig = currentConfig
            .filterValues { it.source == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG && it.value != null }
            .mapValues { it.value.value }
            .plus(topicConfigToSet
                .filter { it.value != currentConfig[it.key]?.value }
                .mapValues {
                    val valueMeetsClusterExpectation = configValueInspector.isValueSameAsExpectedByCluster(
                        it.key, it.value, clusterConfig
                    )
                    when (valueMeetsClusterExpectation) {
                        true -> null   //keep value being defined by static broker config
                        false -> it.value
                    }
                }
            )
        return finalConfig
    }

    private fun checkRuleViolations(result: TopicOnClusterInspectionResult) {
        if (CONFIG_RULE_VIOLATIONS in result.types) {
            throw KafkistryIllegalStateException("Not allowed to do this operation when there are config rule violations: "
                    + result.ruleViolations?.joinToString("; ") { it.renderMessage() }
            )
        }
    }
}

