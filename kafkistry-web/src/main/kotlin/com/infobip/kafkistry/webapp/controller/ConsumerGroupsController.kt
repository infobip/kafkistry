package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.ConsumersApi
import com.infobip.kafkistry.api.ExistingValuesApi
import com.infobip.kafkistry.api.KStreamAppsApi
import com.infobip.kafkistry.api.TopicOffsetsApi
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.ownership.UserOwnershipClassifier
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_CLONE
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_DELETE
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_INSPECT
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_OFFSET_DELETE
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_OFFSET_RESET
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS_OFFSET_PRESET
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$CONSUMER_GROUPS")
class ConsumerGroupsController(
    private val consumersApi: ConsumersApi,
    private val topicOffsetsApi: TopicOffsetsApi,
    private val kStreamAppsApi: KStreamAppsApi,
    private val existingValuesApi: ExistingValuesApi,
    private val clusterEnabledFilter: ClusterEnabledFilter,
    private val ownershipClassifier: UserOwnershipClassifier,
) : BaseController() {

    @GetMapping
    fun showAllClustersConsumerGroups(): ModelAndView {
        val consumersData = consumersApi.allConsumersData()
        val clusterIdentifiers = existingValuesApi.all().clusterRefs
            .filter { clusterEnabledFilter.enabled(it) }
            .map { it.identifier }
        val groupsOwned = consumersData.clustersGroups.asSequence()
            .map { it.consumerGroup.groupId }
            .distinct()
            .associateWith { ownershipClassifier.isOwnerOfConsumerGroup(it) }
        return ModelAndView("consumers/allClustersConsumers", mapOf(
            "consumersData" to consumersData,
            "groupsOwned" to groupsOwned,
            "clusterIdentifiers" to clusterIdentifiers,
        ))
    }

    @GetMapping(CONSUMER_GROUPS_INSPECT)
    fun showConsumerGroup(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId,
        @RequestParam("shownTopic", required = false) shownTopic: TopicName?
    ): ModelAndView {
        val consumerGroupIds = consumersApi.listClusterConsumerGroupIds(clusterIdentifier)
        val consumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, consumerGroupId)
        val kafkaStreamsApp = kStreamAppsApi.consumerGroupKStreamApps(consumerGroupId, clusterIdentifier)
        val groupOwned = ownershipClassifier.isOwnerOfConsumerGroup(consumerGroupId)
        return ModelAndView("consumers/consumerGroup", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "groupOwned" to groupOwned,
            "consumerGroupId" to consumerGroupId,
            "consumerGroup" to consumerGroup,
            "kafkaStreamsApp" to kafkaStreamsApp,
            "shownTopic" to shownTopic,
            "consumerGroupIds" to consumerGroupIds,
        ))
    }

    @GetMapping(CONSUMER_GROUPS_DELETE)
    fun showDeleteConsumerGroup(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId
    ): ModelAndView {
        val consumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, consumerGroupId)
                ?: throw KafkistryIllegalStateException(
                        "Can't show delete form, cause: can't read/find consumer group '%s' on cluster '%s'".format(
                                consumerGroupId, clusterIdentifier
                        )
                )
        val groupOwned = ownershipClassifier.isOwnerOfConsumerGroup(consumerGroupId)
        return ModelAndView("consumers/consumerGroupDelete", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId,
            "consumerGroup" to consumerGroup,
            "groupOwned" to groupOwned,
        ))
    }

    @GetMapping(CONSUMER_GROUPS_OFFSET_RESET)
    fun showResetConsumerGroupOffsets(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId
    ): ModelAndView {
        val consumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, consumerGroupId)
        val topicsOffsets = consumerGroup?.topicMembers?.associate {
            it.topicName to topicOffsetsApi.getTopicOffsets(it.topicName, clusterIdentifier)
        } ?: emptyMap()
        val groupOwned = ownershipClassifier.isOwnerOfConsumerGroup(consumerGroupId)
        return ModelAndView("consumers/consumerGroupReset", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId,
            "groupOwned" to groupOwned,
            "consumerGroup" to consumerGroup,
            "topicsOffsets" to topicsOffsets
        ))
    }

    @GetMapping(CONSUMER_GROUPS_CLONE)
    fun showCloneConsumerGroup(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("fromConsumerGroupId") fromConsumerGroupId: ConsumerGroupId,
        @RequestParam("intoConsumerGroupId") intoConsumerGroupId: ConsumerGroupId
    ): ModelAndView {
        val fromConsumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, fromConsumerGroupId)
        val intoConsumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, intoConsumerGroupId)
        val topicsOffsets = fromConsumerGroup?.topicMembers?.associate {
            it.topicName to topicOffsetsApi.getTopicOffsets(it.topicName, clusterIdentifier)
        } ?: emptyMap()
        return ModelAndView("consumers/cloneGroup", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "fromConsumerGroupId" to fromConsumerGroupId,
            "fromConsumerGroup" to fromConsumerGroup,
            "intoConsumerGroupId" to intoConsumerGroupId,
            "intoConsumerGroup" to intoConsumerGroup,
            "topicsOffsets" to topicsOffsets,
        ))
    }

    @GetMapping(CONSUMER_GROUPS_OFFSET_PRESET)
    fun showPresetConsumerGroupOffsets(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId,
    ): ModelAndView {
        val consumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, consumerGroupId)
        val topicsOffsets = consumerGroup?.topicMembers?.associate {
            it.topicName to topicOffsetsApi.getTopicOffsets(it.topicName, clusterIdentifier)
        } ?: emptyMap()
        val allTopicsOffsets = topicOffsetsApi.getTopicsOffsets(clusterIdentifier)
        val groupOwned = ownershipClassifier.isOwnerOfConsumerGroup(consumerGroupId)
        return ModelAndView("consumers/consumerGroupPreset", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId,
            "consumerGroup" to consumerGroup,
            "topicsOffsets" to topicsOffsets,
            "allTopicsOffsets" to allTopicsOffsets,
            "groupOwned" to groupOwned,
        ))
    }

    @GetMapping(CONSUMER_GROUPS_OFFSET_DELETE)
    fun showDeleteConsumerGroupOffsets(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("consumerGroupId") consumerGroupId: ConsumerGroupId
    ): ModelAndView {
        val consumerGroup = consumersApi.clusterConsumerGroup(clusterIdentifier, consumerGroupId)
        val topicsOffsets = consumerGroup?.topicMembers?.associate {
            it.topicName to topicOffsetsApi.getTopicOffsets(it.topicName, clusterIdentifier)
        } ?: emptyMap()
        val groupOwned = ownershipClassifier.isOwnerOfConsumerGroup(consumerGroupId)
        return ModelAndView("consumers/consumerGroupDeleteOffsets", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "consumerGroupId" to consumerGroupId,
            "consumerGroup" to consumerGroup,
            "topicsOffsets" to topicsOffsets,
            "groupOwned" to groupOwned,
        ))
    }

}
