package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.AvailableAction.*
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.service.resources.RequiredResourcesInspector
import com.infobip.kafkistry.service.resources.TopicResourceRequiredUsages
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.CURRENT_CONFIG_RULE_VIOLATIONS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_OUT_OF_SYNC_REPLICAS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_REPLICATION_THROTTLING
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_UNVERIFIED_REASSIGNMENTS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.INTERNAL
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.NEEDS_LEADER_ELECTION
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.PARTITION_LEADERS_DISBALANCE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.PARTITION_REPLICAS_DISBALANCE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.RE_ASSIGNMENT_IN_PROGRESS
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_CONFIG
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_PARTITION_COUNT
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.WRONG_REPLICATION_FACTOR
import com.infobip.kafkistry.service.topic.validation.TopicConfigurationValidator
import com.infobip.kafkistry.service.topic.validation.rules.ClusterMetadata
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class TopicIssuesInspector(
    private val configValueInspector: ConfigValueInspector,
    private val configurationValidator: TopicConfigurationValidator,
    private val partitionsReplicasAssignor: PartitionsReplicasAssignor,
    private val resourceUsagesInspector: RequiredResourcesInspector,
    private val aclLinkResolver: AclLinkResolver,
    private val externalInspectors: Optional<List<TopicExternalInspector>>,
) {

    fun inspectTopicDataOnClusterData(
        topicName: TopicName,
        topicDescription: TopicDescription?,
        existingTopic: KafkaExistingTopic?,
        currentTopicReplicaInfos: TopicReplicaInfos?,
        partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
        clusterRef: ClusterRef,
        latestClusterState: StateData<KafkaClusterState>
    ): TopicClusterStatus {
        val clusterInfo = latestClusterState.valueOrNull()?.clusterInfo
        return with(TopicOnClusterInspectionResult.Builder()) {
            checkExists(latestClusterState, existingTopic)
            checkClusterVisibility(latestClusterState)
            checkTopicUnknown(topicDescription, existingTopic)
            checkTopicUnavailable(latestClusterState, topicDescription, existingTopic)
            if (topicDescription != null) {
                inspectRegistryTopic(
                    topicDescription, clusterRef, clusterInfo,
                    existingTopic, currentTopicReplicaInfos, partitionReAssignments, latestClusterState,
                )
            }
            val configEntryStatuses = if (topicDescription != null && clusterInfo != null && existingTopic != null) {
                val expectedConfig = topicDescription.configForCluster(clusterRef)
                existingTopic.config.mapValues { (key, value) ->
                    configValueInspector.checkConfigProperty(key, value, expectedConfig[key], clusterInfo.config)
                }
            } else {
                null
            }
            if (clusterInfo != null && existingTopic != null) {
                checkTopicInternal(existingTopic)
                checkExitingTopicDisbalance(existingTopic, clusterInfo, currentTopicReplicaInfos)
            }
            val existingTopicInfo = if (existingTopic != null && clusterInfo != null) {
                existingTopic.toTopicInfo(clusterInfo.nodeIds, currentTopicReplicaInfos, partitionReAssignments, partitionsReplicasAssignor)
            } else {
                null
            }
            val resourceRequiredUsages = inspectRequiredResourcesUsage(topicDescription, clusterRef, clusterInfo)
            checkAffectingAclRules(topicName, clusterRef.identifier)
            val externInfo = checkExternalInspectors(topicName, clusterRef)
            TopicClusterStatus(
                status = prepareAndBuild(),
                lastRefreshTime = latestClusterState.lastRefreshTime,
                clusterIdentifier = clusterRef.identifier,
                clusterTags = clusterRef.tags,
                existingTopicInfo = existingTopicInfo,
                configEntryStatuses = configEntryStatuses,
                resourceRequiredUsages = resourceRequiredUsages,
                currentTopicReplicaInfos = currentTopicReplicaInfos,
                currentReAssignments = partitionReAssignments,
                externInspectInfo = externInfo,
            )
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.inspectRegistryTopic(
        topicDescription: TopicDescription,
        clusterRef: ClusterRef,
        clusterInfo: ClusterInfo?,
        existingTopic: KafkaExistingTopic?,
        currentTopicReplicaInfos: TopicReplicaInfos?,
        partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
        latestClusterState: StateData<KafkaClusterState>,
    ) {
        val topicName = topicDescription.name
        val expectedProperties = topicDescription.propertiesForCluster(clusterRef)
        val expectedConfig = topicDescription.configForCluster(clusterRef)
        val needToBeOnCluster = topicDescription.presence.needToBeOnCluster(clusterRef)
        checkValidationRules(topicName, needToBeOnCluster, expectedProperties, expectedConfig, clusterRef, clusterInfo, topicDescription)
        if (clusterInfo != null) {
            checkPresence(topicDescription.presence, clusterRef, latestClusterState, existingTopic)
        }
        if (clusterInfo != null && existingTopic != null) {
            checkPartitionCount(expectedProperties, existingTopic)
            checkReplicationFactor(expectedProperties, existingTopic, partitionReAssignments)
            checkConfigValues(expectedConfig, existingTopic, clusterInfo)
            checkExitingTopicValidationRules(topicName, existingTopic, clusterRef, clusterInfo, topicDescription, currentTopicReplicaInfos, partitionReAssignments)
            checkPreferredReplicaLeaders(existingTopic)
            checkOutOfSyncReplicas(existingTopic)
            checkReAssignment(existingTopic, partitionReAssignments)
        }
    }

    private fun inspectRequiredResourcesUsage(
        topicDescription: TopicDescription?,
        clusterRef: ClusterRef,
        clusterInfo: ClusterInfo?
    ): OptionalValue<TopicResourceRequiredUsages> = if (topicDescription?.presence?.needToBeOnCluster(clusterRef) == true) {
        try {
            topicDescription.resourceRequirements
                ?.let {
                    resourceUsagesInspector.inspectTopicResources(
                        topicDescription.propertiesForCluster(clusterRef), it, clusterRef, clusterInfo
                    )
                }
                ?.let { OptionalValue.of(it) }
                ?: OptionalValue.absent("missing 'resourceRequirements' in topic description")
        } catch (ex: Exception) {
            OptionalValue.absent(ex.toString())
        }
    } else {
        OptionalValue.absent("not needed on cluster")
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExists(
            latestClusterState: StateData<KafkaClusterState>, existingTopic: KafkaExistingTopic?
    ) {
        if (latestClusterState.stateType == StateType.VISIBLE) {
            exists(existingTopic != null)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkClusterVisibility(
            latestClusterState: StateData<KafkaClusterState>
    ) {
        when (latestClusterState.stateType) {
            StateType.VISIBLE -> Unit
            StateType.DISABLED -> addResultType(CLUSTER_DISABLED)
            else -> addResultType(CLUSTER_UNREACHABLE)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicUnknown(
            topicDescription: TopicDescription?, existingTopic: KafkaExistingTopic?
    ) {
        if (topicDescription == null && existingTopic != null) {
            addResultType(UNKNOWN)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicInternal(
            existingTopic: KafkaExistingTopic
    ) {
        if (existingTopic.internal) {
            addResultType(INTERNAL)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicUnavailable(
        latestClusterState: StateData<KafkaClusterState>,
        topicDescription: TopicDescription?,
        existingTopic: KafkaExistingTopic?
    ) {
        if (latestClusterState.stateType == StateType.VISIBLE && topicDescription == null && existingTopic == null) {
            addResultType(UNAVAILABLE)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkPresence(
        presence: Presence, clusterRef: ClusterRef,
        latestClusterState: StateData<KafkaClusterState>,
        existingTopic: KafkaExistingTopic?
    ) {
        if (latestClusterState.stateType == StateType.DISABLED) {
            return
        }
        val needToBeOnCluster = presence.needToBeOnCluster(clusterRef)
        if (existingTopic == null) {
            //topic does not exist on cluster
            if (needToBeOnCluster) {
                addResultType(MISSING)
            } else {
                addResultType(NOT_PRESENT_AS_EXPECTED)
            }
        } else {
            //topic exist on cluster
            if (!needToBeOnCluster) {
                //but should not exist
                addResultType(UNEXPECTED)
            }
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkPartitionCount(
            expectedProperties: TopicProperties, existingTopic: KafkaExistingTopic
    ) {
        val partitionCount = existingTopic.partitionsAssignments.size
        if (expectedProperties.partitionCount != partitionCount) {
            addResultType(WRONG_PARTITION_COUNT)
                    .addWrongValue(
                            WrongValueAssertion(
                                    type = WRONG_PARTITION_COUNT,
                                    key = "partition-count",
                                    expectedDefault = false,
                                    expected = expectedProperties.partitionCount,
                                    actual = partitionCount
                            )
                    )
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkReplicationFactor(
            expectedProperties: TopicProperties,
            existingTopic: KafkaExistingTopic,
            partitionReAssignments: Map<Partition, TopicPartitionReAssignment>
    ) {
        existingTopic.partitionsAssignments
                .filter { it.resolveReplicationFactor(partitionReAssignments) != expectedProperties.replicationFactor }
                .groupBy { it.replicasAssignments.size }
                .map { (numReplicas, partitions) ->
                    WrongValueAssertion(
                            type = WRONG_REPLICATION_FACTOR,
                            key = "replication-factor",
                            expectedDefault = false,
                            expected = expectedProperties.replicationFactor,
                            actual = numReplicas,
                            message = "Replicas count for partitions ${partitions.map { it.partition }} is $numReplicas"
                    )
                }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    addResultType(WRONG_REPLICATION_FACTOR)
                    addWrongValues(it)
                }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkConfigValues(
        expectedConfig: TopicConfigMap, existingTopic: KafkaExistingTopic, clusterInfo: ClusterInfo
    ) {
        val clusterServerConfig = clusterInfo.config
        existingTopic.config
                .map { ValuesTuple(it.key, it.value, expectedConfig[it.key]) }
                .map {
                    it to configValueInspector.checkConfigProperty(
                            it.key, it.actualValue, it.expectedValue, clusterServerConfig
                    )
                }
                .filter { (_, valueInspection) -> !valueInspection.valid }
                .map { (tuple, valueInspection) ->
                    WrongValueAssertion(
                            type = WRONG_CONFIG,
                            key = tuple.key,
                            expectedDefault = valueInspection.expectingClusterDefault,
                            expected = valueInspection.expectedValue,
                            actual = tuple.actualValue.value,
                    )
                }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    addResultType(WRONG_CONFIG)
                    addWrongValues(it)
                }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkValidationRules(
        topicName: TopicName,
        needToBePresentOnCluster: Boolean,
        expectedProperties: TopicProperties,
        expectedConfig: TopicConfigMap,
        clusterRef: ClusterRef,
        clusterInfo: ClusterInfo?,
        topicDescription: TopicDescription
    ) {
        val clusterDefaults = clusterInfo?.config?.let { clusterConfig ->
            TOPIC_CONFIG_PROPERTIES.associateWith {
                configValueInspector.clusterDefaultValue(clusterConfig, it)?.value
            }
        }.orEmpty()
        val topicEffectiveConfig = clusterDefaults + expectedConfig
        val clusterMetadata = ClusterMetadata(clusterRef, clusterInfo)
        val ruleViolations = configurationValidator.checkRules(
                topicName, needToBePresentOnCluster, expectedProperties, topicEffectiveConfig, clusterMetadata, topicDescription
        )
        if (ruleViolations.isNotEmpty()) {
            addResultType(CONFIG_RULE_VIOLATIONS)
            addRuleViolations(ruleViolations.map {
                RuleViolationIssue(type = CONFIG_RULE_VIOLATIONS, violation = it,)
            })
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExitingTopicValidationRules(
        topicName: TopicName,
        existingTopic: KafkaExistingTopic,
        clusterRef: ClusterRef,
        clusterInfo: ClusterInfo,
        topicDescription: TopicDescription,
        currentTopicReplicaInfos: TopicReplicaInfos?,
        partitionReAssignments: Map<Partition, TopicPartitionReAssignment>
    ) {
        val clusterMetadata = ClusterMetadata(clusterRef, clusterInfo)
        val existingTopicInfo = existingTopic.toTopicInfo(clusterInfo.nodeIds, currentTopicReplicaInfos, partitionReAssignments, partitionsReplicasAssignor)
        val existingTopicConfig = existingTopicInfo.config.mapValues { it.value.value }
        val currentConfigRuleViolations = configurationValidator.checkRules(
                topicName, true, existingTopicInfo.properties, existingTopicConfig, clusterMetadata, topicDescription
        )
        if (currentConfigRuleViolations.isNotEmpty()) {
            addResultType(CURRENT_CONFIG_RULE_VIOLATIONS)
            addCurrentConfigRuleViolations(currentConfigRuleViolations.map {
                RuleViolationIssue(type = CURRENT_CONFIG_RULE_VIOLATIONS, violation = it,)
            })
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExitingTopicDisbalance(
            existingTopic: KafkaExistingTopic, clusterInfo: ClusterInfo, currentTopicReplicaInfos: TopicReplicaInfos?
    ) {
        val currentAssignments = existingTopic.currentAssignments()
        val disbalance = partitionsReplicasAssignor.assignmentsDisbalance(
                existingAssignments = currentAssignments,
                allBrokers = clusterInfo.nodeIds,
                existingPartitionLoads = currentAssignments.partitionLoads(currentTopicReplicaInfos)
        )
        if (disbalance.replicasDisbalance > 0) {
            addResultType(PARTITION_REPLICAS_DISBALANCE)
        }
        if (disbalance.leadersDisbalance > 0) {
            addResultType(PARTITION_LEADERS_DISBALANCE)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkReAssignment(
            existingTopic: KafkaExistingTopic,
            partitionReAssignments: Map<Partition, TopicPartitionReAssignment>
    ) {
        val followerThrottleNonDefault = existingTopic.config["follower.replication.throttled.replicas"]?.default?.not()
                ?: false
        val leaderThrottleNonDefault = existingTopic.config["leader.replication.throttled.replicas"]?.default?.not()
                ?: false
        if (followerThrottleNonDefault && leaderThrottleNonDefault) {
            addResultType(HAS_REPLICATION_THROTTLING)
            if (partitionReAssignments.isEmpty()) {
                addResultType(HAS_UNVERIFIED_REASSIGNMENTS)
            }
        }
        if (partitionReAssignments.isNotEmpty()) {
            addResultType(RE_ASSIGNMENT_IN_PROGRESS)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkPreferredReplicaLeaders(
            existingTopic: KafkaExistingTopic
    ) {
        val needsLeaderElection = existingTopic.partitionsAssignments
                .partitionsToReElectLeader()
                .isNotEmpty()
        if (needsLeaderElection) {
            addResultType(NEEDS_LEADER_ELECTION)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkOutOfSyncReplicas(
            existingTopic: KafkaExistingTopic
    ) {
        val hasOutOfSyncReplicas = existingTopic.partitionsAssignments.asSequence()
                .map { it.replicasAssignments }
                .flatten()
                .any { !it.inSyncReplica }
        if (hasOutOfSyncReplicas) {
            addResultType(HAS_OUT_OF_SYNC_REPLICAS)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkAffectingAclRules(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) {
        val aclRules = aclLinkResolver.findTopicAffectingAclRules(topicName, clusterIdentifier)
        affectingAclRules(aclRules)
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExternalInspectors(
        topicName: TopicName, clusterRef: ClusterRef,
    ): Map<String, Any> {
        val externInfo = mutableMapOf<String, Any>()
        externalInspectors.ifPresent { inspectors ->
            inspectors.forEach {
                it.inspectTopic(topicName, clusterRef, object : TopicExternalInspectCallback {

                    override fun addStatusType(statusType: TopicInspectionResultType) {
                        addResultType(statusType)
                    }

                    override fun setExternalInfo(info: Any) {
                        externInfo[it.name] = info
                    }
                })
            }
        }
        return externInfo
    }

    private fun TopicOnClusterInspectionResult.Builder.prepareAndBuild(): TopicOnClusterInspectionResult {
        with(types()) {
            if (isEmpty() || all { it.valid && (it != NOT_PRESENT_AS_EXPECTED && it != CLUSTER_DISABLED) }) {
                addOkResultType()
            }
        }
        val availableActions = types()
                .map { type ->
                    when (type) {
                        MISSING -> listOf(CREATE_TOPIC)
                        UNEXPECTED -> listOf(MANUAL_EDIT, SUGGESTED_EDIT, DELETE_TOPIC_ON_KAFKA)
                        UNKNOWN -> listOf(IMPORT_TOPIC) + if (INTERNAL !in types()) listOf(DELETE_TOPIC_ON_KAFKA) else emptyList()
                        WRONG_PARTITION_COUNT -> listOf(MANUAL_EDIT, SUGGESTED_EDIT, ALTER_PARTITION_COUNT)
                        WRONG_REPLICATION_FACTOR -> listOf(MANUAL_EDIT, SUGGESTED_EDIT, ALTER_REPLICATION_FACTOR)
                        WRONG_CONFIG -> listOf(MANUAL_EDIT, SUGGESTED_EDIT, ALTER_TOPIC_CONFIG)
                        CONFIG_RULE_VIOLATIONS, CURRENT_CONFIG_RULE_VIOLATIONS -> listOf(MANUAL_EDIT, SUGGESTED_EDIT, FIX_VIOLATIONS_EDIT)
                        PARTITION_REPLICAS_DISBALANCE, PARTITION_LEADERS_DISBALANCE -> listOf(RE_BALANCE_ASSIGNMENTS)
                        HAS_UNVERIFIED_REASSIGNMENTS, HAS_OUT_OF_SYNC_REPLICAS, NEEDS_LEADER_ELECTION, RE_ASSIGNMENT_IN_PROGRESS -> listOf(INSPECT_TOPIC)
                        else -> emptyList()
                    }
                }
                .flatten()
                .distinct()
        availableActions(availableActions)
        return build()
    }

    private data class ValuesTuple(
            val key: String,
            val actualValue: ConfigValue,
            val expectedValue: String?
    )

}