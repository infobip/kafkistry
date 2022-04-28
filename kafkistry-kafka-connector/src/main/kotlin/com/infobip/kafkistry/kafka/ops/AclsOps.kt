package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclResource
import org.apache.kafka.clients.admin.CreateAclsOptions
import org.apache.kafka.clients.admin.DeleteAclsOptions
import org.apache.kafka.clients.admin.DescribeAclsOptions
import org.apache.kafka.common.acl.AccessControlEntry
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclBindingFilter
import org.apache.kafka.common.resource.ResourcePattern
import java.util.concurrent.CompletableFuture

class AclsOps(
    clientCtx: ClientCtx
): BaseOps(clientCtx) {

    fun listAcls(): CompletableFuture<List<KafkaAclRule>> {
        return adminClient
            .describeAcls(AclBindingFilter.ANY, DescribeAclsOptions().withReadTimeout())
            .values()
            .asCompletableFuture("list acls")
            .thenApply { aclBindings -> aclBindings.map { it.toAclRule() } }
    }

    fun createAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        val aclBindings = acls.map { it.toAclBinding() }
        return adminClient.createAcls(aclBindings, CreateAclsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("create acls")
            .thenApply { }
    }

    fun deleteAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        val aclFilters = acls.map { it.toAclBinding().toFilter() }
        return adminClient.deleteAcls(aclFilters, DeleteAclsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete acls")
            .thenApply { }
    }

    private fun AclBinding.toAclRule(): KafkaAclRule {
        return KafkaAclRule(
            principal = entry().principal(),
            resource = AclResource(
                type = pattern().resourceType().convert(),
                name = pattern().name(),
                namePattern = pattern().patternType().convert()
            ),
            host = entry().host(),
            operation = AclOperation(
                type = entry().operation().convert(),
                policy = entry().permissionType().convert()
            )
        )
    }

    private fun KafkaAclRule.toAclBinding(): AclBinding {
        return AclBinding(
            ResourcePattern(
                resource.type.convert(),
                resource.name,
                resource.namePattern.convert()
            ),
            AccessControlEntry(
                principal,
                host,
                operation.type.convert(),
                operation.policy.convert()
            )
        )
    }

}