package com.infobip.kafkistry.repository.storage

import com.infobip.kafkistry.repository.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cached decoration of [RequestingKeyValueRepository].
 * Purpose is to reduce amount of IO reads from disk and deserialization of file contents to  entity instances.
 */
class CachingKeyValueRepository<ID : Any, T : Any>(
    private val delegate: RequestingKeyValueRepository<ID, T>,
    private val refreshAllAfterMs: Long?,
) : DelegatingRequestingKeyValueRepository<ID, T>(delegate) {

    private val cache = ConcurrentHashMap<ID, T>()
    private var lastRefresh = System.currentTimeMillis()

    init {
        refreshAll()
    }

    private fun refreshAll() {
        val all = delegate.findAll().associateBy(keyIdExtractor)
        cache.putAll(all)
        cache.keys.retainAll(all.keys)
        lastRefresh = System.currentTimeMillis()
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
            if (lastRefresh + refreshAllAfterMs < now) {
                refreshAll()
            }
        }
        return operation()
    }

    /**
     * force reload of everything
     */
    override fun refresh() {
        delegate.refresh()
        refreshAll()
    }

    override fun findAll(): List<T> = maybeRefreshAndDo { cache.values.toList() }

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
}