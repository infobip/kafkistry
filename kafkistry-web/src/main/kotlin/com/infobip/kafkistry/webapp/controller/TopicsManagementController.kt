package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.toPartitionReplicasMap
import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.TopicInspectionResultType.Companion.HAS_UNVERIFIED_REASSIGNMENTS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_CONFIG_UPDATE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_CONFIG_UPDATES
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_CREATE_MISSING
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_CREATE_MISSING_ON_CLUSTERS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_DELETE_UNWANTED_ON_CLUSTER
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_DELETE_UNWANTED_ON_CLUSTERS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_RE_BALANCE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_RE_BALANCE_FORM
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_RE_ELECT_REPLICA_LEADERS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_BULK_VERIFY_RE_ASSIGNMENTS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_CONFIG_SET
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_CONFIG_UPDATE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_CREATE_MISSING
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT_INPUT
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_DELETE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_EXCLUDED_BROKERS_RE_ASSIGNMENT
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_PARTITION_COUNT_CHANGE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_REPLICATION_FACTOR_CHANGE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_RE_BALANCE
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS_FORM
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT_UNWANTED_LEADER_RE_ASSIGNMENT
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.SessionAttribute
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$TOPICS_MANAGEMENT")
class TopicsManagementController(
        private val clustersApi: ClustersApi,
        private val topicsApi: TopicsApi,
        private val inspectApi: InspectApi,
        private val consumersApi: ConsumersApi,
        private val topicOffsetsApi: TopicOffsetsApi,
        private val suggestionApi: SuggestionApi,
        private val topicReplicasApi: TopicReplicasApi,
        private val existingValuesApi: ExistingValuesApi,
) : BaseController() {

    private fun ClustersApi.getClusterStateValue(clusterIdentifier: KafkaClusterIdentifier): KafkaClusterState {
        return getClusterState(clusterIdentifier).value()
    }

    @GetMapping(TOPICS_MANAGEMENT_CREATE_MISSING)
    fun showCreateMissingTopic(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val topicInfo = suggestionApi.expectedTopicInfoOnCluster(topicName, clusterIdentifier)
        val cluster = clustersApi.getCluster(clusterIdentifier)
        return ModelAndView("management/missingTopicCreation", mutableMapOf(
                "topicInfo" to topicInfo,
                "cluster" to cluster
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_CREATE_MISSING)
    fun showBulkCreateMissingTopics(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val missingTopics = inspectApi.listMissingTopics(clusterIdentifier).map {
            suggestionApi.expectedTopicInfoOnCluster(it.name, clusterIdentifier)
        }
        val cluster = clustersApi.getCluster(clusterIdentifier)
        return ModelAndView("management/missingTopicsBulkCreation", mutableMapOf(
                "missingTopics" to missingTopics,
                "cluster" to cluster
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_CREATE_MISSING_ON_CLUSTERS)
    fun showBulkCreateMissingTopicOnClusters(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val topic = topicsApi.getTopic(topicName)
        val clusterExpectedTopicInfos = inspectApi.inspectTopic(topicName).statusPerClusters
                .filter { AvailableAction.CREATE_TOPIC in it.status.availableActions }
                .associate { it.clusterIdentifier to suggestionApi.expectedTopicInfoOnCluster(topicName, it.clusterIdentifier) }
        return ModelAndView("management/missingTopicBulkCreationOnClusters", mutableMapOf(
                "topic" to topic,
                "clusterExpectedTopicInfos" to clusterExpectedTopicInfos
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_DELETE_UNWANTED_ON_CLUSTERS)
    fun showBulkDeleteUnwantedTopicOnClusters(
        @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val unwantedTopicClusters = inspectApi.inspectTopic(topicName).statusPerClusters
            .filter { AvailableAction.DELETE_TOPIC_ON_KAFKA in it.status.availableActions }
            .map { it.clusterIdentifier }
        val clusterConsumerGroups = unwantedTopicClusters.associateWith {
            consumersApi.clusterTopicConsumers(it, topicName)
        }
        val clusterTopicOffsets = unwantedTopicClusters.associateWith {
            topicOffsetsApi.getTopicOffsets(topicName, it)
        }
        return ModelAndView("management/unwantedTopicBulkDeletionOnClusters", mutableMapOf(
            "topicName" to topicName,
            "unwantedTopicClusters" to unwantedTopicClusters,
            "clusterConsumerGroups" to clusterConsumerGroups,
            "clusterTopicOffsets" to clusterTopicOffsets,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_DELETE_UNWANTED_ON_CLUSTER)
    fun showBulkDeleteUnwantedTopicsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val unwantedTopics = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
            .statusPerTopics.orEmpty()
            .filter { AvailableAction.DELETE_TOPIC_ON_KAFKA in it.topicClusterStatus.status.availableActions }
            .map { it.topicName }
        val topicsConsumerGroups = unwantedTopics.associateWith {
            consumersApi.clusterTopicConsumers(clusterIdentifier, it)
        }
        val topicsTopicOffsets = unwantedTopics.associateWith {
            topicOffsetsApi.getTopicOffsets(it, clusterIdentifier)
        }
        return ModelAndView("management/unwantedTopicsBulkDeletionOnCluster", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "unwantedTopics" to unwantedTopics,
            "topicsConsumerGroups" to topicsConsumerGroups,
            "topicsTopicOffsets" to topicsTopicOffsets,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_RE_ELECT_REPLICA_LEADERS)
    fun showBulkReElectReplicaLeaders(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val leaderReElectionTopicsPartitions = inspectApi.listTopicsForLeaderReElection(clusterIdentifier)
                .associate { it.name to it.partitionsAssignments.partitionsToReElectLeader() }
        val cluster = clustersApi.getCluster(clusterIdentifier)
        return ModelAndView("management/topicsLeaderBulkReElection", mutableMapOf(
                "topicsPartitions" to leaderReElectionTopicsPartitions,
                "cluster" to cluster
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_VERIFY_RE_ASSIGNMENTS)
    fun showBulkVerifyReAssignments(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val topicsToVerifyReAssignments = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
                .let { inspection ->
                    inspection.statusPerTopics ?: throw KafkistryIllegalStateException(
                            "Can't show topics which have unverified assignments because cluster status is: " + inspection.clusterState
                    )
                }
                .filter { HAS_UNVERIFIED_REASSIGNMENTS in it.topicClusterStatus.status.types }
                .map { it.topicName }
        return ModelAndView("management/bulkVerifyReAssignments", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "topicsToVerifyReAssignments" to topicsToVerifyReAssignments,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_RE_BALANCE_FORM)
    fun showBulkReBalanceTopics(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val topicsStatuses = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
        val clusterInfo = topicsStatuses.clusterInfo ?: throw KafkistryIllegalStateException(
            "Can't show re-assign form since cluster '$clusterIdentifier' has state: '${topicsStatuses.clusterState}'"
        )
        val topicNames = topicsStatuses.statusPerTopics.orEmpty().map { it.topicName }
        return ModelAndView("management/bulkReBalanceTopicsForm", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "topicNames" to topicNames,
            "clusterInfo" to clusterInfo,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_RE_BALANCE)
    fun showBulkReBalanceTopics(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam(name = "reBalanceMode", required = false) reBalanceMode: ReBalanceMode?,
        @RequestParam(name = "objectives", required = false) reAssignObjectives: List<BulkReAssignmentOptions.ReAssignObjective>? = null,
        @RequestParam(name = "includeTopicNamePattern", required = false) includeTopicNamePattern: String?,
        @RequestParam(name = "excludeTopicNamePattern", required = false) excludeTopicNamePattern: String?,
        @RequestParam(name = "topicSelectOrder", required = false) topicSelectOrder: BulkReAssignmentOptions.TopicSelectOrder?,
        @RequestParam(name = "topicBy", required = false) topicBy: BulkReAssignmentOptions.TopicBy?,
        @RequestParam(name = "topicCountLimit", required = false) topicCountLimit: Int?,
        @RequestParam(name = "topicPartitionCountLimit", required = false) topicPartitionCountLimit: Int?,
        @RequestParam(name = "totalMigrationBytesLimit", required = false) totalMigrationBytesLimit: Long?,
        @RequestParam(name = "excludedBrokerIds", required = false) excludedBrokerIds: List<BrokerId>? = null,
    ): ModelAndView {
        val bulkReBalanceTopics = suggestionApi.bulkReBalanceTopics(
            clusterIdentifier, BulkReAssignmentOptions(
                reBalanceMode = reBalanceMode ?: ReBalanceMode.REPLICAS_THEN_LEADERS,
                objectives = reAssignObjectives ?: BulkReAssignmentOptions.ReAssignObjective.entries,
                includeTopicNamePattern = includeTopicNamePattern,
                excludeTopicNamePattern  = excludeTopicNamePattern,
                topicSelectOrder = topicSelectOrder ?: BulkReAssignmentOptions.TopicSelectOrder.TOP,
                topicBy = topicBy ?: BulkReAssignmentOptions.TopicBy.MIGRATION_BYTES,
                topicCountLimit = topicCountLimit?.takeIf { it >= 0 } ?: Int.MAX_VALUE,
                topicPartitionCountLimit = topicPartitionCountLimit?.takeIf { it >= 0 } ?: Int.MAX_VALUE,
                totalMigrationBytesLimit = totalMigrationBytesLimit?.takeIf { it >= 0 } ?: Long.MAX_VALUE,
                excludedBrokerIds = excludedBrokerIds.orEmpty(),
            )
        )
        val clusterTopicsReplicas = topicReplicasApi.getClusterTopicsReplicas(clusterIdentifier)
        return ModelAndView("management/bulkReBalanceTopics", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterInfo" to bulkReBalanceTopics.clusterInfo,
            "stats" to bulkReBalanceTopics.stats,
            "topicsReBalanceSuggestions" to bulkReBalanceTopics.topicsReBalanceSuggestions,
            "topicsReBalanceStatuses" to bulkReBalanceTopics.topicsReBalanceStatuses,
            "totalDataMigration" to bulkReBalanceTopics.totalDataMigration,
            "clusterTopicsReplicas" to clusterTopicsReplicas,
            "selectionLimitedBy" to bulkReBalanceTopics.selectionLimitedBy,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_DELETE)
    fun showDeleteTopicOnCluster(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterInfo = clustersApi.getCluster(clusterIdentifier)
        val topicConsumerGroups = consumersApi.clusterTopicConsumers(clusterIdentifier, topicName)
        val topicOffsets = topicOffsetsApi.getTopicOffsets(topicName, clusterIdentifier)
        return ModelAndView("management/clusterTopicDeletion", mutableMapOf(
            "topicName" to topicName,
            "clusterInfo" to clusterInfo,
            "topicConsumerGroups" to topicConsumerGroups,
            "topicOffsets" to topicOffsets,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_CONFIG_UPDATE)
    fun showTopicConfigUpdate(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val cluster = clustersApi.getCluster(clusterIdentifier)
        val configChanges = inspectApi.inspectTopicNeededConfigChangesOnCluster(topicName, clusterIdentifier)
        return ModelAndView("management/topicConfigUpdate", mutableMapOf(
                "topicName" to topicName,
                "cluster" to cluster,
                "configChanges" to configChanges
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_CONFIG_UPDATES)
    fun showBulkConfigUpdates(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val cluster = clustersApi.getCluster(clusterIdentifier)
        val topicsConfigChanges = inspectApi.inspectTopicsNeededConfigChangesOnCluster(clusterIdentifier)
        return ModelAndView("management/bulkTopicsConfigsUpdates", mutableMapOf(
            "topicsConfigChanges" to topicsConfigChanges,
            "cluster" to cluster
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_BULK_CONFIG_UPDATE)
    fun showBulkTopicConfigUpdate(
            @RequestParam("topicName") topicName: TopicName,
    ): ModelAndView {
        val clusterConfigChanges = inspectApi.inspectTopic(topicName).statusPerClusters
            .filter { AvailableAction.ALTER_TOPIC_CONFIG in it.status.availableActions }
            .associate { it.clusterIdentifier to inspectApi.inspectTopicNeededConfigChangesOnCluster(topicName, it.clusterIdentifier) }
        return ModelAndView("management/topicConfigBulkUpdate", mutableMapOf(
                "topicName" to topicName,
                "clusterConfigChanges" to clusterConfigChanges,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_PARTITION_COUNT_CHANGE)
    fun showTopicPartitionCountChange(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val changesSuggestion = suggestionApi.generateTopicPartitionPropertiesChangesOnCluster(
                topicName, clusterIdentifier
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = changesSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(changesSuggestion.partitionCountChange.change, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/partitionCountChange", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "partitionCountChange" to changesSuggestion.partitionCountChange,
                "assignmentStatus" to assignmentStatus,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_REPLICATION_FACTOR_CHANGE)
    fun showTopicReplicationFactorChange(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val changesSuggestion = suggestionApi.generateTopicPartitionPropertiesChangesOnCluster(
                topicName, clusterIdentifier
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = changesSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(changesSuggestion.replicationFactorChange.change, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/replicationFactorChange", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "replicationFactorChange" to changesSuggestion.replicationFactorChange,
                "assignmentStatus" to assignmentStatus,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_RE_BALANCE)
    fun showTopicReBalance(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("reBalanceMode") reBalanceMode: ReBalanceMode
    ): ModelAndView {
        val reBalanceSuggestion = suggestionApi.reBalanceAssignments(
                topicName, clusterIdentifier, reBalanceMode
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = reBalanceSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(reBalanceSuggestion.assignmentsChange, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/reBalanceTopic", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "reBalanceSuggestion" to reBalanceSuggestion,
                "assignmentStatus" to assignmentStatus,
                "reBalanceMode" to reBalanceMode,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_UNWANTED_LEADER_RE_ASSIGNMENT)
    fun showUnwantedLeaderReAssignment(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("unwantedBrokerId") unwantedBrokerId: BrokerId
    ): ModelAndView {
        val reBalanceSuggestion = suggestionApi.reAssignTopicUnwantedLeader(
                topicName, clusterIdentifier, unwantedBrokerId
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = reBalanceSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(reBalanceSuggestion.assignmentsChange, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/reAssignTopicUnwantedLeader", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "reBalanceSuggestion" to reBalanceSuggestion,
                "assignmentStatus" to assignmentStatus,
                "unwantedBrokerId" to unwantedBrokerId,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_EXCLUDED_BROKERS_RE_ASSIGNMENT)
    fun showExcludedBrokersReAssignment(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("excludedBrokerIds") excludedBrokerIds: List<BrokerId>
    ): ModelAndView {
        val reBalanceSuggestion = suggestionApi.reAssignTopicWithoutBrokers(
                topicName, clusterIdentifier, excludedBrokerIds.toTypedArray()
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = reBalanceSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(reBalanceSuggestion.assignmentsChange, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/reAssignTopicExcludedBrokers", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "reBalanceSuggestion" to reBalanceSuggestion,
                "assignmentStatus" to assignmentStatus,
                "excludedBrokerIds" to excludedBrokerIds,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT_INPUT)
    fun showCustomReAssignmentInput(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val topicInspection = inspectApi.inspectTopicOnCluster(topicName, clusterIdentifier)
        val existingAssignments = topicInspection.existingTopicInfo
                ?.partitionsAssignments
                ?.toPartitionReplicasMap()
                ?: throw KafkistryIllegalStateException(
                        "Topic '$topicName' on '$clusterIdentifier' has state: ${topicInspection.status.types}, can't get current assignments"
                )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/customReAssignmentInput", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "existingAssignments" to existingAssignments,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT)
    fun showCustomReAssignment(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("assignment") assignmentString: String    //example: 0:1,2,3;1:2,3,1;2:3,1,2
    ): ModelAndView {
        val newAssignments: Map<Partition, List<BrokerId>> = assignmentString.trim()
                .split(";")
                .map { it.split(":", limit = 2) }
                .associate { (partition, replicas) ->
                    partition.toInt() to replicas.split(",").map { it.toInt() }
                }
        val reBalanceSuggestion = suggestionApi.customReAssignInspect(
                topicName, clusterIdentifier, newAssignments
        )
        val clusterInfo = clustersApi.getClusterStateValue(clusterIdentifier).clusterInfo
        val assignmentStatus = reBalanceSuggestion.existingTopicInfo.partitionsAssignments
                .toAssignmentsInfo(reBalanceSuggestion.assignmentsChange, clusterInfo.assignableBrokers())
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        return ModelAndView("management/reAssignTopicCustomAssignment", mutableMapOf(
                "topicName" to topicName,
                "clusterInfo" to clusterInfo,
                "reBalanceSuggestion" to reBalanceSuggestion,
                "assignmentStatus" to assignmentStatus,
                "topicReplicas" to topicReplicas
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS_FORM)
    fun showThrottleBrokerPartitionsForm(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val (clusterInfo, topics) = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
            .let {
                val clusterInfo = it.clusterInfo
                val statusPerTopics = it.statusPerTopics
                if (clusterInfo == null || statusPerTopics == null) {
                    throw KafkistryIllegalStateException("Can't get cluster info and topics because cluster state is: " + it.clusterState)
                }
                clusterInfo to statusPerTopics.filter { it.topicClusterStatus.existingTopicInfo != null }
            }
        val topicsOffsets = topicOffsetsApi.getTopicsOffsets(clusterIdentifier)
        val topicsReplicas = topicReplicasApi.getClusterTopicsReplicas(clusterIdentifier)
        return ModelAndView("management/throttleBrokerPartitionsForm", mutableMapOf(
            "clusterIdentifier" to clusterIdentifier,
            "clusterInfo" to clusterInfo,
            "topics" to topics,
            "topicsOffsets"  to topicsOffsets,
            "topicsReplicas" to topicsReplicas,
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS)
    fun showThrottleBrokerPartitions(
            @SessionAttribute(name = ClustersManagementSuggestionApi.THROTTLE_BROKER_TOPIC_PARTITIONS, required = true)
            throttleBrokerTopicPartitionsSuggestion: ThrottleBrokerTopicPartitionsSuggestion
    ): ModelAndView {
        val clusterIdentifier = throttleBrokerTopicPartitionsSuggestion.throttleRequest.clusterIdentifier
        val clusterInfo = inspectApi.inspectTopicsOnCluster(clusterIdentifier)
                .let {
                    it.clusterInfo ?: throw KafkistryIllegalStateException(
                            "Can't show throttle suggestion because cluster state is: " + it.clusterState
                    )
                }
        return ModelAndView("management/throttleBrokerPartitions", mutableMapOf(
                "clusterIdentifier" to clusterIdentifier,
                "clusterInfo" to clusterInfo,
                "throttleBrokerTopicPartitionsSuggestion" to throttleBrokerTopicPartitionsSuggestion
        ))
    }

    @GetMapping(TOPICS_MANAGEMENT_CONFIG_SET)
    fun showTopicConfigSet(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val cluster = clustersApi.getCluster(clusterIdentifier)
        val topicConfig = inspectApi.inspectTopicOnCluster(topicName, clusterIdentifier)
                .let {
                    it.existingTopicInfo?.config ?: throw KafkistryIllegalStateException(
                            "Can't update topic config for topic which has state: " + it.status.types
                    )
                }
        return ModelAndView("management/topicConfigSet", mutableMapOf(
                "topicName" to topicName,
                "cluster" to cluster,
                "topicConfig" to topicConfig,
                "topicConfigDoc" to existingValuesApi.all().topicConfigDoc,
        ))
    }

}