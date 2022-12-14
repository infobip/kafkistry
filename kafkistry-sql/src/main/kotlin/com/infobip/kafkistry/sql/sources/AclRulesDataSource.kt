@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.sql.SqlDataSource
import com.infobip.kafkistry.service.acl.AclInspectionResultType
import com.infobip.kafkistry.service.acl.PrincipalAclsInspection
import com.infobip.kafkistry.service.acl.toKafkaAclRule
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import jakarta.persistence.*

@Component
class AclRulesDataSource(
    private val aclsInspectionService: AclsInspectionService,
    private val clustersRegistry: ClustersRegistryService,
) : SqlDataSource<Acl> {

    override fun modelAnnotatedClass(): Class<Acl> = Acl::class.java

    override fun supplyEntities(): List<Acl> {
        val allClusterRefs = clustersRegistry.listClustersRefs()
        val allPrincipals = aclsInspectionService.inspectAllPrincipals()
        val unknownPrincipals = aclsInspectionService.inspectUnknownPrincipals()
        val aclIdGenerator = AclIdGenerator()
        return (allPrincipals + unknownPrincipals).flatMap {
            mapPrincipalClusterAcls(it, allClusterRefs, aclIdGenerator)
        }
    }

    private class AclIdGenerator {

        private val nextId = AtomicLong(1)
        private val rules = ConcurrentHashMap<Pair<KafkaClusterIdentifier, KafkaAclRule>, Long>()

        fun idFor(clusterIdentifier: KafkaClusterIdentifier, rule: KafkaAclRule): Long {
            return rules.computeIfAbsent(clusterIdentifier to rule) { nextId.getAndIncrement() }
        }
    }

    private fun mapPrincipalClusterAcls(
        principalAclsInspection: PrincipalAclsInspection,
        allClusters: List<ClusterRef>,
        idGenerator: AclIdGenerator
    ): List<Acl> {
        val shouldExistMap = principalAclsInspection.principalAcls?.rules?.let { rules ->
            rules.associate { aclRule ->
                val rule = aclRule.toKafkaAclRule(principalAclsInspection.principal)
                rule to allClusters.associate { it.identifier to aclRule.presence.needToBeOnCluster(it) }
            }
        }
        return principalAclsInspection.clusterInspections.flatMap { clusterInspection ->
            clusterInspection.statuses.map { aclRuleStatus ->
                Acl().apply {
                    id = idGenerator.idFor(clusterInspection.clusterIdentifier, aclRuleStatus.rule)
                    principal = principalAclsInspection.principal
                    cluster = clusterInspection.clusterIdentifier
                    acl = aclRuleStatus.rule.toAcl()
                    ok = aclRuleStatus.statusTypes.all { it.valid }
                    status = aclRuleStatus.statusTypes.joinToString(separator = ",") { it.name }
                    exist = aclRuleStatus.statusTypes.mapNotNull { type ->
                        when (type) {
                            AclInspectionResultType.OK, AclInspectionResultType.UNEXPECTED, AclInspectionResultType.UNKNOWN -> true
                            AclInspectionResultType.MISSING, AclInspectionResultType.NOT_PRESENT_AS_EXPECTED, AclInspectionResultType.SECURITY_DISABLED, AclInspectionResultType.UNAVAILABLE -> false
                            AclInspectionResultType.CLUSTER_DISABLED, AclInspectionResultType.CLUSTER_UNREACHABLE -> null
                            else -> null
                        }
                    }.all { it }
                    shouldExist = shouldExistMap
                        ?.get(aclRuleStatus.rule)
                        ?.get(clusterInspection.clusterIdentifier)
                        ?: false
                    affectedTopics = aclRuleStatus.affectedTopics
                    affectedGroups = aclRuleStatus.affectedConsumerGroups
                    conflictingRules = aclRuleStatus.conflictingAcls.map {
                        idGenerator.idFor(clusterInspection.clusterIdentifier, it)
                    }
                }
            }
        }
    }

    private fun KafkaAclRule.toAcl() = AclRule().apply {
        host = this@toAcl.host
        resourceType = resource.type
        resourceName = resource.name
        resourcePattern = resource.namePattern
        operation = this@toAcl.operation.type
        policy = this@toAcl.operation.policy
    }

}

@Entity
@Table(name = "Acls")
class Acl {

    @Id
    var id: Long? = null

    lateinit var principal: PrincipalId
    lateinit var cluster: KafkaClusterIdentifier
    lateinit var acl: AclRule

    var ok: Boolean? = null
    lateinit var status: String

    var exist: Boolean? = null
    var shouldExist: Boolean? = null

    @ElementCollection
    @JoinTable(name = "Acls_AffectedTopics")
    @Column(name = "topic")
    lateinit var affectedTopics: List<TopicName>

    @ElementCollection
    @Column(name = "groupId")
    @JoinTable(name = "Acls_AffectedGroups")
    lateinit var affectedGroups: List<ConsumerGroupId>

    @ElementCollection
    @Column(name = "Acl_otherId")
    @JoinTable(name = "Acls_ConflictingRules")
    lateinit var conflictingRules: List<Long>
}

@Embeddable
class AclRule {

    lateinit var host: String

    @Enumerated(EnumType.STRING)
    lateinit var resourceType: AclResource.Type
    lateinit var resourceName: String

    @Enumerated(EnumType.STRING)
    lateinit var resourcePattern: AclResource.NamePattern

    @Enumerated(EnumType.STRING)
    lateinit var operation: AclOperation.Type

    @Enumerated(EnumType.STRING)
    lateinit var policy: AclOperation.Policy

}
