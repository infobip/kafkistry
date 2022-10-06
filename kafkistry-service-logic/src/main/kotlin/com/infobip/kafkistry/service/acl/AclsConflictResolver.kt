package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.utils.Filter
import com.infobip.kafkistry.utils.FilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.acl.conflict-checking")
class AclsConflictResolverProperties {

    @NestedConfigurationProperty
    var consumerGroups = EnabledProperties()
    @NestedConfigurationProperty
    var transactionalIds = EnabledProperties()

    class EnabledProperties {
        var enabled = true

        @NestedConfigurationProperty
        var enabledClusters = FilterProperties()

        @NestedConfigurationProperty
        var enabledClusterTags = FilterProperties()
    }
}

private class EnabledFilter(
    private val properties: AclsConflictResolverProperties.EnabledProperties,
) {
    private val clusterFilter = Filter(properties.enabledClusters)
    private val clusterTagFilter = Filter(properties.enabledClusterTags)

    fun enabled(clusterRef: ClusterRef): Boolean {
        if (!properties.enabled) {
            return false
        }
        return clusterFilter(clusterRef.identifier) && clusterTagFilter.matches(clusterRef.tags)
    }
}

@Component
class AclsConflictResolver(
    aclDataProvider: AclResolverDataProvider,
    properties: AclsConflictResolverProperties,
) {

    private val cgFilter = EnabledFilter(properties.consumerGroups)
    private val txnFilter = EnabledFilter(properties.transactionalIds)

    private val srcAclDataProvider = VirtualNamesAclResolverDataProvider(aclDataProvider)
    private val defaultCheckerResolver = ConflictChecker(AclLinkResolver(srcAclDataProvider))

    fun checker(
        principalOverrides: List<PrincipalAclRules> = emptyList(),
    ): ConflictChecker {
        if (principalOverrides.isEmpty()) {
            return defaultCheckerResolver
        }
        return ConflictChecker(
            AclLinkResolver(
                OverridingAclResolverDataProvider(srcAclDataProvider, principalOverrides)
            )
        )
    }

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
                .groupBy({ it.first }) { it.second }
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
            aclRule: KafkaAclRule, clusterRef: ClusterRef
        ): List<KafkaAclRule> {
            if (!cgFilter.enabled(clusterRef)) {
                return emptyList()
            }
            return findConflicting(
                clusterRef.identifier, AclResource.Type.GROUP, aclRule,
                setOf(AclOperation.Type.ALL, AclOperation.Type.READ),
                affectedNamesExtractor = { acl, cluster -> findAffectedConsumerGroups(acl, cluster) },
                affectingAclsExtractor = { name, cluster -> findConsumerGroupAffectingAclRules(name, cluster) },
            )
        }

        fun transactionalIdConflicts(
            aclRule: KafkaAclRule, clusterRef: ClusterRef
        ): List<KafkaAclRule> {
            if (!txnFilter.enabled(clusterRef)) {
                return emptyList()
            }
            return findConflicting(
                clusterRef.identifier, AclResource.Type.TRANSACTIONAL_ID, aclRule,
                setOf(AclOperation.Type.ALL, AclOperation.Type.WRITE),
                affectedNamesExtractor = { acl, cluster -> findAffectedTransactionalIds(acl, cluster) },
                affectingAclsExtractor = { name, cluster -> findTransactionalIdAffectingAclRules(name, cluster) },
            )
        }

     }

}