package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.kafkastate.TopicReplicaInfos
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.AvailableAction.*
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
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
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.topic-inspect")
class TopicInspectorProperties {
    var disabledInspectors = mutableListOf<Class<in TopicExternalInspector>>()
    var enabledInspectors = mutableListOf<Class<in TopicExternalInspector>>()
}

@Component
class TopicIssuesInspector(
    private val configValueInspector: ConfigValueInspector,
    private val configurationValidator: TopicConfigurationValidator,
    private val partitionsReplicasAssignor: PartitionsReplicasAssignor,
    private val resourceUsagesInspector: RequiredResourcesInspector,
    private val aclLinkResolver: AclLinkResolver,
    externalInspectors: List<TopicExternalInspector>,
    properties: TopicInspectorProperties,
) {

    private val inspectors = externalInspectors
        .filterEnabled(properties.enabledInspectors)
        .filterDisabled(properties.disabledInspectors)

    fun inspectTopicDataOnClusterData(
        topicName: TopicName,
        topicDescription: TopicDescription?,
        existingTopic: KafkaExistingTopic?,
        currentTopicReplicaInfos: TopicReplicaInfos?,
        partitionReAssignments: Map<Partition, TopicPartitionReAssignment>,
        clusterRef: ClusterRef,
        latestClusterState: StateData<KafkaClusterState>
    ): TopicClusterStatus = inspectTopicDataOnClusterData(
        TopicInspectCtx(
            topicName, clusterRef, latestClusterState, topicDescription, existingTopic,
            currentTopicReplicaInfos, partitionReAssignments,
        )
    )

    fun inspectTopicDataOnClusterData(ctx: TopicInspectCtx): TopicClusterStatus {
        return with(TopicOnClusterInspectionResult.Builder()) {
            checkExists(ctx)
            checkClusterVisibility(ctx)
            checkTopicUnknown(ctx)
            checkTopicUnavailable(ctx)
            inspectRegistryTopic(ctx)
            val configEntryStatuses = ctx.computeConfigEntryStatuses()
            checkTopicInternal(ctx)
            checkExitingTopicDisbalance(ctx)
            val existingTopicInfo = ctx.cache { computeExistingTopicInfo() }
            val resourceRequiredUsages = ctx.inspectRequiredResourcesUsage()
            checkAffectingAclRules(ctx)
            val externInfo = checkExternalInspectors(ctx)
            TopicClusterStatus(
                status = prepareAndBuild(),
                lastRefreshTime = ctx.latestClusterState.lastRefreshTime,
                clusterIdentifier = ctx.clusterRef.identifier,
                clusterTags = ctx.clusterRef.tags,
                existingTopicInfo = existingTopicInfo,
                configEntryStatuses = configEntryStatuses,
                resourceRequiredUsages = resourceRequiredUsages,
                currentTopicReplicaInfos = ctx.currentTopicReplicaInfos,
                currentReAssignments = ctx.partitionReAssignments,
                externInspectInfo = externInfo,
            )
        }
    }

    private fun TopicInspectCtx.computeConfigEntryStatuses(): Map<String, ValueInspection>? =
        if (topicDescription != null && clusterInfo != null && existingTopic != null) {
            val expectedConfig = topicDescription.configForCluster(clusterRef)
            existingTopic.config.mapValues { (key, value) ->
                configValueInspector.checkConfigProperty(key, value, expectedConfig[key], clusterInfo.config)
            }
        } else {
            null
        }

    private fun TopicOnClusterInspectionResult.Builder.inspectRegistryTopic(ctx: TopicInspectCtx) {
        if (ctx.topicDescription == null) {
            return
        }
        val expectedProperties = ctx.topicDescription.propertiesForCluster(ctx.clusterRef)
        val expectedConfig = ctx.topicDescription.configForCluster(ctx.clusterRef)
        val needToBeOnCluster = ctx.topicDescription.presence.needToBeOnCluster(ctx.clusterRef)
        checkValidationRules(needToBeOnCluster, expectedProperties, expectedConfig, ctx)
        checkPresence(ctx.topicDescription.presence, ctx)
        if (ctx.clusterInfo != null && ctx.existingTopic != null) {
            checkPartitionCount(expectedProperties, ctx.existingTopic)
            checkReplicationFactor(expectedProperties, ctx.existingTopic, ctx.partitionReAssignments)
            checkConfigValues(expectedConfig, ctx.existingTopic, ctx.clusterInfo)
            checkExitingTopicValidationRules(ctx)
            checkPreferredReplicaLeaders(ctx.existingTopic)
            checkOutOfSyncReplicas(ctx.existingTopic)
            checkReAssignment(ctx.existingTopic, ctx.partitionReAssignments)
        }
    }

    private fun TopicInspectCtx.computeExistingTopicInfo(): ExistingTopicInfo? = if (existingTopic != null && clusterInfo != null) {
        existingTopic.toTopicInfo(
            clusterInfo.brokerIds, currentTopicReplicaInfos, partitionReAssignments, partitionsReplicasAssignor,
        )
    } else {
        null
    }

    private fun TopicInspectCtx.inspectRequiredResourcesUsage(): OptionalValue<TopicResourceRequiredUsages> =
        if (topicDescription?.presence?.needToBeOnCluster(clusterRef) == true) {
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

    private fun TopicOnClusterInspectionResult.Builder.checkExists(ctx: TopicInspectCtx) {
        if (ctx.latestClusterState.stateType == StateType.VISIBLE) {
            exists(ctx.existingTopic != null)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkClusterVisibility(ctx: TopicInspectCtx) {
        when (ctx.latestClusterState.stateType) {
            StateType.VISIBLE -> Unit
            StateType.DISABLED -> addResultType(CLUSTER_DISABLED)
            else -> addResultType(CLUSTER_UNREACHABLE)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicUnknown(ctx: TopicInspectCtx) {
        if (ctx.topicDescription == null && ctx.existingTopic != null) {
            addResultType(UNKNOWN)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicInternal(ctx: TopicInspectCtx) {
        val existingTopic = ctx.existingTopic ?: return
        if (existingTopic.internal) {
            addResultType(INTERNAL)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkTopicUnavailable(ctx: TopicInspectCtx) {
        if (ctx.latestClusterState.stateType == StateType.VISIBLE && ctx.topicDescription == null && ctx.existingTopic == null) {
            addResultType(UNAVAILABLE)
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkPresence(presence: Presence, ctx: TopicInspectCtx) {
        if (ctx.latestClusterState.stateType == StateType.DISABLED || ctx.clusterInfo == null) {
            return
        }
        val needToBeOnCluster = presence.needToBeOnCluster(ctx.clusterRef)
        if (ctx.existingTopic == null) {
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
            addWrongValue(
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
        needToBePresentOnCluster: Boolean,
        expectedProperties: TopicProperties,
        expectedConfig: TopicConfigMap,
        ctx: TopicInspectCtx,
    ) {
        if (ctx.topicDescription == null) {
            return
        }
        val clusterDefaults = ctx.clusterInfo?.config?.let { clusterConfig ->
            TOPIC_CONFIG_PROPERTIES.associateWith {
                configValueInspector.clusterDefaultValue(clusterConfig, it)?.value
            }
        }.orEmpty()
        val topicEffectiveConfig = clusterDefaults + expectedConfig
        val clusterMetadata = ClusterMetadata(ctx.clusterRef, ctx.clusterInfo)
        val ruleViolations = configurationValidator.checkRules(
            ctx.topicName, needToBePresentOnCluster, expectedProperties, topicEffectiveConfig, clusterMetadata,
            ctx.topicDescription, ctx.cache { computeExistingTopicInfo() },
        )
        if (ruleViolations.isNotEmpty()) {
            addResultType(CONFIG_RULE_VIOLATIONS)
            addRuleViolations(ruleViolations.map {
                RuleViolationIssue(type = CONFIG_RULE_VIOLATIONS, violation = it,)
            })
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExitingTopicValidationRules(ctx: TopicInspectCtx) {
        if (ctx.topicDescription == null) {
            return
        }
        val existingTopicInfo = ctx.cache { computeExistingTopicInfo() } ?: return
        val clusterMetadata = ClusterMetadata(ctx.clusterRef, ctx.clusterInfo)
        val existingTopicConfig = existingTopicInfo.config.mapValues { it.value.value }
        val currentConfigRuleViolations = configurationValidator.checkRules(
            ctx.topicName, true, existingTopicInfo.properties, existingTopicConfig, clusterMetadata,
            ctx.topicDescription, ctx.cache { computeExistingTopicInfo() },
        )
        if (currentConfigRuleViolations.isNotEmpty()) {
            addResultType(CURRENT_CONFIG_RULE_VIOLATIONS)
            addCurrentConfigRuleViolations(currentConfigRuleViolations.map {
                RuleViolationIssue(type = CURRENT_CONFIG_RULE_VIOLATIONS, violation = it)
            })
        }
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExitingTopicDisbalance(ctx: TopicInspectCtx) {
        if (ctx.clusterInfo == null || ctx.existingTopic == null) {
            return
        }
        val currentAssignments = ctx.existingTopic.currentAssignments()
        val disbalance = partitionsReplicasAssignor.assignmentsDisbalance(
            existingAssignments = currentAssignments,
            allBrokers = ctx.clusterInfo.brokerIds,
            existingPartitionLoads = currentAssignments.partitionLoads(ctx.currentTopicReplicaInfos)
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

    private fun TopicOnClusterInspectionResult.Builder.checkAffectingAclRules(ctx: TopicInspectCtx) {
        val aclRules = ctx.cache {
            aclLinkResolver.findTopicAffectingAclRules(topicName, clusterRef.identifier)
        }
        affectingAclRules(aclRules)
    }

    private fun TopicOnClusterInspectionResult.Builder.checkExternalInspectors(
        ctx: TopicInspectCtx,
    ): Map<String, Any> {
        val externInfo = mutableMapOf<String, Any>()
        inspectors.forEach {
            it.inspectTopic(ctx, object : TopicExternalInspectCallback {

                override fun addStatusType(statusType: TopicInspectionResultType) {
                    addResultType(statusType)
                }

                override fun addDescribedStatusType(statusTypeDescription: NamedTypeCauseDescription<TopicInspectionResultType>) {
                    addResultType(statusTypeDescription.type)
                    addTypeDescription(statusTypeDescription)
                }

                override fun setExternalInfo(info: Any) {
                    externInfo[it.name] = info
                }
            })
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