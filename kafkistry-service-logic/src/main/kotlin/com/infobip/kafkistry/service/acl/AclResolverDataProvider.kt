package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springframework.stereotype.Component

interface AclResolverDataProvider {

    fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData>
}

@Component
class AclResolverDataProviderImpl(
    private val clustersRegistry: ClustersRegistryService,
    private val topicsRegistry: TopicsRegistryService,
    private val quotasRegistry: QuotasRegistryService,
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val consumerGroupsProvider: KafkaConsumerGroupsProvider,
    private val quotasProvider: KafkaQuotasProvider,
    private val aclsRegistry: AclsRegistryService
) : AclResolverDataProvider {

    override fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData> {
        val allTopics = topicsRegistry.listTopics()
        val allQuotas = quotasRegistry.listAllQuotas()
        val allPrincipalsAcls = aclsRegistry.listAllPrincipalsAcls()
        return clustersRegistry.listClustersRefs().associate { clusterRef ->
            val clusterState = clustersStateProvider.getLatestClusterState(clusterRef.identifier)
            val clusterConsumerGroups = consumerGroupsProvider.getLatestState(clusterRef.identifier)
            val clusterQuotas = quotasProvider.getLatestState(clusterRef.identifier)
            clusterRef.identifier to AclClusterLinkData(
                clusterRef = clusterRef,
                topics = clusterTopicNames(clusterRef, allTopics, clusterState, allPrincipalsAcls),
                consumerGroups = consumerGroupIds(clusterState, clusterConsumerGroups),
                quotaEntities = clusterEntityQuotas(clusterRef, allQuotas, clusterQuotas),
                acls = clusterAcls(clusterRef, clusterState, allPrincipalsAcls)
            )
        }
    }

    private fun clusterTopicNames(
        clusterRef: ClusterRef,
        allTopics: List<TopicDescription>,
        clusterState: StateData<KafkaClusterState>,
        allPrincipalAcls: List<PrincipalAclRules>
    ): List<TopicName> {
        return sequence {
            allTopics.asSequence()
                .filter { it.presence.needToBeOnCluster(clusterRef) }
                .map { it.name }
                .also { yieldAll(it) }
            clusterState.valueOrNull()?.topics?.asSequence()?.map { it.name }?.also { yieldAll(it) }
            clusterState.valueOrNull()?.acls?.asSequence()
                ?.filter { it.resource.type == AclResource.Type.TOPIC && it.resource.namePattern == AclResource.NamePattern.LITERAL }
                ?.map { it.resource.name }
                ?.also { yieldAll(it) }
            allPrincipalAcls.asSequence()
                .flatMap { it.rules.asSequence() }
                .filter { it.presence.needToBeOnCluster(clusterRef) }
                .map { it.resource }
                .extractResourceNames(AclResource.Type.TOPIC)
                .also { yieldAll(it) }
        }.distinct().sorted().toList()
    }

    private fun clusterEntityQuotas(
        clusterRef: ClusterRef,
        allQuotas: List<QuotaDescription>,
        clusterQuotas: StateData<ClusterQuotas>,
    ): List<QuotaEntity> {
        return sequence {
            allQuotas.asSequence()
                .filter { it.presence.needToBeOnCluster(clusterRef) }
                .map { it.entity }
                .also { yieldAll(it) }
            yieldAll(clusterQuotas.valueOrNull()?.quotas.orEmpty().keys)
        }.distinct().sortedBy { it.asID() }.toList()
    }

    private fun consumerGroupIds(
        clusterState: StateData<KafkaClusterState>,
        clusterConsumerGroups: StateData<ClusterConsumerGroups>
    ): List<ConsumerGroupId> {
        return sequence {
            clusterState.valueOrNull()?.acls?.asSequence()
                ?.map { it.resource }
                ?.extractResourceNames(AclResource.Type.GROUP)
                ?.also { yieldAll(it) }
            clusterConsumerGroups.valueOrNull()?.apply {
                consumerGroups.keys.also { yieldAll(it) }
            }
        }.distinct().sorted().toList()
    }

    private fun Sequence<AclResource>.extractResourceNames(type: AclResource.Type): Sequence<String> = this
        .filter { it.type == type }
        .filter { it.namePattern == AclResource.NamePattern.LITERAL && it.name != "*" }
        .map { it.name }

    private fun clusterAcls(
        clusterRef: ClusterRef,
        clusterState: StateData<KafkaClusterState>,
        allPrincipalAcls: List<PrincipalAclRules>
    ): List<KafkaAclRule> {
        return sequence {
            allPrincipalAcls.asSequence()
                .flatMap { principalAcls ->
                    principalAcls.rules.asSequence()
                        .filter { it.presence.needToBeOnCluster(clusterRef) }
                        .map { it.toKafkaAclRule(principalAcls.principal) }
                }
                .also { yieldAll(it) }
            clusterState.valueOrNull()?.acls?.also { yieldAll(it) }
        }.distinct().toList()
    }

}

class VirtualNamesAclResolverDataProvider(
    private val delegate: AclResolverDataProvider,
) : AclResolverDataProvider {

    private fun AclClusterLinkData.addVirtualNames(): AclClusterLinkData {
        fun virtualNamesFor(type: AclResource.Type): List<String> {
            return acls.asSequence()
                .filter { it.resource.type == type && it.resource.name != "*" }
                .map { it.resource.name }
                .toList()
        }

        val virtualTopics = virtualNamesFor(AclResource.Type.TOPIC)
        val virtualGroups = virtualNamesFor(AclResource.Type.GROUP)
        val virtualTransactionalIds = virtualNamesFor(AclResource.Type.TRANSACTIONAL_ID)
        return copy(
            topics = consumerGroups.plus(virtualTopics).distinct(),
            consumerGroups = consumerGroups.plus(virtualGroups).distinct(),
            transactionalIds = transactionalIds.plus(virtualTransactionalIds).distinct(),
        )
    }

    override fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData> {
        return delegate.getClustersData().mapValues { it.value.addVirtualNames() }
    }

}

class OverridingAclResolverDataProvider(
    private val delegate: AclResolverDataProvider,
    private val overrides: List<PrincipalAclRules>,
) : AclResolverDataProvider {

    override fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData> {
        val principalsOverrides = overrides.associateBy { it.principal }
        return delegate.getClustersData()
            .mapValues { (_, clusterData) ->
                val expectedToExist = principalsOverrides
                    .flatMap { (principal, aclOverrides) ->
                        aclOverrides.rules
                            .filter { it.presence.needToBeOnCluster(clusterData.clusterRef) }
                            .map { it.toKafkaAclRule(principal) }
                    }
                val currentAcls = clusterData.acls
                val ignoredExistingAcls = currentAcls.filterNot {
                    it.principal in principalsOverrides.keys
                }
                clusterData.copy(
                    acls = ignoredExistingAcls + expectedToExist
                )
            }
    }

}