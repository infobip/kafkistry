package com.infobip.kafkistry.repository

import com.infobip.kafkistry.repository.storage.CommitsRange
import com.infobip.kafkistry.service.KafkistryUnsupportedOperationException

interface ReadOnlyKeyValueRepository<ID : Any, T : Any> : RequestingKeyValueRepository<ID, T>{

    val reasonBeingReadOnly: String

    override fun requestDeleteById(writeContext: WriteContext, id: ID) {
        throw KafkistryUnsupportedOperationException(
            "Can't delete from READ-ONLY repository, reason: $reasonBeingReadOnly"
        )
    }

    override fun requestInsert(writeContext: WriteContext, entity: T) {
        throw KafkistryUnsupportedOperationException(
            "Can't insert into READ-ONLY repository, reason: $reasonBeingReadOnly"
        )
    }

    override fun requestUpdate(writeContext: WriteContext, entity: T) {
        throw KafkistryUnsupportedOperationException(
            "Can't do update on READ-ONLY repository, reason: $reasonBeingReadOnly"
        )
    }

    override fun requestDeleteAll(writeContext: WriteContext) {
        throw KafkistryUnsupportedOperationException(
            "Can't delete all on READ-ONLY repository, reason: $reasonBeingReadOnly"
        )
    }

    override fun findPendingRequests(): List<EntityRequests<ID, T>> = emptyList()

    override fun findPendingRequestsById(id: ID): List<ChangeRequest<T>> = emptyList()

    override fun listUpdatesOf(id: ID): List<ChangeRequest<T>> = emptyList()

    override fun listCommits(range: CommitsRange): List<CommitEntityChanges<ID, T>> = emptyList()
}
