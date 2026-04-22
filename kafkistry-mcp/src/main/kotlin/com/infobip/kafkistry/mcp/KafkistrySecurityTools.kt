package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistrySecurityTools(
    private val aclsRegistryService: AclsRegistryService,
    private val quotasRegistryService: QuotasRegistryService,
) {

    @McpTool(
        name = "kafkistry_list_registry_acl_principals",
        description = """Returns the identifiers of all ACL principals registered in the Kafkistry registry.
Each principal represents a Kafka security principal (e.g., "User:service-account-name") for which
ACL rules have been defined in the registry. These identifiers can be used with
kafkistry_get_registry_principal_acls to fetch the full ACL rule set for a specific principal.
The registry defines the desired ACL state; actual state on each cluster may differ."""
    )
    open fun listRegistryAclPrincipals(): String {
        return try {
            val result = aclsRegistryService.listAllPrincipalsAcls().map { it.principal }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_registry_acl_principals", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_principal_acls",
        description = """Returns the full set of ACL rules for a specific principal as stored in the registry.
Contains the principal identifier and the list of ACL rule entries. Each rule specifies:
resource type (TOPIC, GROUP, CLUSTER, etc.), resource name pattern, operation (READ, WRITE, DESCRIBE, etc.),
and permission type (ALLOW or DENY). The presence field on each rule determines on which clusters the ACL
should be applied. This is the desired/expected ACL state."""
    )
    open fun getRegistryPrincipalAcls(
        @McpToolParam(required = true, description = "Principal identifier (e.g., \"User:my-service\")") principal: String,
    ): String {
        return try {
            val result = aclsRegistryService.getPrincipalAcls(principal)
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_principal_acls", ex)
        }
    }

    @McpTool(
        name = "kafkistry_list_registry_quota_entities",
        description = """Returns the identifiers of all client quota entities registered in the Kafkistry registry.
A quota entity identifies the target of a Kafka client quota and can represent:
a specific user (e.g., "User:alice"), a specific client ID (e.g., "ClientId:my-producer"),
a combination of user and client ID, or default quotas for all users or all clients.
Use with kafkistry_get_registry_quota to fetch the full quota configuration for a specific entity."""
    )
    open fun listRegistryQuotaEntities(): String {
        return try {
            val result = quotasRegistryService.listAllQuotas().map { it.entity.asID() }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_registry_quota_entities", ex)
        }
    }

    @McpTool(
        name = "kafkistry_list_registry_quotas",
        description = """Returns all client quota definitions from the registry.
Each QuotaDescription contains: entity (identifies the target: user, client ID, or combination),
presence (cluster-targeting policy: which clusters the quota should be applied on),
and properties (actual quota values: producer_byte_rate, consumer_byte_rate, request_percentage limits).
This is the desired quota state as configured in the registry."""
    )
    open fun listRegistryQuotas(): String {
        return try {
            val result = quotasRegistryService.listAllQuotas()
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_list_registry_quotas", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_registry_quota",
        description = """Returns the quota definition for a specific entity from the registry.
Includes: entity identifier, cluster presence configuration (which clusters the quota applies to),
and quota property values (e.g., producer_byte_rate, consumer_byte_rate, request_percentage).
Use kafkistry_list_registry_quota_entities to discover valid quota entity IDs before calling this tool."""
    )
    open fun getRegistryQuota(
        @McpToolParam(required = true, description = "Quota entity identifier (e.g., \"User:alice\" or \"ClientId:producer-app\")") quotaEntityID: String,
    ): String {
        return try {
            val result = quotasRegistryService.getQuotas(quotaEntityID)
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_registry_quota", ex)
        }
    }
}
