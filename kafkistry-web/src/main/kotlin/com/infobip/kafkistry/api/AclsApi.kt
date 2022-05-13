package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.service.history.AclsChange
import com.infobip.kafkistry.service.history.AclsRequest
import com.infobip.kafkistry.service.history.ChangeCommit
import org.springframework.web.bind.annotation.*

/**
 * CRUD on ACL-s repository
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/acls")
class AclsApi(
    private val aclsRegistry: AclsRegistryService
) {

    @PostMapping
    fun createPrincipalAcls(
        @RequestBody principalAcls: PrincipalAclRules,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = aclsRegistry.createPrincipalAcls(principalAcls, UpdateContext(message, targetBranch))

    @DeleteMapping
    fun deletePrincipalAcls(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = aclsRegistry.deletePrincipalAcls(principal, UpdateContext(message, targetBranch))

    @PutMapping
    fun updatePrincipalAcls(
        @RequestBody principalAcls: PrincipalAclRules,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?
    ): Unit = aclsRegistry.updatePrincipalAcls(principalAcls, UpdateContext(message, targetBranch))

    @GetMapping("/single")
    fun getPrincipalAcls(
        @RequestParam("principal") principal: PrincipalId
    ): PrincipalAclRules = aclsRegistry.getPrincipalAcls(principal)

    @GetMapping("/single/changes")
    fun getPrincipalChanges(
        @RequestParam("principal") principal: PrincipalId
    ): List<AclsRequest> = aclsRegistry.getPrincipalAclsChanges(principal)

    @GetMapping
    fun listPrincipalAcls(): List<PrincipalAclRules> = aclsRegistry.listAllPrincipalsAcls()

    @GetMapping("/pending-requests")
    fun pendingPrincipalRequests(): Map<PrincipalId, List<AclsRequest>> = aclsRegistry.findAllPendingRequests()

    @GetMapping("/single/pending-requests")
    fun pendingPrincipalRequests(
        @RequestParam("principal") principal: PrincipalId
    ): List<AclsRequest> = aclsRegistry.findPendingRequests(principal)

    @GetMapping("/single/pending-requests/branch")
    fun pendingPrincipalRequest(
        @RequestParam("principal") principal: PrincipalId,
        @RequestParam("branch") branch: String
    ): AclsRequest = aclsRegistry.pendingRequest(principal, branch)

    @GetMapping("/history")
    fun commitsHistory(): List<ChangeCommit<AclsChange>> = aclsRegistry.getCommitsHistory()

}