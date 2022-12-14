package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryIntegrityException
import com.infobip.kafkistry.service.KafkistryValidationException
import com.infobip.kafkistry.service.topic.toAssignmentsInfo
import com.infobip.kafkistry.webapp.TopicInspectExtensionProperties
import com.infobip.kafkistry.webapp.WizardTopicNameProperties
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_CLONE_ADD_NEW
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_CLUSTER_INSPECT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_CREATE
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_DELETE
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_DRY_RUN_INSPECT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_EDIT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_EDIT_ON_BRANCH
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_FIX_VIOLATIONS_EDIT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_IMPORT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_INSPECT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_INSPECT_HISTORY
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_SUGGEST_EDIT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_WIZARD
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS_WIZARD_CREATE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*
import jakarta.servlet.http.HttpSession

@Controller
@RequestMapping("\${app.http.root-path}$TOPICS")
class TopicsController(
    private val topicsApi: TopicsApi,
    private val clustersApi: ClustersApi,
    private val inspectApi: InspectApi,
    private val suggestionApi: SuggestionApi,
    private val existingValuesApi: ExistingValuesApi,
    private val consumersApi: ConsumersApi,
    private val topicOffsetsApi: TopicOffsetsApi,
    private val wizardTopicNameProperties: WizardTopicNameProperties,
    private val topicInspectExtensionProperties: TopicInspectExtensionProperties,
    private val topicReplicasApi: TopicReplicasApi,
    private val topicPartitionsReAssignmentsApi: TopicPartitionsReAssignmentsApi,
    private val topicOldestRecordAgeApiOpt: Optional<TopicOldestRecordAgeApi>,
    private val resourceAnalyzerApi: ResourceAnalyzerApi,
    private val kStreamAppsApi: KStreamAppsApi,
    private val clusterEnabledFilter: ClusterEnabledFilter,
) : BaseController() {

    @GetMapping
    fun showTopics(): ModelAndView {
        val topics = inspectApi.inspectTopics()
        val unknownTopics = inspectApi.inspectUnknownTopics()
        val pendingTopicsRequests = topicsApi.pendingTopicsRequests()
        val allTopics = (topics + unknownTopics).sortedBy { it.topicName }
        return ModelAndView("topics/all", mutableMapOf(
                "topics" to allTopics,
                "pendingTopicsUpdates" to pendingTopicsRequests
        ))
    }

    @GetMapping(TOPICS_IMPORT)
    fun showImportTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val topicImport = suggestionApi.suggestUnexpectedTopicImport(topicName)
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/import", mutableMapOf(
                "topic" to topicImport,
                "existingValues" to existingValues,
                "topicSourceType" to "NEW"
        ))
    }

    @GetMapping(TOPICS_EDIT)
    fun showEditTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val topicDescription = topicsApi.getTopic(topicName)
        return showEditForm(topicDescription, "Edit topic")
    }

    @GetMapping(TOPICS_EDIT_ON_BRANCH)
    fun showEditTopicOnBranch(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("branch") branch: String
    ): ModelAndView {
        val topicRequest = topicsApi.pendingTopicRequest(topicName, branch)
        val topicExists = topicsApi.listTopics().any { it.name == topicName }
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/editOnBranch", mutableMapOf(
                "title" to "Edit pending topic request",
                "topicRequest" to topicRequest,
                "existingValues" to existingValues,
                "branch" to branch,
                "topicSourceType" to "BRANCH_EDIT",
                "topicExists" to topicExists
        ))
    }

    @GetMapping(TOPICS_SUGGEST_EDIT)
    fun suggestEditTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val suggestedTopic = suggestionApi.suggestTopicUpdate(topicName)
        return showEditForm(suggestedTopic, "Suggested edit to match current state on clusters")
    }

    @GetMapping(TOPICS_FIX_VIOLATIONS_EDIT)
    fun fixViolationsEdit(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val fixedTopic = suggestionApi.fixViolationsUpdate(topicName)
        return showEditForm(fixedTopic, "Edit with generated fixes for validation rules")
    }

    private fun showEditForm(
        topic: TopicDescription, title: String,
    ): ModelAndView {
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/edit", mutableMapOf(
            "title" to title,
            "topic" to topic,
            "existingValues" to existingValues,
            "topicSourceType" to "EDIT"
        ))
    }

    @GetMapping(TOPICS_CLONE_ADD_NEW)
    fun showCloneAddNewTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val sourceTopic = topicsApi.getTopic(topicName)
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/cloneAdd", mutableMapOf(
                "topic" to sourceTopic,
                "existingValues" to existingValues,
                "topicSourceType" to "NEW"
        ))
    }

    @GetMapping(TOPICS_DELETE)
    fun showDeleteTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val topic = topicsApi.getTopic(topicName)
        return ModelAndView("topics/delete", mutableMapOf(
                "topic" to topic
        ))
    }

    @GetMapping(TOPICS_INSPECT)
    fun showTopic(
            @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val topicStatuses = inspectApi.inspectTopic(topicName)
        val pendingTopicRequests = topicsApi.pendingTopicRequests(topicName)
        return ModelAndView("topics/topic", mutableMapOf(
                "topic" to topicStatuses,
                "pendingTopicRequests" to pendingTopicRequests,
        ))
    }

    @GetMapping(TOPICS_INSPECT_HISTORY)
    fun showTopicHistory(
        @RequestParam("topicName") topicName: TopicName
    ): ModelAndView {
        val historyChanges = topicsApi.getTopicChanges(topicName)
        return ModelAndView("git/entityHistory", mapOf("historyChangeRequests" to historyChanges))
    }

    @GetMapping(TOPICS_CLUSTER_INSPECT)
    fun showInspectTopicOnCluster(
            @RequestParam("topicName") topicName: TopicName,
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val topicStatus = inspectApi.inspectTopicOnCluster(topicName, clusterIdentifier)
        val clusterInfo = clustersApi.getClusterState(clusterIdentifier).valueOrNull()?.clusterInfo
        val expectedTopicInfo = try {
            suggestionApi.expectedTopicInfoOnCluster(topicName, clusterIdentifier)
        } catch (e: KafkistryIntegrityException) {
            null    //it means that topic exists on cluster but not in registry -> nothing expected then
        }
        val wrongPartitionValues = topicStatus.status.wrongValues
                ?.map { it.key }
                ?.filter { it in listOf("partition-count", "replication-factor") }
                ?: emptyList()
        val assignmentStatus = topicStatus.existingTopicInfo?.partitionsAssignments
                ?.toAssignmentsInfo(null, clusterInfo?.nodeIds ?: emptyList())
        val topicConsumerGroups = consumersApi.clusterTopicConsumers(clusterIdentifier, topicName)
        val topicOffsets = topicOffsetsApi.getTopicOffsets(topicName, clusterIdentifier)
        val topicReplicas = topicReplicasApi.getTopicReplicas(topicName, clusterIdentifier)
        val partitionReAssignments = topicPartitionsReAssignmentsApi.getTopicPartitionReAssignments(topicName, clusterIdentifier)
        val (oldestRecordAgesDisabled, oldestRecordAges) = run {
            val topicOldestRecordAgeApi = topicOldestRecordAgeApiOpt.orElse(null)
            if (topicOldestRecordAgeApi != null) {
                val partitionAges = topicOldestRecordAgeApi.getTopicOldestRecordAges(topicName, clusterIdentifier)
                false to partitionAges
            } else {
                true to null
            }
        }
        val clusterRef = with(topicStatus) { ClusterRef(clusterIdentifier,  clusterTags) }
        val topicResources = if (clusterInfo != null && clusterEnabledFilter.enabled(clusterRef)) {
            try {
                resourceAnalyzerApi.getTopicStatusOnCluster(clusterIdentifier, topicName)
            } catch (ex: KafkistryValidationException) {
                null //RF > num brokers
            }
        } else {
            null
        }
        val kStreamsInvolvement = kStreamAppsApi.topicKStreamApps(topicName, clusterIdentifier)
        return ModelAndView("topics/inspect", mutableMapOf(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "topicStatus" to topicStatus,
            "expectedTopicInfo" to expectedTopicInfo,
            "wrongPartitionValues" to wrongPartitionValues,
            "clusterInfo" to clusterInfo,
            "assignmentStatus" to assignmentStatus,
            "topicConsumerGroups" to topicConsumerGroups,
            "topicOffsets" to topicOffsets,
            "topicReplicas" to topicReplicas,
            "partitionReAssignments" to partitionReAssignments,
            "oldestRecordAgesDisabled" to oldestRecordAgesDisabled,
            "oldestRecordAges" to oldestRecordAges,
            "topicResources" to topicResources,
            "kStreamsInvolvement" to kStreamsInvolvement,
            "topicConfigDoc" to existingValuesApi.all().topicConfigDoc,
            "inspectExtensionProperties" to topicInspectExtensionProperties,
        ))
    }

    @PostMapping(TOPICS_DRY_RUN_INSPECT)
    fun showDryRunInspect(
            @RequestBody topicDescription: TopicDescription
    ): ModelAndView {
        val topicStatuses = inspectApi.inspectTopicUpdateDryRun(topicDescription)
        val clustersResources = resourceAnalyzerApi.getTopicStatus(topicDescription)
        return ModelAndView("topics/dryRunInspect", mutableMapOf(
                "topicStatuses" to topicStatuses,
                "clustersResources" to clustersResources,
        ))
    }

    @GetMapping(TOPICS_CREATE)
    fun showTopicCreate(): ModelAndView {
        val defaultTopicDescription = suggestionApi.suggestDefaultTopicCreation()
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/create", mutableMapOf(
                "topic" to defaultTopicDescription,
                "existingValues" to existingValues,
                "topicSourceType" to "NEW"
        ))
    }

    @GetMapping(TOPICS_WIZARD)
    fun showTopicWizard(): ModelAndView {
        val defaultTopicDescription = suggestionApi.suggestDefaultTopicCreation()
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/wizard", mutableMapOf(
                "topic" to defaultTopicDescription,
                "existingValues" to existingValues,
                "topicNameProperties" to wizardTopicNameProperties
        ))
    }

    @GetMapping(TOPICS_WIZARD_CREATE)
    fun createTopicFromWizard(
            session: HttpSession,
            @SessionAttribute(name = TopicWizardApi.SessionKeys.TOPIC_DESCRIPTION_FROM_WIZARD, required = true) sourceTopic: TopicDescription
    ): ModelAndView {
        val existingValues = existingValuesApi.all()
        return ModelAndView("topics/topicCreateFromWizard", mutableMapOf(
                "topic" to sourceTopic,
                "existingValues" to existingValues,
                "topicSourceType" to "NEW"
        ))
    }


}