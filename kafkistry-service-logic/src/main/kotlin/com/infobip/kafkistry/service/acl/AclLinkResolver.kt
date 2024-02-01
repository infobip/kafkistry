package com.infobip.kafkistry.service.acl

import com.google.common.base.Suppliers
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.AclResource.NamePattern.LITERAL
import com.infobip.kafkistry.model.AclResource.NamePattern.PREFIXED
import com.infobip.kafkistry.model.AclResource.Type.*
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

const val ANY_PRINCIPAL = "User:*"

@Component
class AclLinkResolver(
    private val aclDataProvider: AclResolverDataProvider
) {

    private val indexCache = IndexCache(10)

    fun invalidateCache() = indexCache.setNewSupplier()

    fun findAffectedTopics(
        aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): List<TopicName> = indexCache().findAffectedTopics(aclRule, clusterIdentifier)

    fun findAffectedExistingTopics(
        aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): List<TopicName>? = indexCache().findAffectedExistingTopics(aclRule, clusterIdentifier)

    fun findAffectedExistingAnywhereTopics(
        aclRule: KafkaAclRule
    ): List<TopicName>? = indexCache().findAffectedExistingTopics(aclRule, null)

    fun findAffectedConsumerGroups(
        aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): List<ConsumerGroupId> = indexCache().findAffectedConsumerGroups(aclRule, clusterIdentifier)

    fun findAffectedExistingConsumerGroups(
        aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): List<ConsumerGroupId>? = indexCache().findAffectedExistingConsumerGroups(aclRule, clusterIdentifier)

    fun findAffectedExistingAnywhereConsumerGroups(
        aclRule: KafkaAclRule
    ): List<ConsumerGroupId>? = indexCache().findAffectedExistingConsumerGroups(aclRule, null)

    fun findAffectedTransactionalIds(
        aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
    ): List<TransactionalId> = indexCache().findAffectedTransactionalIds(aclRule, clusterIdentifier)

    fun findTopicAffectingAclRules(
        topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
    ): List<KafkaAclRule> = indexCache().findTopicAffectingAclRules(topicName, clusterIdentifier)

    fun findConsumerGroupAffectingAclRules(
        consumerGroup: ConsumerGroupId, clusterIdentifier: KafkaClusterIdentifier
    ): List<KafkaAclRule> = indexCache().findConsumerGroupAffectingAclRules(consumerGroup, clusterIdentifier)

    fun findTransactionalIdAffectingAclRules(
        transactionalId: TransactionalId, clusterIdentifier: KafkaClusterIdentifier
    ): List<KafkaAclRule> = indexCache().findTransactionalIdAffectingAclRules(transactionalId, clusterIdentifier)

    fun findQuotaAffectingPrincipals(
        quotaEntity: QuotaEntity, clusterIdentifier: KafkaClusterIdentifier
    ): List<PrincipalId> = indexCache().findQuotaAffectingPrincipals(quotaEntity, clusterIdentifier)

    fun findPrincipalAffectingQuotas(
        principalId: PrincipalId, clusterIdentifier: KafkaClusterIdentifier
    ): List<QuotaEntity> = indexCache().findPrincipalAffectingQuotas(principalId, clusterIdentifier)

    private fun createIndex(): IndexSnapshot {
        val clustersData = aclDataProvider.getClustersData()
        val clusterSnapshots = clustersData.mapValues { (_, clusterData) ->
            LinksSnapshot(
                topics = clusterData.topics,
                consumerGroups = clusterData.consumerGroups,
                existingTopics = clusterData.existingTopics,
                existingConsumerGroups = clusterData.existingConsumerGroups,
                quotaEntities = clusterData.quotaEntities,
                resourceAcls = clusterData.acls.groupBy { it.resource },
                principals = clusterData.acls.map { it.principal }.toSet(),
                transactionalIds = clusterData.transactionalIds,
            )
        }
        val haveUnreachableCluster = clustersData.values.any {
            !it.clusterDisabled && (it.existingTopics == null || it.existingConsumerGroups == null)
        }
        val globalSnapshot = LinksSnapshot(
            topics = clustersData.values.flatMap { it.topics }.distinct(),
            consumerGroups = clustersData.values.flatMap { it.consumerGroups }.distinct(),
            existingTopics = if (haveUnreachableCluster) { null } else { clustersData.values.flatMap { it.existingTopics.orEmpty() } },
            existingConsumerGroups = if (haveUnreachableCluster) { null } else { clustersData.values.flatMap { it.existingConsumerGroups.orEmpty() } },
            quotaEntities = clustersData.values.flatMap { it.quotaEntities }.distinct(),
            resourceAcls = clustersData.values.flatMap { it.acls }.distinct().groupBy { it.resource },
            principals = clustersData.values.flatMap { it.acls }.map { it.principal }.toSet(),
            transactionalIds = clustersData.values.flatMap { it.transactionalIds }.distinct(),
        )
        return IndexSnapshot(
            globalSnapshot = globalSnapshot,
            clusterSnapshots = clusterSnapshots,
        )
    }

    private inner class LinksSnapshot(
        val topics: List<TopicName>,
        val consumerGroups: List<ConsumerGroupId>,
        val existingTopics: List<TopicName>?,
        val existingConsumerGroups: List<ConsumerGroupId>?,
        val quotaEntities: List<QuotaEntity>,
        val resourceAcls: Map<AclResource, List<KafkaAclRule>>,
        val principals: Set<PrincipalId>,
        val transactionalIds: List<TransactionalId>,
    ) {
        val existingTopicsSet: Set<TopicName>? = existingTopics?.toSet()
        val existingConsumerGroupsSet: Set<ConsumerGroupId>? = existingConsumerGroups?.toSet()
        val topicAcls: Map<TopicName, List<KafkaAclRule>>
        val consumerGroupsAcls: Map<ConsumerGroupId, List<KafkaAclRule>>
        val aclTopics: Map<AclResource, List<TopicName>>
        val aclConsumerGroups: Map<AclResource, List<ConsumerGroupId>>
        val aclTransactionalIds: Map<AclResource, List<TransactionalId>>
        val transactionalIdsAcls: Map<TransactionalId, List<KafkaAclRule>>
        val quotaEntityPrincipals: Map<QuotaEntity, List<PrincipalId>>
        val userQuotaEntities: Map<KafkaUser?, List<QuotaEntity>>
        val principalQuotaEntities: Map<PrincipalId, List<QuotaEntity>>
        val nonExplicitlyReferencedPrincipals: List<PrincipalId>
        val nonLiteralUserQuotaEntities: List<QuotaEntity>

        //pre-compute all topic-acl, acl-topic, group-acl, acl-group relations to allow O(1) lookups
        init {
            val literalResourceAcls = resourceAcls.filterKeys {
                it.namePattern == LITERAL && it.name != "*"
            }
            val prefixedResourceAcls = resourceAcls.filterKeys {
                it.namePattern == PREFIXED
            }
            val wildcardResourceAcls = resourceAcls.filterKeys {
                it.namePattern == LITERAL && it.name == "*"
            }
            topicAcls = topics.associateWith { topic ->
                val literalAcls = literalResourceAcls[AclResource(TOPIC, topic, LITERAL)] ?: emptyList()
                val prefixedAcls = prefixedResourceAcls.findAffectingForTopic(topic)
                val wildcardAcls = wildcardResourceAcls.findAffectingForTopic(topic)
                (wildcardAcls + prefixedAcls + literalAcls).orderAclsAsWildcardPrefixedLiteral()
            }
            consumerGroupsAcls = consumerGroups.associateWith { consumerGroup ->
                val literalAcls = literalResourceAcls[AclResource(GROUP, consumerGroup, LITERAL)] ?: emptyList()
                val prefixedAcls = prefixedResourceAcls.findAffectingForConsumerGroup(consumerGroup)
                val wildcardAcls = wildcardResourceAcls.findAffectingForConsumerGroup(consumerGroup)
                (wildcardAcls + prefixedAcls + literalAcls).orderAclsAsWildcardPrefixedLiteral()
            }
            transactionalIdsAcls = transactionalIds.associateWith { transactionalId ->
                val literalAcls = literalResourceAcls[AclResource(TRANSACTIONAL_ID, transactionalId, LITERAL)] ?: emptyList()
                val prefixedAcls = prefixedResourceAcls.findAffectingForTransactionalId(transactionalId)
                val wildcardAcls = wildcardResourceAcls.findAffectingForTransactionalId(transactionalId)
                (wildcardAcls + prefixedAcls + literalAcls).orderAclsAsWildcardPrefixedLiteral()
            }
            fun associateAclToNames(type: AclResource.Type,  allNames: List<String>): Map<AclResource, List<String>> {
                return resourceAcls.keys.asSequence()
                    .filter { it.type == type }
                    .associateWith { aclResource ->
                        with(aclResource) {
                            when (namePattern) {
                                LITERAL -> if (name == "*") allNames else listOf(name)
                                PREFIXED -> allNames.findMatchingNames(this)
                            }
                        }
                    }
            }

            aclTopics = associateAclToNames(TOPIC, topics)
            aclConsumerGroups = associateAclToNames(GROUP, consumerGroups)
            aclTransactionalIds = associateAclToNames(TRANSACTIONAL_ID, transactionalIds)
            val quotaEntitiesSet = quotaEntities.toHashSet()
            val defaultUserExist = QuotaEntity.userDefault() in quotaEntitiesSet
            val defaultClientExist = QuotaEntity.clientDefault() in quotaEntitiesSet
            val defaultUserDefaultClientExist = QuotaEntity.userDefaultClientDefault() in quotaEntitiesSet
            val userEntities = quotaEntities.asSequence()
                .filter { !it.userIsDefault() && !it.userIsAny() }
                .mapNotNull { entity -> entity.user?.let { it to entity } }
                .groupBy ({ it.first }, { it.second })
            principalQuotaEntities = principals.asSequence()
                .filter { it != ANY_PRINCIPAL && it.startsWith("User:") }
                .associateWith { principal ->
                    val user: KafkaUser = principal.removePrefix("User:")
                    val exactUserEntities = userEntities[user].orEmpty()
                    val hasUser = QuotaEntity.user(user) in exactUserEntities
                    sequence {
                        yieldAll(exactUserEntities)
                        if (!hasUser && defaultUserExist) yield(QuotaEntity.userDefault())
                        if (defaultClientExist) yield(QuotaEntity.clientDefault())
                        if (defaultUserDefaultClientExist) yield(QuotaEntity.userDefaultClientDefault())
                    }.toList()
                }
            quotaEntityPrincipals = principalQuotaEntities.asSequence()
                .flatMap { (principal, quotaEntities) ->
                    quotaEntities.map { it to principal }
                }
                .groupBy ({ it.first }, {it.second})
            userQuotaEntities = quotaEntities.groupBy { it.user }
            nonExplicitlyReferencedPrincipals = principalQuotaEntities
                .filter { it.value.isEmpty() || it.value.none { entity -> entity.userIsLiteral() } }
                .keys.toList()
            nonLiteralUserQuotaEntities = listOfNotNull(
                QuotaEntity.userDefault().takeIf { defaultUserExist },
                QuotaEntity.clientDefault().takeIf { defaultClientExist },
                QuotaEntity.userDefaultClientDefault().takeIf { defaultUserDefaultClientExist },
            )
        }
    }

    private inner class IndexSnapshot(
        private val globalSnapshot: LinksSnapshot,
        private val clusterSnapshots: Map<KafkaClusterIdentifier, LinksSnapshot>,
    ) {

        private fun snapshotFor(clusterIdentifier: KafkaClusterIdentifier?): LinksSnapshot? {
            return if (clusterIdentifier == null) {
                globalSnapshot
            } else {
                clusterSnapshots[clusterIdentifier]
            }
        }

        private fun findAffected(
            aclRule: KafkaAclRule,
            clusterIdentifier: KafkaClusterIdentifier?,
            type: AclResource.Type,
            directMap: LinksSnapshot.() -> Map<AclResource, List<String>>,
            allList: LinksSnapshot.() -> List<String>,
        ): List<String> {
            if (aclRule.resource.type != type) {
                return emptyList()
            }
            val snapshot = snapshotFor(clusterIdentifier) ?: return emptyList()
            snapshot.directMap()[aclRule.resource]?.run { return this }
            return snapshot.allList().findMatchingNames(aclRule.resource)
        }

        private fun List<String>.onlyExisting(
            clusterIdentifier: KafkaClusterIdentifier?,
            existingSet: LinksSnapshot.() -> Set<String>?,
        ): List<String>? {
            val snapshot = snapshotFor(clusterIdentifier) ?: return null
            val existing = snapshot.existingSet() ?: return null
            return filter { it in existing }
        }

        fun findAffectedTopics(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
        ): List<TopicName> = findAffected(aclRule, clusterIdentifier, TOPIC, { aclTopics }, { topics })

        fun findAffectedExistingTopics(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier?
        ): List<TopicName>? = findAffected(aclRule, clusterIdentifier, TOPIC, { aclTopics }, { topics })
            .onlyExisting(clusterIdentifier) { existingTopicsSet }

        fun findAffectedConsumerGroups(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
        ): List<ConsumerGroupId> = findAffected(aclRule, clusterIdentifier, GROUP, { aclConsumerGroups }, { consumerGroups })

        fun findAffectedExistingConsumerGroups(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier?,
        ): List<ConsumerGroupId>? = findAffected(aclRule, clusterIdentifier, GROUP, { aclConsumerGroups }, { consumerGroups })
            .onlyExisting(clusterIdentifier) { existingConsumerGroupsSet }

        fun findAffectedTransactionalIds(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
        ): List<TransactionalId> = findAffected(aclRule, clusterIdentifier, TRANSACTIONAL_ID, { aclTransactionalIds }, { transactionalIds })

        fun findTopicAffectingAclRules(
                topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
        ): List<KafkaAclRule> {
            val snapshot = clusterSnapshots[clusterIdentifier] ?: return emptyList()
            snapshot.topicAcls[topicName]?.run { return this }
            return snapshot.resourceAcls.findAffectingForTopic(topicName)
        }

        fun findConsumerGroupAffectingAclRules(
            consumerGroup: ConsumerGroupId, clusterIdentifier: KafkaClusterIdentifier
        ): List<KafkaAclRule> {
            val snapshot = clusterSnapshots[clusterIdentifier] ?: return emptyList()
            snapshot.consumerGroupsAcls[consumerGroup]?.run { return this }
            return snapshot.resourceAcls.findAffectingForConsumerGroup(consumerGroup)
        }

        fun findTransactionalIdAffectingAclRules(
            transactionalId: TransactionalId, clusterIdentifier: KafkaClusterIdentifier
        ): List<KafkaAclRule> {
            val snapshot = clusterSnapshots[clusterIdentifier] ?: return emptyList()
            snapshot.transactionalIdsAcls[transactionalId]?.run { return this }
            return snapshot.resourceAcls.findAffectingForTransactionalId(transactionalId)
        }

        fun findQuotaAffectingPrincipals(
            quotaEntity: QuotaEntity, clusterIdentifier: KafkaClusterIdentifier
        ): List<PrincipalId> {
            val snapshot = clusterSnapshots[clusterIdentifier] ?: return emptyList()
            snapshot.quotaEntityPrincipals[quotaEntity]?.run { return this }
            return snapshot.findAffectedForQuotaEntity(quotaEntity)
        }

        fun findPrincipalAffectingQuotas(
            principalId: PrincipalId, clusterIdentifier: KafkaClusterIdentifier
        ): List<QuotaEntity> {
            if (principalId == ANY_PRINCIPAL || !principalId.startsWith("User:")) {
                return emptyList()
            }
            val snapshot = clusterSnapshots[clusterIdentifier] ?: return emptyList()
            snapshot.principalQuotaEntities[principalId]?.run { return this }
            return snapshot.findAffectingForPrincipal(principalId)
        }

    }

    private fun Iterable<String>.findMatchingNames(resource: AclResource): List<String> {
        return filter { resource.matchesName(it) }
    }

    private fun AclResource.matchesName(name: String): Boolean {
        return when (namePattern) {
            LITERAL -> name == this.name || this.name == "*"
            PREFIXED -> name.startsWith(this.name)
        }
    }

    private fun Map<AclResource, List<KafkaAclRule>>.findAffectingForTopic(topicName: TopicName): List<KafkaAclRule> {
        return this.filterKeys { it.type == TOPIC && it.matchesName(topicName) }
                .flatMap { it.value }
                .orderAclsAsWildcardPrefixedLiteral()
    }

    private fun Map<AclResource, List<KafkaAclRule>>.findAffectingForConsumerGroup(consumerGroup: ConsumerGroupId): List<KafkaAclRule> {
        return this.filterKeys { it.type == GROUP && it.matchesName(consumerGroup) }
                .flatMap { it.value }
                .orderAclsAsWildcardPrefixedLiteral()
    }

    private fun Map<AclResource, List<KafkaAclRule>>.findAffectingForTransactionalId(transactionalId: TransactionalId): List<KafkaAclRule> {
        return this.filterKeys { it.type == TRANSACTIONAL_ID && it.matchesName(transactionalId) }
                .flatMap { it.value }
                .orderAclsAsWildcardPrefixedLiteral()
    }

    private fun LinksSnapshot.findAffectedForQuotaEntity(quotaEntity: QuotaEntity): List<PrincipalId> {
        return when {
            quotaEntity == QuotaEntity.userDefault() ||
                    quotaEntity == QuotaEntity.clientDefault() ||
                    quotaEntity == QuotaEntity.userDefaultClientDefault() -> nonExplicitlyReferencedPrincipals
            "User:${quotaEntity.user}" in principals -> listOf("User:${quotaEntity.user}")
            else -> emptyList()
        }
    }

    private fun LinksSnapshot.findAffectingForPrincipal(principal: PrincipalId): List<QuotaEntity> {
        val user = principal.removePrefix("User:")
        return userQuotaEntities[user] ?: nonLiteralUserQuotaEntities
    }

    private fun Iterable<KafkaAclRule>.orderAclsAsWildcardPrefixedLiteral(): List<KafkaAclRule> {
        return sortedBy {
            when (it.resource.namePattern) {
                LITERAL -> if (it.resource.name == "*") 0 else 2
                PREFIXED -> 1
            }
        }
    }

    private inner class IndexCache(
            val expireSeconds: Int
    ) {
        @Volatile   //ensure invalidation is guaranteed for all threads
        lateinit var supplier: () -> IndexSnapshot

        init {
            setNewSupplier()
        }

        fun setNewSupplier() {
            supplier = Suppliers.memoizeWithExpiration(
                { createIndex() }, expireSeconds.toLong(), TimeUnit.SECONDS
            ).let { { it.get() } }
        }

        operator fun invoke(): IndexSnapshot = supplier()
    }
}

data class AclClusterLinkData(
    val clusterRef: ClusterRef,
    val clusterDisabled: Boolean,
    val topics: List<TopicName>,
    val consumerGroups: List<ConsumerGroupId>,
    val quotaEntities: List<QuotaEntity>,
    val acls: List<KafkaAclRule>,
    val transactionalIds: List<TransactionalId>,
    val existingTopics: List<TopicName>?,
    val existingConsumerGroups: List<TopicName>?,
)

