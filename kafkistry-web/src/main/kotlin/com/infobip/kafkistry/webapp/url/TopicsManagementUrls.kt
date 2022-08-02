package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class TopicsManagementUrls(base: String) : BaseUrls() {

    companion object {
        const val TOPICS_MANAGEMENT = "/topics/management"
        const val TOPICS_MANAGEMENT_CREATE_MISSING = "/create-missing-topic"
        const val TOPICS_MANAGEMENT_DELETE = "/delete-topic"
        const val TOPICS_MANAGEMENT_CONFIG_UPDATE = "/topic-config-update"
        const val TOPICS_MANAGEMENT_BULK_CONFIG_UPDATE = "/bulk-topic-config-update"
        const val TOPICS_MANAGEMENT_PARTITION_COUNT_CHANGE = "/topic-partition-count-change"
        const val TOPICS_MANAGEMENT_REPLICATION_FACTOR_CHANGE = "/topic-replication-factor-change"
        const val TOPICS_MANAGEMENT_RE_BALANCE = "/topic-re-balance"
        const val TOPICS_MANAGEMENT_UNWANTED_LEADER_RE_ASSIGNMENT = "/topic-unwanted-leader-re-assignment"
        const val TOPICS_MANAGEMENT_EXCLUDED_BROKERS_RE_ASSIGNMENT = "/topic-excluded-brokers-re-assignment"
        const val TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT_INPUT = "/custom-re-assignment-input"
        const val TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT = "/custom-re-assignment"
        const val TOPICS_MANAGEMENT_BULK_CREATE_MISSING = "/bulk-create-missing-topics"
        const val TOPICS_MANAGEMENT_BULK_CREATE_MISSING_ON_CLUSTERS = "/bulk-create-missing-topic-on-clusters"
        const val TOPICS_MANAGEMENT_BULK_RE_ELECT_REPLICA_LEADERS = "/bulk-re-elect-replica-leaders"
        const val TOPICS_MANAGEMENT_BULK_CONFIG_UPDATES = "/bulk-topics-config-updates"
        const val TOPICS_MANAGEMENT_BULK_VERIFY_RE_ASSIGNMENTS = "/bulk-verify-re-assignments"
        const val TOPICS_MANAGEMENT_BULK_RE_BALANCE_FORM = "/bulk-re-balance-topics-form"
        const val TOPICS_MANAGEMENT_BULK_RE_BALANCE = "/bulk-re-balance-topics"
        const val TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS_FORM = "/throttle-broker-partitions-form"
        const val TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS = "/throttle-broker-partitions"
        const val TOPICS_MANAGEMENT_CONFIG_SET = "/topic-config-set"
    }

    private val showCreateMissingTopic = Url(
        "$base$TOPICS_MANAGEMENT_CREATE_MISSING", listOf("topicName", "clusterIdentifier")
    )
    private val showDeleteTopicOnCluster = Url(
        "$base$TOPICS_MANAGEMENT_DELETE", listOf("topicName", "clusterIdentifier")
    )
    private val showTopicConfigUpdate = Url(
        "$base$TOPICS_MANAGEMENT_CONFIG_UPDATE", listOf("topicName", "clusterIdentifier")
    )
    private val showTopicConfigBulkUpdate = Url(
        "$base$TOPICS_MANAGEMENT_BULK_CONFIG_UPDATE", listOf("topicName")
    )
    private val showTopicPartitionCountChange = Url(
        "$base$TOPICS_MANAGEMENT_PARTITION_COUNT_CHANGE", listOf("topicName", "clusterIdentifier")
    )
    private val showTopicReplicationFactorChange = Url(
        "$base$TOPICS_MANAGEMENT_REPLICATION_FACTOR_CHANGE", listOf("topicName", "clusterIdentifier")
    )
    private val showTopicReBalance = Url(
        "$base$TOPICS_MANAGEMENT_RE_BALANCE", listOf("topicName", "clusterIdentifier", "reBalanceMode")
    )
    private val showTopicUnwantedLeaderReAssignment = Url(
        "$base$TOPICS_MANAGEMENT_UNWANTED_LEADER_RE_ASSIGNMENT", listOf("topicName", "clusterIdentifier", "unwantedBrokerId")
    )
    private val showTopicExcludedBrokersReAssignment = Url(
        "$base$TOPICS_MANAGEMENT_EXCLUDED_BROKERS_RE_ASSIGNMENT", listOf("topicName", "clusterIdentifier", "excludedBrokerIds")
    )
    private val showCustomReAssignmentInput = Url(
        "$base$TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT_INPUT", listOf("topicName", "clusterIdentifier")
    )
    private val showCustomReAssignment = Url(
        "$base$TOPICS_MANAGEMENT_CUSTOM_RE_ASSIGNMENT", listOf("topicName", "clusterIdentifier", "assignment")
    )
    private val showBulkCreateMissingTopics = Url(
        "$base$TOPICS_MANAGEMENT_BULK_CREATE_MISSING", listOf("clusterIdentifier")
    )
    private val showBulkCreateMissingTopicOnClusters = Url(
        "$base$TOPICS_MANAGEMENT_BULK_CREATE_MISSING_ON_CLUSTERS", listOf("topicName")
    )
    private val showBulkReElectReplicaLeaders = Url(
        "$base$TOPICS_MANAGEMENT_BULK_RE_ELECT_REPLICA_LEADERS", listOf("clusterIdentifier")
    )
    private val showBulkConfigUpdates = Url(
        "$base$TOPICS_MANAGEMENT_BULK_CONFIG_UPDATES", listOf("clusterIdentifier")
    )
    private val showBulkVerifyReAssignments = Url(
        "$base$TOPICS_MANAGEMENT_BULK_VERIFY_RE_ASSIGNMENTS", listOf("clusterIdentifier")
    )
    private val showBulkReBalanceTopicsForm = Url(
        "$base$TOPICS_MANAGEMENT_BULK_RE_BALANCE_FORM", listOf("clusterIdentifier")
    )
    private val showBulkReBalanceTopics = Url(
        "$base$TOPICS_MANAGEMENT_BULK_RE_BALANCE",
        listOf(
            "clusterIdentifier", "reBalanceMode",
            "includeTopicNamePattern", "excludeTopicNamePattern",
            "topicSelectOrder", "topicBy",
            "topicCountLimit", "topicPartitionCountLimit", "totalMigrationBytesLimit",
        )
    )
    private val showThrottleBrokerPartitionsForm = Url(
        "$base$TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS_FORM", listOf("clusterIdentifier")
    )
    private val showThrottleBrokerPartitions = Url(
        "$base$TOPICS_MANAGEMENT_THROTTLE_BROKER_PARTITIONS"
    )
    private val showTopicConfigSet = Url(
        "$base$TOPICS_MANAGEMENT_CONFIG_SET", listOf("topicName", "clusterIdentifier")
    )

    fun showCreateMissingTopic(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showCreateMissingTopic.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

    fun showBulkCreateMissingTopics(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showBulkCreateMissingTopics.render("clusterIdentifier" to clusterIdentifier)

    fun showBulkCreateMissingTopicOnClusters(
            topicName: TopicName
    ) = showBulkCreateMissingTopicOnClusters.render("topicName" to topicName)

    fun showBulkReElectReplicaLeaders(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showBulkReElectReplicaLeaders.render("clusterIdentifier" to clusterIdentifier)

    fun showBulkConfigUpdates(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showBulkConfigUpdates.render("clusterIdentifier" to clusterIdentifier)

    fun showBulkVerifyReAssignments(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showBulkVerifyReAssignments.render("clusterIdentifier" to clusterIdentifier)

    fun showBulkReBalanceTopicsForm(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showBulkReBalanceTopicsForm.render("clusterIdentifier" to clusterIdentifier)

    @SuppressWarnings("kotlin:S107")
    fun showBulkReBalanceTopics(
        clusterIdentifier: KafkaClusterIdentifier,
        reBalanceMode: String,
        includeTopicNamePattern: String,
        excludeTopicNamePattern: String,
        topicSelectOrder: String,
        topicBy: String,
        topicCountLimit: Int,
        topicPartitionCountLimit: Int,
        totalMigrationBytesLimit: Long,
    ) = showBulkReBalanceTopics.render("clusterIdentifier" to clusterIdentifier,
        "reBalanceMode" to reBalanceMode,
        "includeTopicNamePattern"  to includeTopicNamePattern,
        "excludeTopicNamePattern" to excludeTopicNamePattern,
        "topicSelectOrder" to topicSelectOrder,
        "topicBy" to topicBy,
        "topicCountLimit" to topicCountLimit.toString(),
        "topicPartitionCountLimit" to topicPartitionCountLimit.toString(),
        "totalMigrationBytesLimit" to totalMigrationBytesLimit.toString(),
    )

    fun showThrottleBrokerPartitionsForm(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showThrottleBrokerPartitionsForm.render("clusterIdentifier" to clusterIdentifier)

    fun showThrottleBrokerPartitions() = showThrottleBrokerPartitions.render()

    fun showDeleteTopicOnCluster(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showDeleteTopicOnCluster.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

    fun showTopicConfigUpdate(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showTopicConfigUpdate.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

    fun showTopicConfigBulkUpdate(
            topicName: TopicName,
    ) = showTopicConfigBulkUpdate.render(
            "topicName" to topicName,
    )

    fun showTopicPartitionCountChange(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showTopicPartitionCountChange.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

    fun showTopicReplicationFactorChange(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showTopicReplicationFactorChange.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

    fun showTopicReBalance(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        reBalanceMode: String
    ) = showTopicReBalance.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "reBalanceMode" to reBalanceMode
    )

    fun showTopicUnwantedLeaderReAssignment(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        unwantedBrokerId: BrokerId
    ) = showTopicUnwantedLeaderReAssignment.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "unwantedBrokerId" to unwantedBrokerId.toString()
    )

    fun showTopicExcludedBrokersReAssignment(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        excludedBrokerIds: List<BrokerId>
    ) = showTopicExcludedBrokersReAssignment.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "excludedBrokerIds" to excludedBrokerIds.joinToString(",")
    )

    fun showCustomReAssignmentInput(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showCustomReAssignmentInput.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
    )

    fun showCustomReAssignment(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        assignment: String    //example: 0:1,2,3;1:2,3,1;2:3,1,2
    ) = showCustomReAssignment.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "assignment" to assignment
    )

    fun showTopicConfigSet(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier
    ) = showTopicConfigSet.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier
    )

}