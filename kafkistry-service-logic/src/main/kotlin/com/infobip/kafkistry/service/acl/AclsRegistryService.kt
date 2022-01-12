package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.events.AclsRepositoryEvent
import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.repository.ChangeRequest
import com.infobip.kafkistry.repository.EntityCommitChange
import com.infobip.kafkistry.repository.KafkaAclsRepository
import com.infobip.kafkistry.service.AclsChange
import com.infobip.kafkistry.service.AclsRequest
import com.infobip.kafkistry.service.KafkistryIntegrityException
import com.infobip.kafkistry.service.AbstractRegistryService
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.service.toKafkaAclRule
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Service

@Service
class AclsRegistryService(
        aclsRepository: KafkaAclsRepository,
        userResolver: CurrentRequestUserResolver,
        eventPublisher: EventPublisher
) : AbstractRegistryService<PrincipalId, PrincipalAclRules, KafkaAclsRepository, AclsRequest, AclsChange>(
        aclsRepository, userResolver, eventPublisher
) {
    override fun generateRepositoryEvent(id: PrincipalId?) = AclsRepositoryEvent(id)

    override fun mapChangeRequest(id: PrincipalId, changeRequest: ChangeRequest<PrincipalAclRules>) = AclsRequest(
            branch = changeRequest.branch,
            commitChanges = changeRequest.commitChanges,
            type = changeRequest.type,
            errorMsg = changeRequest.optionalEntity.errorMsg,
            principal = id,
            principalAcls = changeRequest.optionalEntity.entity
    )

    override fun mapChange(change: EntityCommitChange<PrincipalId, PrincipalAclRules>) = AclsChange(
            changeType = change.changeType,
            oldContent = change.fileChange.oldContent,
            newContent = change.fileChange.newContent,
            errorMsg = change.optionalEntity.errorMsg,
            principal = change.id,
            principalAcls = change.optionalEntity.entity
    )

    override val PrincipalAclRules.id: PrincipalId get() = principal
    override val type: Class<PrincipalAclRules> get() = PrincipalAclRules::class.java

    override fun preCreateCheck(entity: PrincipalAclRules) = entity.checkDuplicateRules()
    override fun preUpdateCheck(entity: PrincipalAclRules) = entity.checkDuplicateRules()

    fun createPrincipalAcls(aclRules: PrincipalAclRules, updateContext: UpdateContext) = create(aclRules, updateContext)
    fun deletePrincipalAcls(principalId: PrincipalId, updateContext: UpdateContext) = delete(principalId, updateContext)
    fun updatePrincipalAcls(aclRules: PrincipalAclRules, updateContext: UpdateContext) = update(aclRules, updateContext)

    fun getPrincipalAcls(principalId: PrincipalId): PrincipalAclRules = getOne(principalId)
    fun findPrincipalAcls(principalId: PrincipalId): PrincipalAclRules? = findOne(principalId)
    fun listAllPrincipalsAcls(): List<PrincipalAclRules> = listAll()
    fun getPrincipalAclsChanges(principalId: PrincipalId): List<AclsRequest> = getChanges(principalId)

    private fun PrincipalAclRules.checkDuplicateRules() {
        rules.asSequence()
                .map { it.toKafkaAclRule(principal) }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .takeIf { it.isNotEmpty() }
                ?.also {
                    throw KafkistryIntegrityException("Principal acl rules contain duplicate following rules: " + it.keys)
                }
    }

}