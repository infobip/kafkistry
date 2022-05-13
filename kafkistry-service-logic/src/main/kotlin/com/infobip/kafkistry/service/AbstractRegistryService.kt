package com.infobip.kafkistry.service

import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.RepositoryEvent
import com.infobip.kafkistry.repository.*
import com.infobip.kafkistry.repository.storage.CommitsRange
import com.infobip.kafkistry.service.history.Change
import com.infobip.kafkistry.service.history.ChangeCommit
import com.infobip.kafkistry.service.history.PendingRequest
import com.infobip.kafkistry.repository.RequestingKeyValueRepository as Repository
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver

data class UpdateContext(
        val message: String,
        val targetBranch: String? = null
)

abstract class AbstractRegistryService<ID : Any, T : Any, R : Repository<ID, T>, PR : PendingRequest, C : Change>(
        private val repository: R,
        private val userResolver: CurrentRequestUserResolver,
        private val eventPublisher: EventPublisher
) {

    private fun resolveUser() = userResolver.resolveUserOrUnknown().let {
        Committer(username = it.username, fullName = it.fullName, email = it.email)
    }

    private fun UpdateContext.toWriteCtx() = WriteContext(
            user = resolveUser(),
            message = message,
            targetBranch = targetBranch
    )

    protected open fun preCreateCheck(entity: T) = Unit
    protected open fun preUpdateCheck(entity: T) = Unit
    protected open fun preDeleteCheck(id: ID) = Unit
    protected abstract fun generateRepositoryEvent(id: ID?): RepositoryEvent
    protected abstract fun mapChangeRequest(id: ID, changeRequest: ChangeRequest<T>): PR
    protected abstract fun mapChange(change: EntityCommitChange<ID, T>): C
    protected abstract val T.id: ID
    protected abstract val type: Class<T>

    fun create(entity: T, updateContext: UpdateContext) {
        preCreateCheck(entity)
        try {
            repository.requestInsert(updateContext.toWriteCtx(), entity)
        } catch (e: DuplicateKeyException) {
            throw KafkistryIntegrityException("Entity of type ${type.simpleName} with id '${entity.id}' already exists, can't create")
        }
        eventPublisher.publish(generateRepositoryEvent(entity.id))
    }

    fun delete(entityId: ID, updateContext: UpdateContext) {
        if (!repository.existsById(entityId)) {
            throw KafkistryIntegrityException("Entity of type ${type.simpleName} with id '${entityId}' does not exist, can't delete")
        }
        preDeleteCheck(entityId)
        repository.requestDeleteById(updateContext.toWriteCtx(), entityId)
        eventPublisher.publish(generateRepositoryEvent(entityId))
    }

    fun deleteAll(updateContext: UpdateContext): Unit = repository.requestDeleteAll(updateContext.toWriteCtx()).also {
        eventPublisher.publish(generateRepositoryEvent(null))
    }

    fun update(entity: T, updateContext: UpdateContext) {
        preUpdateCheck(entity)
        if (!repository.existsById(entity.id)) {
            throw KafkistryIntegrityException("Entity of type ${type.simpleName} with id '${entity.id}' does not exist, can't update")
        }
        repository.requestUpdate(updateContext.toWriteCtx(), entity)
        eventPublisher.publish(generateRepositoryEvent(entity.id))
    }

    fun findAllPendingRequests(): Map<ID, List<PR>> = repository
            .findPendingRequests()
            .associate { (id, changes) ->
                id to changes.map { mapChangeRequest(id, it) }
            }

    fun findPendingRequests(id: ID): List<PR> = repository.findPendingRequestsById(id).map { mapChangeRequest(id, it) }

    fun pendingRequest(id: ID, branch: String): PR {
        return findPendingRequests(id)
                .firstOrNull { it.branch == branch }
                ?: throw KafkistryIntegrityException("Entity of type ${type.simpleName} with id '${id}' does not have changes in branch '$branch'")
    }

    protected fun listAll(): List<T> = repository.findAll().sortedBy { it.id.toString() }

    protected fun findOne(id: ID): T? = repository.findById(id)

    protected fun getOne(id: ID): T {
        return repository.findById(id) ?: throw KafkistryIntegrityException("Entity of type ${type.simpleName} with id '${id}' does not exist")
    }

    fun getChanges(id: ID): List<PR> = repository.listUpdatesOf(id).map { mapChangeRequest(id, it) }

    fun getCommitsHistory(range: CommitsRange = CommitsRange.ALL): List<ChangeCommit<C>> {
        return repository.listCommits(range)
                .map { commitChanges ->
                    ChangeCommit(
                            commit = commitChanges.commit,
                            changes = commitChanges.entities.map(::mapChange)
                    )
                }
    }
}