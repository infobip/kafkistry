@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.acl.PrincipalAclsInspection
import com.infobip.kafkistry.sql.SqlDataSource
import org.springframework.stereotype.Component
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Component
class AclPrincipalsDataSource(
    private val aclsInspectionService: AclsInspectionService,
) : SqlDataSource<Principal> {

    override fun modelAnnotatedClass(): Class<Principal> = Principal::class.java

    override fun supplyEntities(): List<Principal> {
        val allPrincipals = aclsInspectionService.inspectAllPrincipals()
        val unknownPrincipals = aclsInspectionService.inspectUnknownPrincipals()
        return (allPrincipals + unknownPrincipals).map { mapPrincipal(it) }
    }

    private fun mapPrincipal(
        principalAclsInspection: PrincipalAclsInspection
    ): Principal {
        return Principal().apply {
            principal = principalAclsInspection.principal
            inRegistry = principalAclsInspection.principalAcls != null
            description = principalAclsInspection.principalAcls?.description
            owner = principalAclsInspection.principalAcls?.owner
        }
    }

}

@Entity
@Table(name = "Principals")
class Principal {

    @Id
    lateinit var principal: PrincipalId

    @Column(nullable = false)
    var inRegistry: Boolean? = null

    var description: String? = null
    var owner: String? = null
}

