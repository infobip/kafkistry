package com.infobip.kafkistry.service.quotas

import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.QuotasRepositoryEvent
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.repository.ChangeRequest
import com.infobip.kafkistry.repository.EntityCommitChange
import com.infobip.kafkistry.repository.KafkaQuotasRepository
import com.infobip.kafkistry.service.AbstractRegistryService
import com.infobip.kafkistry.service.KafkistryIntegrityException
import com.infobip.kafkistry.service.UpdateContext
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Service

@Service
class QuotasRegistryService(
    quotasRepository: KafkaQuotasRepository,
    userResolver: CurrentRequestUserResolver,
    eventPublisher: EventPublisher
) : AbstractRegistryService<QuotaEntityID, QuotaDescription, KafkaQuotasRepository, QuotasRequest, QuotasChange>(
    quotasRepository, userResolver, eventPublisher
) {

    override val QuotaDescription.id: QuotaEntityID get() = entity.asID()
    override val type: Class<QuotaDescription> get() = QuotaDescription::class.java

    override fun generateRepositoryEvent(id: QuotaEntityID?) = QuotasRepositoryEvent(id)

    override fun mapChangeRequest(id: QuotaEntityID, changeRequest: ChangeRequest<QuotaDescription>) = QuotasRequest(
        branch = changeRequest.branch,
        commitChanges = changeRequest.commitChanges,
        type = changeRequest.type,
        errorMsg = changeRequest.optionalEntity.errorMsg,
        entityID = id,
        quota = changeRequest.optionalEntity.entity,
    )

    override fun mapChange(change: EntityCommitChange<QuotaEntityID, QuotaDescription>) = QuotasChange(
        changeType = change.changeType,
        oldContent = change.fileChange.oldContent,
        newContent = change.fileChange.newContent,
        errorMsg = change.optionalEntity.errorMsg,
        entityID = change.id,
        quota = change.optionalEntity.entity,
    )

    override fun preCreateCheck(entity: QuotaDescription) = checkValid(entity)

    override fun preUpdateCheck(entity: QuotaDescription) = checkValid(entity)

    private fun checkValid(quotaDescription: QuotaDescription) {
        val emptyProperties = sequence {
            if (quotaDescription.properties == QuotaProperties.NONE) {
                yield("Global-properties")
            }
            for ((cluster, properties) in quotaDescription.clusterOverrides) {
                if (properties == QuotaProperties.NONE) {
                    yield("Cluster:'$cluster'")
                }
            }
            for ((tag, properties) in quotaDescription.tagOverrides) {
                if (properties == QuotaProperties.NONE) {
                    yield("Tag:'$tag'")
                }
            }
        }.toList()
        if (emptyProperties.isNotEmpty()) {
            throw KafkistryIntegrityException(
                "Quota properties must have at least one value specified, following properties are empty: $emptyProperties"
            )
        }
    }

    fun getQuotas(entityID: QuotaEntityID) = getOne(entityID)
    fun findQuotas(entityID: QuotaEntityID) = findOne(entityID)
    fun listAllQuotas() = listAll()

    fun createQuotas(quotaDescription: QuotaDescription, updateContext: UpdateContext) = create(quotaDescription, updateContext)
    fun deleteQuotas(entityID: QuotaEntityID, updateContext: UpdateContext) = delete(entityID, updateContext)
    fun updateQuotas(quotaDescription: QuotaDescription, updateContext: UpdateContext) = update(quotaDescription, updateContext)

}