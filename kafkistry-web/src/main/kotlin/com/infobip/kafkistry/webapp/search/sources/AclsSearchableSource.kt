package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

@Component
class AclsSearchableSource(
    private val aclsInspectionService: AclsInspectionService,
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "ACLS",
        displayName = "ACL Principals",
        icon = "icon-acl",
        defaultPriority = 30
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    override fun listAll(): List<SearchableItem> {
        val allPrincipals = aclsInspectionService.inspectAllPrincipals()
        val unknownPrincipals = aclsInspectionService.inspectUnknownPrincipals()
        return (allPrincipals + unknownPrincipals).map { principalInspection ->
            SearchableItem(
                title = principalInspection.principal,
                subtitle = principalInspection.principalAcls?.owner,
                description = null,
                url = appUrl.acls().showAllPrincipalAcls(principalInspection.principal),
                category = category,
                metadata = mapOf(
                    "owner" to (principalInspection.principalAcls?.owner ?: "")
                )
            )
        }
    }
}
