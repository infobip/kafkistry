package com.infobip.kafkistry.repository.storage

import com.infobip.kafkistry.repository.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory cached decoration of [RequestingKeyValueRepository].
 * Purpose is to reduce amount of IO reads from disk and deserialization of file contents to  entity instances.
 */
class CachingKeyValueRepository<ID : Any, T : Any>(
    private val delegate: RequestingKeyValueRepository<ID, T>,
    private val refreshAllAfterMs: Long?,
) : DelegatingRequestingKeyValueRepository<ID, T>(delegate) {

    private val cache = ConcurrentHashMap<ID, T>()
    private var lastRefresh = AtomicReference(System.currentTimeMillis())
    private val commits = AtomicReference<List<CommitEntityChanges<ID, T>>>(emptyList())
    private var globalLastSeenCommitId = AtomicReference<CommitId?>(null)

    private val globalLastSeenBranchCommitIds = AtomicReference<Map<Branch, CommitId>>(emptyMap())
    private val cachedPendingRequests = AtomicReference<List<EntityRequests<ID, T>>>(emptyList())

    init {
        refreshAll()
    }

    private fun refreshAll() {
        val all = delegate.findAll().associateBy(keyIdExtractor)
        cache.putAll(all)
        cache.keys.retainAll(all.keys)
        lastRefresh.set(System.currentTimeMillis())
    }

    private fun refreshById(id: ID) {
        val value = delegate.findById(id)
        if (value == null) {
            cache.remove(id)
        } else {
            cache[id] = value
        }
    }

    private inline fun <T> maybeRefreshAndDo(operation: () -> T): T {
        if (refreshAllAfterMs != null) {
            val now = System.currentTimeMillis()
            if (lastRefresh.get() + refreshAllAfterMs < now) {
                refreshAll()
            }
        }
        return operation()
    }

    /**
     * force reload of everything
     */
    override fun refresh() {
        super.refresh()
        refreshAll()
    }

    override fun findAll(): List<T> = maybeRefreshAndDo { cache.values.toList() }

    override fun findAllAt(branch: Branch): List<T> = delegate.findAllAt(branch)    //explicitly delegated / not using cache; wait as long as needed

    override fun existsById(id: ID): Boolean = maybeRefreshAndDo { id in cache.keys }

    override fun findById(id: ID): T? = maybeRefreshAndDo { cache[id] }

    override fun requestDeleteById(writeContext: WriteContext, id: ID) {
        delegate.requestDeleteById(writeContext, id)
        refreshById(id)
    }

    override fun requestInsert(writeContext: WriteContext, entity: T) {
        delegate.requestInsert(writeContext, entity)
        refreshById(keyIdExtractor(entity))
    }

    override fun requestUpdate(writeContext: WriteContext, entity: T) {
        delegate.requestUpdate(writeContext, entity)
        refreshById(keyIdExtractor(entity))
    }

    override fun requestUpdateMulti(writeContext: WriteContext, entities: List<T>) {
        delegate.requestUpdateMulti(writeContext, entities)
        entities.forEach { refreshById(keyIdExtractor(it)) }
    }

    override fun requestDeleteAll(writeContext: WriteContext) {
        delegate.requestDeleteAll(writeContext)
        refreshAll()
    }

    private fun needsCommitsRefresh(): Boolean = globalLastSeenCommitId.get() != delegate.globallyLastCommitId()

    private fun refreshAllCommits() {
        val newestCommitId = delegate.globallyLastCommitId()
        val listCommits = delegate.listCommits(CommitsRange.ALL)
        commits.set(listCommits)
        globalLastSeenCommitId.set(newestCommitId)
    }

    @Synchronized  //ensure that all concurrent calls will wait and use results from first which acquires the monitor
    private fun refreshCommitsIfNeeded() {
        if (needsCommitsRefresh()) {
            refreshAllCommits()
        }
    }

    override fun listCommits(range: CommitsRange): List<CommitEntityChanges<ID, T>> {
        return if (range == CommitsRange.ALL) {
            refreshCommitsIfNeeded()
            commits.get()
        } else {
            if (needsCommitsRefresh()) {
                //don't want to potentially wait long time for full refresh
                delegate.listCommits(range)
            } else {
                commits.get().asSequence()
                    .drop(range.skip)
                    .let { if (range.count != null) it.take(range.count) else it }
                    .let { if (range.globalLimit != null) it.take(range.globalLimit) else it }
                    .toList()
            }
        }
    }

    private fun needsPendingRequestsRefresh(): Boolean = globalLastSeenBranchCommitIds.get() != delegate.allBranchCommitsIds()

    @Synchronized  //ensure that all concurrent calls will wait and use results from first which acquires the monitor
    private fun refreshPendingRequestsIfNeeded() {
        if (needsPendingRequestsRefresh()) {
            refreshPendingRequests()
        }
    }

    private fun refreshPendingRequests() {
        val currentBranchCommitIds = delegate.allBranchCommitsIds()
        val currentPendingRequests = delegate.findPendingRequests()
        cachedPendingRequests.set(currentPendingRequests)
        globalLastSeenBranchCommitIds.set(currentBranchCommitIds)
    }

    override fun findPendingRequests(): List<EntityRequests<ID, T>> {
        refreshPendingRequestsIfNeeded()
        return cachedPendingRequests.get()
    }

    override fun findPendingRequests(branch: Branch): List<EntityRequests<ID, T>> {
        refreshPendingRequestsIfNeeded()
        return cachedPendingRequests.get()
            .map { er ->
                er.copy(changes = er.changes.filter { it.branch == branch })
            }
            .filter { it.changes.isNotEmpty() }
    }

}
