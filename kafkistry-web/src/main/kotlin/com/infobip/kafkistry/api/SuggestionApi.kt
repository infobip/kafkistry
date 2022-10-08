package com.infobip.kafkistry.api

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.yaml.YamlMapper
import com.infobip.kafkistry.service.acl.AclsSuggestionService
import com.infobip.kafkistry.service.quotas.QuotasSuggestionService
import com.infobip.kafkistry.service.topic.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/suggestion")
class SuggestionApi(
    private val yamlMapper: YamlMapper,
    private val suggestionService: OperationSuggestionService,
    private val aclsSuggestionService: AclsSuggestionService,
    private val quotasSuggestionService: QuotasSuggestionService,
) {

    /**
     * Generate TopicDescription based on currently existing topic on clusters.
     * Generated TopicDescription is such that it's "wanted" configuration of topic matches the current
     * configuration on all tracked kafka clusters.
     * This method will work only if topic currently does not exist in registry.
     * (only for unknown topics visible on cluster(s))
     */
    @GetMapping("/import-topic")
    fun suggestUnexpectedTopicImport(
        @RequestParam("topicName") topicName: TopicName
    ): TopicDescription = suggestionService.suggestTopicImport(topicName)

    /**
     * Generate TopicDescription based on currently existing topic on clusters.
     * Generated TopicDescription is such that it's "wanted" configuration of topic matches the current
     * configuration on all tracked kafka clusters.
     * This method is used for generating TopicDescription that matches current state on clusters.
     * It is useful if we want to adjust "wanted" configuration to match existing without altering actual
     * topic's configuration.
     */
    @GetMapping("/update-topic")
    fun suggestTopicUpdate(
        @RequestParam("topicName") topicName: TopicName
    ): TopicDescription = suggestionService.suggestTopicUpdate(topicName)

    @GetMapping("/fix-violations-update")
    fun fixViolationsUpdate(
        @RequestParam("topicName") topicName: TopicName
    ): TopicDescription = suggestionService.suggestFixRuleViolations(topicName)

    @GetMapping("/create-default-topic")
    fun suggestDefaultTopicCreation(): TopicDescription = suggestionService.suggestDefaultTopic()

    /**
     * Generate topic configuration for specific cluster.
     * Method uses existing TopicDescription in registry and creates effective wanted configuration of topic
     * for specific cluster.
     */
    @GetMapping("/expected-topic-info")
    fun expectedTopicInfoOnCluster(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ExpectedTopicInfo = suggestionService.generateExpectedTopicInfo(topicName, clusterIdentifier)

    /**
     * Generate what topic properties changes need to be done on specific kafka cluster to match "wanted"
     * number of partitions and number of replicas.
     */
    @GetMapping("/topic-needed-partition-properties-changes")
    fun generateTopicPartitionPropertiesChangesOnCluster(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): PartitionPropertiesChanges = suggestionService.offerTopicPartitionPropertiesChanges(topicName, clusterIdentifier)

    /**
     * This is basically just converter from json data format to yaml representation
     */
    @PostMapping("/json-to-yaml")
    fun generateYaml(
        @RequestBody body: Any
    ): String = yamlMapper.serialize(body)

    @GetMapping("/re-balance")
    fun reBalanceAssignments(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("reBalanceMode") reBalanceMode: ReBalanceMode,
    ): ReBalanceSuggestion = suggestionService.reBalanceTopicAssignments(topicName, clusterIdentifier, reBalanceMode)

    @PostMapping("/bulk-re-balance")
    fun bulkReBalanceTopics(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestBody options: BulkReAssignmentOptions,
    ): BulkReAssignmentSuggestion = suggestionService.suggestBulkReBalanceTopics(clusterIdentifier, options)

    @GetMapping("/unwanted-leader-re-assign")
    fun reAssignTopicUnwantedLeader(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("unwantedBrokerId") unwantedBrokerId: BrokerId,
    ): ReBalanceSuggestion =
        suggestionService.reAssignTopicUnwantedLeader(topicName, clusterIdentifier, unwantedBrokerId)

    @GetMapping("/exclude-brokers-re-assign")
    fun reAssignTopicWithoutBrokers(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("excludedBrokerIds") excludedBrokerIds: Array<BrokerId>,
    ): ReBalanceSuggestion =
        suggestionService.reAssignTopicWithoutBrokers(topicName, clusterIdentifier, excludedBrokerIds.toList())

    @PostMapping("/custom-re-assign-inspect")
    fun customReAssignInspect(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestBody newAssignments: Map<Partition, List<BrokerId>>,
    ): ReBalanceSuggestion = suggestionService.customReAssignInspect(topicName, clusterIdentifier, newAssignments)

    @PostMapping("/apply-resource-requirements")
    fun applyResourceRequirements(
        @RequestParam(name = "onlyClusterIdentifiers", required = false) onlyClusterIdentifiers: Set<KafkaClusterIdentifier>?,
        @RequestParam(name = "onlyClusterTags", required = false) onlyClusterTags: Set<Tag>?,
        @RequestBody topicDescription: TopicDescription
    ): TopicDescription = suggestionService.applyResourceRequirements(
        topicDescription, onlyClusterIdentifiers, onlyClusterTags
    )

    @GetMapping("/import-principal-acls")
    fun suggestPrincipalAclsImport(
        @RequestParam("principal") principal: PrincipalId
    ): PrincipalAclRules = aclsSuggestionService.suggestPrincipalAclsImport(principal)

    @GetMapping("/update-principal-acls")
    fun suggestPrincipalAclsUpdate(
        @RequestParam("principal") principal: PrincipalId
    ): PrincipalAclRules = aclsSuggestionService.suggestPrincipalAclsUpdate(principal)

    @PostMapping("/generate-broker-topic-partition-throttle")
    fun generateBrokerTopicsPartitionsThrottle(
        @RequestBody throttleBrokerTopicPartitions: ThrottleBrokerTopicPartitions
    ): ThrottleBrokerTopicPartitionsSuggestion = suggestionService.suggestBrokerTopicPartitionsThrottle(throttleBrokerTopicPartitions)

    @GetMapping("/import-entity-quotas")
    fun suggestEntityQuotasImport(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID
    ): QuotaDescription = quotasSuggestionService.suggestImport(quotaEntityID)

    @GetMapping("/edit-entity-quotas")
    fun suggestEntityQuotasEdit(
        @RequestParam("quotaEntityID") quotaEntityID: QuotaEntityID
    ): QuotaDescription = quotasSuggestionService.suggestEdit(quotaEntityID)

}