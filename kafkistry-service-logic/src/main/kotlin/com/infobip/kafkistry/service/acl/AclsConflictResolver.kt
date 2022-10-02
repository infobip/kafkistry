package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import org.springframework.stereotype.Component

@Component
//TODO ability to disable
class AclsConflictResolver(
    private val aclDataProvider: AclResolverDataProvider,
) {

    private val srcAclDataProvider = object : AclResolverDataProvider {

        fun AclClusterLinkData.addVirtualNames(): AclClusterLinkData {
            fun virtualNamesFor(type: AclResource.Type): List<String> {
                return acls.asSequence()
                    .filter { it.resource.type == type }
                    .filter { it.resource.namePattern == AclResource.NamePattern.LITERAL && it.resource.name != "*" }
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
            return aclDataProvider.getClustersData()
                .mapValues { it.value.addVirtualNames() }
        }

    }

    private val aclLinkResolver = AclLinkResolver(srcAclDataProvider)

    private fun linkResolver(
        withAcls: Map<KafkaAclRule, Presence>,
        withoutAcls: Map<KafkaAclRule, Presence>,
    ): AclLinkResolver {
        if (withAcls.isEmpty() && withoutAcls.isEmpty()) {
            return aclLinkResolver
        }
        return AclLinkResolver(object : AclResolverDataProvider {
            override fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData> {
                return srcAclDataProvider.getClustersData()
                    .mapValues { (_, clusterData) ->
                        val aclsToAdd = withAcls
                            .filterValues { it.needToBeOnCluster(clusterData.clusterRef) }
                            .keys
                        val aclsToRemove = withoutAcls
                            .filterValues { it.needToBeOnCluster(clusterData.clusterRef) }
                            .keys
                        if (aclsToAdd.isNotEmpty() || aclsToRemove.isNotEmpty()) {
                            clusterData.copy(
                                acls = clusterData.acls.plus(aclsToAdd).minus(aclsToRemove.toSet()).distinct()
                            )
                        } else {
                            clusterData
                        }
                    }
            }

        })
    }

    fun checker(
        withAcls: Map<KafkaAclRule, Presence> = emptyMap(),
        withoutAcls: Map<KafkaAclRule, Presence> = emptyMap(),
    ) = ConflictChecker(linkResolver(withAcls, withoutAcls))

    inner class ConflictChecker(
        private val aclLinkResolver: AclLinkResolver,
    ) {
        private fun findConflicting(
            clusterIdentifier: KafkaClusterIdentifier,
            resourceType: AclResource.Type,
            aclRule: KafkaAclRule,
            permittingOperations: Set<AclOperation.Type>,
            affectedNamesExtractor: AclLinkResolver.(KafkaAclRule, KafkaClusterIdentifier) -> List<String>,
            affectingAclsExtractor: AclLinkResolver.(String, KafkaClusterIdentifier) -> List<KafkaAclRule>,
        ): List<KafkaAclRule> {
            if (aclRule.resource.type != resourceType) {
                return emptyList()
            }
            val resourceNames = aclLinkResolver
                .affectedNamesExtractor(aclRule, clusterIdentifier)
                .plus(aclRule.resource.name)
                .distinct()
            return resourceNames
                .flatMap { resourceName ->
                    aclLinkResolver
                        .affectingAclsExtractor(resourceName, clusterIdentifier)
                        .filter { it.operation.type in permittingOperations }
                        .map { resourceName to it }
                }
                .groupBy ({ it.first }) { it.second }
                .flatMap { (_, resourceAcls) ->
                    val canAccessPrincipalAcls = resourceAcls
                        .groupBy { it.principal }
                        .filterValues { acls -> acls.none { it.operation.policy == AclOperation.Policy.DENY } }
                    if (canAccessPrincipalAcls.size > 1) {
                        canAccessPrincipalAcls.values.flatten()
                    } else {
                        emptyList()
                    }
                }
                .distinct()
                .minus(aclRule)
        }

        fun consumerGroupConflicts(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
        ): List<KafkaAclRule> = findConflicting(
            clusterIdentifier, AclResource.Type.GROUP, aclRule,
            setOf(AclOperation.Type.ALL, AclOperation.Type.READ),
            affectedNamesExtractor = { acl, cluster -> findAffectedConsumerGroups(acl, cluster) },
            affectingAclsExtractor = { name, cluster -> findConsumerGroupAffectingAclRules(name, cluster) },
        )

        fun transactionalIdConflicts(
            aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier
        ): List<KafkaAclRule> = findConflicting(
            clusterIdentifier, AclResource.Type.TRANSACTIONAL_ID, aclRule,
            setOf(AclOperation.Type.ALL, AclOperation.Type.WRITE),
            affectedNamesExtractor = { acl, cluster -> findAffectedTransactionalIds(acl, cluster) },
            affectingAclsExtractor = { name, cluster -> findTransactionalIdAffectingAclRules(name, cluster) },
        )

    }

//    fun findConflictingGroupAcls(aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier): List<KafkaAclRule> {
//        val checker = ConflictChecker(linkResolver(clusterIdentifier, withAcls = listOf(aclRule), withoutAcls = emptyList()))
//        return checker.consumerGroupConflicts(aclRule, clusterIdentifier)
//    }
//
//    fun findConflictingGroupAclsOld(aclRule: KafkaAclRule, clusterIdentifier: KafkaClusterIdentifier): List<KafkaAclRule> {
//        if (aclRule.resource.type != AclResource.Type.GROUP || aclRule.operation.policy == AclOperation.Policy.DENY) {
//            return emptyList()
//        }
//        val linkResolver = linkResolver(clusterIdentifier, withAcls = listOf(aclRule), withoutAcls = emptyList())
//        val consumerGroups = linkResolver
//            .findAffectedConsumerGroups(aclRule, clusterIdentifier)
//            .plus(aclRule.resource.name)
//            .distinct()
//        return consumerGroups
//            .flatMap { groupId ->
//                linkResolver
//                    .findConsumerGroupAffectingAclRules(groupId, clusterIdentifier)
//                    .filter { it.operation.type == AclOperation.Type.ALL || it.operation.type == AclOperation.Type.READ }
//                    .map { groupId to it }
//            }
//            .groupBy ({ it.first }) { it.second }
//            .flatMap { (_, groupAcls) ->
//                val canJoinPrincipalAcls = groupAcls
//                    .groupBy { it.principal }
//                    .filterValues { acls -> acls.none { it.operation.policy == AclOperation.Policy.DENY } }
//                if (canJoinPrincipalAcls.size > 1) canJoinPrincipalAcls.values.flatten() else emptyList()
//            }
//            .distinct()
//            .minus(aclRule)
//    }
}