package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class ConsumerGroupsUrls(base: String) : BaseUrls() {

    companion object {
        const val CONSUMER_GROUPS = "/consumer-groups"
        const val CONSUMER_GROUPS_INSPECT = "/inspect"
        const val CONSUMER_GROUPS_DELETE = "/delete"
        const val CONSUMER_GROUPS_OFFSET_RESET = "/offset-reset"
        const val CONSUMER_GROUPS_OFFSET_PRESET = "/offset-preset"
        const val CONSUMER_GROUPS_CLONE = "/clone"
        const val CONSUMER_GROUPS_OFFSET_DELETE = "/offset-delete"
    }

    private val showAll = Url(base)
    private val showConsumerGroup = Url("$base$CONSUMER_GROUPS_INSPECT", listOf("clusterIdentifier", "consumerGroupId", "shownTopic"))
    private val showDeleteConsumerGroup = Url("$base$CONSUMER_GROUPS_DELETE", listOf("clusterIdentifier", "consumerGroupId"))
    private val showResetConsumerGroupOffsets = Url("$base$CONSUMER_GROUPS_OFFSET_RESET", listOf("clusterIdentifier", "consumerGroupId"))
    private val showPresetConsumerGroupOffsets = Url("$base$CONSUMER_GROUPS_OFFSET_PRESET", listOf("clusterIdentifier", "consumerGroupId"))
    private val showCloneConsumerGroup = Url("$base$CONSUMER_GROUPS_CLONE", listOf("clusterIdentifier", "fromConsumerGroupId", "intoConsumerGroupId"))
    private val showDeleteConsumerGroupOffsets = Url("$base$CONSUMER_GROUPS_OFFSET_DELETE", listOf("clusterIdentifier", "consumerGroupId"))

    fun showAllClustersConsumerGroups() = showAll.render()

    @JvmOverloads
    fun showConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId,
        shownTopic: TopicName? = null
    ) = showConsumerGroup.render(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId,
            "shownTopic" to shownTopic
    )

    fun showDeleteConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ) = showDeleteConsumerGroup.render(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId
    )

    fun showResetConsumerGroupOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ) = showResetConsumerGroupOffsets.render(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId
    )

    fun showPresetConsumerGroupOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ) = showPresetConsumerGroupOffsets.render(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId
    )

    fun showCloneConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        fromConsumerGroupId: ConsumerGroupId,
        intoConsumerGroupId: ConsumerGroupId,
    ) = showCloneConsumerGroup.render(
            "clusterIdentifier" to clusterIdentifier,
            "fromConsumerGroupId" to fromConsumerGroupId,
            "intoConsumerGroupId" to intoConsumerGroupId,
    )

    fun showDeleteConsumerGroupOffsets(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId
    ) = showDeleteConsumerGroupOffsets.render(
        "clusterIdentifier" to clusterIdentifier,
        "consumerGroupId" to consumerGroupId
    )

}
