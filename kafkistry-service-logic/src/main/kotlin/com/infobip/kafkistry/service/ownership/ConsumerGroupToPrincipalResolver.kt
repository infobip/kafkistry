package com.infobip.kafkistry.service.ownership

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclLinkResolver
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.utils.deepToString
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class ConsumerGroupPrincipalOwnerRelation(
    val groupId: ConsumerGroupId,
    val principalId: PrincipalId,
    val owner: String,
)

data class PrincipalsOwners(
    val principalsIds: List<PrincipalId>,
    val owners: List<String>,
)

@Component
@ConfigurationProperties(prefix = "app.consumer-group-principal-resolver")
class ConsumerGroupToPrincipalResolverProperties {

    var cacheEnabled = true
    var cacheStorageDir = ""
    var refreshMs = 20_000L

    fun refreshMs() = refreshMs
}

@Component
class ConsumerGroupToPrincipalResolver(
    private val properties: ConsumerGroupToPrincipalResolverProperties,
    private val aclsRegistryService: AclsRegistryService,
    private val aclLinkResolver: AclLinkResolver,
    private val consumersService: ConsumersService,
    private val backgroundJobs: BackgroundJobIssuesRegistry,
) {

    companion object {
        private val canJoinOpTypes = setOf(AclOperation.Type.ALL, AclOperation.Type.READ)
        private const val CACHE_FILENAME = "groupPrincipalsRelations.json"
    }

    private val backgroundJob = BackgroundJob.of(
        category = "CG-ACL-OWNER", description = "Refresh of cached mapping of consumer group to principal owner",
    )
    private val log = LoggerFactory.getLogger(ConsumerGroupToPrincipalResolver::class.java)

    private val relationsRef = AtomicReference<List<ConsumerGroupPrincipalOwnerRelation>>(emptyList())
    private val groupRelationsRef = AtomicReference<Map<ConsumerGroupId, PrincipalsOwners>>(emptyMap())

    private val objectMapper = jacksonObjectMapper()
    private val cacheDir = File(properties.cacheStorageDir)
    private val storageFile = File(cacheDir, CACHE_FILENAME)

    private class RelationsList : ArrayList<ConsumerGroupPrincipalOwnerRelation>()

    init {
        if (properties.cacheEnabled) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            try {
                val relations = objectMapper.readValue(storageFile, RelationsList::class.java)
                log.info("Loaded {} consumer group to principal relations from disk cache", relations.size)
                update(relations)
            } catch (e: Exception) {
                log.info("Unable to load cached consumer group to principal relations, missing or malformed, cause: ${e.deepToString()}")
            }
        }
        this.refresh()
    }

    @Scheduled(fixedDelayString = "#{consumerGroupToPrincipalResolverProperties.refreshMs()}")
    fun refresh() {
        backgroundJobs.doCapturingException(backgroundJob) {
            doRefresh()
        }
    }

    private fun doRefresh() {
        val allRegistryPrincipals = aclsRegistryService.listAllPrincipalsAcls().associateBy { it.principal }
        val consumerGroupPrincipalOwnerRelations = sequence {
            consumersService.allConsumersData().clustersGroups.forEach { clusterGroups ->
                val groupId = clusterGroups.consumerGroup.groupId
                val rules = aclLinkResolver.findConsumerGroupAffectingAclRules(
                    groupId, clusterGroups.clusterIdentifier
                )
                rules
                    .filter { it.operation.type in canJoinOpTypes }
                    .groupBy { it.principal }
                    .filter { (_, pRules) -> pRules.none { it.operation.policy == AclOperation.Policy.DENY } }
                    .forEach { (principal, _) ->
                        allRegistryPrincipals[principal]?.let { principalAcls ->
                            yield(ConsumerGroupPrincipalOwnerRelation(groupId, principal, principalAcls.owner))
                        }
                    }
            }
        }.distinct().toList()

        if (consumerGroupPrincipalOwnerRelations.isEmpty()) {
            log.warn("Refresh resulted in no group to principal mapping, keeping current cache")
            return
        }
        update(consumerGroupPrincipalOwnerRelations)
        if (properties.cacheEnabled) {
            objectMapper.writeValue(storageFile, consumerGroupPrincipalOwnerRelations)
        }
        log.info("Refreshed {} consumer group to principal relations", consumerGroupPrincipalOwnerRelations.size)
    }

    private fun update(relations: List<ConsumerGroupPrincipalOwnerRelation>) {
        relationsRef.set(relations)
        groupRelationsRef.set(
            relations.groupBy { it.groupId }
                .mapValues { (_, relations) ->
                    PrincipalsOwners(
                        principalsIds = relations.map { it.principalId }.distinct(),
                        owners = relations.map { it.owner }.distinct(),
                    )
                }
        )
    }

    fun resolvePrincipalOwner(consumerGroup: ConsumerGroupId): PrincipalsOwners? {
        return this.groupRelationsRef.get()[consumerGroup]
    }

}