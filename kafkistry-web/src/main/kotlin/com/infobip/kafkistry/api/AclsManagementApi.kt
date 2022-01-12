package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclsManagementService
import com.infobip.kafkistry.kafka.parseAcl
import org.springframework.web.bind.annotation.*

/**
 * Management operations for principal ACLs on actual kafka clusters
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/acls-management")
class AclsManagementApi(
    private val aclsManagementService: AclsManagementService
) {

    @DeleteMapping("/delete-principal-acls")
    fun deletePrincipalAclsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId
    ): Unit = aclsManagementService.deleteUnknownOrUnexpectedPrincipalAcls(principal, clusterIdentifier)

    @DeleteMapping("/delete-principal-acl")
    fun deletePrincipalAclRuleOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("rule") rule: String
    ): Unit = aclsManagementService.deletePrincipalAclOnCluster(principal, rule.parseAcl(), clusterIdentifier)

    @DeleteMapping("/force-delete-principal-acl")
    fun forceDeletePrincipalAclRuleOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("rule") rule: String
    ): Unit = aclsManagementService.forceDeletePrincipalAclOnCluster(principal, rule.parseAcl(), clusterIdentifier)

    @PostMapping("/create-principal-missing-acls")
    fun createPrincipalAclsOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId
    ): Unit = aclsManagementService.createPrincipalMissingAcls(principal, clusterIdentifier)

    @PostMapping("/create-missing-principal-acl")
    fun createPrincipalAclOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("rule") rule: String
    ): Unit = aclsManagementService.createPrincipalMissingAcl(principal, rule.parseAcl(), clusterIdentifier)

    @PostMapping("/force-create-missing-principal-acl")
    fun forceCreatePrincipalAclOnCluster(
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("rule") rule: String
    ): Unit = aclsManagementService.forceCreatePrincipalAcl(principal, rule.parseAcl(), clusterIdentifier)

}