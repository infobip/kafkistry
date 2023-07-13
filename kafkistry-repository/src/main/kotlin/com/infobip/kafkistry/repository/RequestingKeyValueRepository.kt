package com.infobip.kafkistry.repository

import com.infobip.kafkistry.repository.storage.*
import java.lang.RuntimeException

/**
 * This defines a contract for repository of entities in registry (example: topics, clusters,  ...).
 *
 * Basic CRUD-like operations are defined.
 *
 * This repository has semantics of lazy/postponed completion of update operations.
 * All methods `request*` are write operations which might be effective immediately upon return or later in future or even never.
 * For example, in case of GIT underlying storage, write operation might create new branch and only when that branch
 * merged (to main/master branch) externally, changes will be reflected in `find*`/`exist*` methods.
 */
interface RequestingKeyValueRepository<ID : Any, T : Any> {

    val keyIdExtractor: KeyIdExtractor<ID, T>

    fun findAll(): List<T>

    fun existsById(id: ID): Boolean

    fun findById(id: ID): T?

    @Throws(DuplicateKeyException::class)
    fun requestInsert(writeContext: WriteContext, entity: T)

    @Throws(EntryDoesNotExistException::class)
    fun requestUpdate(writeContext: WriteContext, entity: T)

    @Throws(EntryDoesNotExistException::class)
    fun requestUpdateMulti(writeContext: WriteContext, entities: List<T>)

    fun requestDeleteById(writeContext: WriteContext, id: ID)

    fun requestDeleteAll(writeContext: WriteContext)

    fun findPendingRequests(): List<EntityRequests<ID, T>>

    fun findPendingRequests(branch: Branch): List<EntityRequests<ID, T>>

    fun findPendingRequestsById(id: ID): List<ChangeRequest<T>>

    fun listUpdatesOf(id: ID): List<ChangeRequest<T>>

    fun listCommits(range: CommitsRange): List<CommitEntityChanges<ID, T>>

    fun globallyLastCommitId(): CommitId?

    /**
     * Signal underlying implementation to do any refreshing if needed.
     * It may be useful in case there is some caching going on.
     */
    fun refresh() = Unit
}

interface KeyIdExtractor<ID, T> : (T) -> ID {
    fun idOf(value: T): ID
    override fun invoke(value: T): ID = idOf(value)

    companion object {
        inline operator fun <ID, T> invoke(crossinline lambda: (T) -> ID) = object : KeyIdExtractor<ID, T> {
            override fun idOf(value: T): ID = lambda(value)
        }
    }
}

abstract class DelegatingRequestingKeyValueRepository<ID : Any, T : Any>(
        private val delegate: RequestingKeyValueRepository<ID, T>
) : RequestingKeyValueRepository<ID, T> by delegate

data class EntityRequests<ID, T>(
        val id: ID,
        val changes: List<ChangeRequest<T>>
)

data class ChangeRequest<T>(
        val type: ChangeType,
        val branch: Branch,
        val commitChanges: List<CommitChange>,
        val optionalEntity: OptionalEntity<T>
)

data class OptionalEntity<T>(
        val entity: T?,
        val errorMsg: String?
) {
    companion object {
        fun <T> of(entity: T): OptionalEntity<T> = OptionalEntity(entity, null)
        fun <T> error(errorMsg: String): OptionalEntity<T> = OptionalEntity(null, errorMsg)
    }
}

class DuplicateKeyException(msg: String) : RuntimeException(msg)
class EntryDoesNotExistException(msg: String) : RuntimeException(msg)

data class CommitEntityChanges<ID, T>(
        val commit: Commit,
        val entities: List<EntityCommitChange<ID, T>>
)

data class EntityCommitChange<ID, T>(
        val id: ID,
        val changeType: ChangeType,
        val fileChange: FileCommitChange,
        val optionalEntity: OptionalEntity<T>
)

data class WriteContext(
        val user: Committer,
        val message: String,
        val targetBranch: String? = null
)

data class Committer(
    val username: String,
    val fullName: String,
    val email: String,
)
