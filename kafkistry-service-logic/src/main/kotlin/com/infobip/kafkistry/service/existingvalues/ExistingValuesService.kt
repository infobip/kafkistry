package com.infobip.kafkistry.service.existingvalues

import com.infobip.kafkistry.kafka.BROKER_CONFIG_DOC
import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.kafka.TOPIC_CONFIG_DOC
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaUser
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.QuotaDescription
import org.springframework.stereotype.Service
import java.util.*

@Service
class ExistingValuesService(
    private val managementClientProperties: KafkaManagementClientProperties,
    private val topicsRegistry: TopicsRegistryService,
    private val clustersRegistry: ClustersRegistryService,
    private val aclsRegistry: AclsRegistryService,
    private val quotasRegistry: QuotasRegistryService,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
    private val consumersGroupsProvider: KafkaConsumerGroupsProvider,
    private val existingValuesSuppliers: Optional<List<ExistingValuesSupplier>>
) {

    private fun suppliers(): List<ExistingValuesSupplier> = existingValuesSuppliers.orElse(emptyList())

    private fun Sequence<String>.distinctSortedList() = distinct().sorted().toList()

    private fun List<StateData<KafkaClusterState>>.generateMostCommonConfig(): ExistingConfig {
        val allTopicConfigs = this
            .mapNotNull { it.valueOrNull()?.topics }
            .flatten()
            .map { it.config }
        val allKnownConfigKeys = allTopicConfigs.asSequence()
            .map { it.keys }
            .flatten()
            .toSet()
        return allKnownConfigKeys.asSequence()
            .map { key -> key to allTopicConfigs.mostFrequentElement { it[key] }!! }
            .sortedBy { (key, _) -> key }
            .associate { it }
    }

    private fun allOwners(
        topics: List<TopicDescription>,
        principals: List<PrincipalAclRules>,
        entityQuotas: List<QuotaDescription>
    ): List<String> {
        return sequence {
            yieldAll(topics.asSequence().map { it.owner })
            yieldAll(principals.asSequence().map { it.owner })
            yieldAll(entityQuotas.asSequence().map { it.owner })
            suppliers().forEach { yieldAll(it.owners()) }
        }.distinctSortedList()
    }

    private fun allUsers(
        principals: List<PrincipalAclRules>,
        entityQuotas: List<QuotaDescription>
    ): List<KafkaUser> {
        return sequence {
            principals.asSequence()
                .map { it.principal }
                .filter { it.startsWith("User:") }
                .map { it.removePrefix("User:") }
                .filter { it != "*" }
                .forEach { yield(it) }
            yieldAll(entityQuotas.asSequence().filter { !it.entity.userIsDefault() }.mapNotNull { it.entity.user })
            suppliers().forEach { yieldAll(it.users()) }
        }.distinctSortedList()
    }

    private fun allProducers(topics: List<TopicDescription>): List<String> {
        return sequence {
            yieldAll(topics.asSequence().map { it.producer })
            suppliers().forEach { yieldAll(it.producers()) }
        }.distinctSortedList()
    }

    private fun allTopics(
        topics: List<TopicDescription>,
        clustersStates: List<StateData<KafkaClusterState>>
    ): List<TopicName> {
        return sequence {
            yieldAll(topics.map { it.name })
            yieldAll(clustersStates.asSequence()
                .mapNotNull { it.valueOrNull()?.topics }
                .flatMap { it.asSequence() }
                .map { it.name }
            )
            suppliers().forEach { it.topics() }
        }.distinctSortedList()
    }

    private fun allConsumerGroups(consumersGroupsStates: Collection<StateData<ClusterConsumerGroups>>): List<ConsumerGroupId> {
        return sequence {
            yieldAll(consumersGroupsStates
                .mapNotNull { clusterState ->
                    clusterState.valueOrNull()?.consumerGroups?.keys
                }
                .flatten()
            )
            suppliers().forEach { yieldAll(it.consumerGroups()) }
        }.distinctSortedList()
    }

    fun allExistingValues(): ExistingValues {
        val kafkaProfiles = managementClientProperties.profiles.keys.toList()
        val clusterStates = kafkaClustersStateProvider.listAllLatestClusterStates()
        val topics = topicsRegistry.listTopics()
        val aclPrincipals = aclsRegistry.listAllPrincipalsAcls()
        val entityQuotas = quotasRegistry.listAllQuotas()
        val consumersGroupsStates = consumersGroupsProvider.listAllLatestStates()
        val clusterRefs = clustersRegistry.listClustersRefs()
        return ExistingValues(
            kafkaProfiles = kafkaProfiles,
            commonTopicConfig = clusterStates.generateMostCommonConfig(),
            topicConfigDoc = TOPIC_CONFIG_DOC,
            brokerConfigDoc = BROKER_CONFIG_DOC,
            owners = allOwners(topics, aclPrincipals, entityQuotas),
            producers = allProducers(topics),
            clusterIdentifiers = clusterRefs.map { it.identifier },
            clusterRefs = clusterRefs,
            tagClusters = clusterRefs
                .flatMap { ref -> ref.tags.map { it to ref.identifier } }
                .groupBy({ it.first }, { it.second }),
            topics = allTopics(topics, clusterStates),
            consumerGroups = allConsumerGroups(consumersGroupsStates),
            users = allUsers(aclPrincipals, entityQuotas),
        )
    }

}