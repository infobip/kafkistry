package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.service.quotas.QuotasInspectionService
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

@Component
class QuotasSearchableSource(
    private val quotasRegistryService: QuotasRegistryService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "QUOTAS",
        displayName = "Quotas",
        icon = "icon-quota",
        defaultPriority = 40
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allQuotas = quotasRegistryService.listAllQuotas()
        return allQuotas.map { quota ->
            SearchableItem(
                title = quota.entity.asDescription(),
                subtitle = quota.owner,
                description = buildList {
                    if (quota.hasDefined { consumerByteRate }) {
                        add("Consumer throttle")
                    }
                    if (quota.hasDefined { producerByteRate }) {
                        add("Producer throttle")
                    }
                    if (quota.hasDefined { requestPercentage }) {
                        add("Percentage limit")
                    }
                }.joinToString(", "),
                url = appUrl.quotas().showEntity(quota.entity.asID()),
                category = category,
                metadata = mapOf(
                    "owner" to quota.owner,
                    "entityId" to quota.entity.asID(),
                )
            )
        }
    }

    private fun QuotaEntity.asDescription(): String {
        val userInfo = when (user) {
            null -> null
            QuotaEntity.DEFAULT -> "DEFAULT-user"
            else -> "USER:$user"
        }
        val clientInfo = when (clientId) {
            null -> null
            QuotaEntity.DEFAULT -> "DEFAULT-client-id"
            else -> "CLIENT-ID:$clientId"
        }
        return listOfNotNull(userInfo, clientInfo).joinToString(" ")
    }

    private fun QuotaDescription.hasDefined(property: QuotaProperties.() -> Number?): Boolean {
        return properties.property() != null
                || clusterOverrides.any { it.value.property() != null }
                || tagOverrides.any { it.value.property() != null }
    }
}
