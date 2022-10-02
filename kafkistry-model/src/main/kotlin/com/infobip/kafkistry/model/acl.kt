package com.infobip.kafkistry.model

//////////////////////////////////
// ACL - Access Control Lists
//////////////////////////////////

typealias PrincipalId = String
typealias TransactionalId = String

data class PrincipalAclRules(
    val principal: PrincipalId,
    val description: String,
    val owner: String,
    val rules: List<AclRule>,
)

data class AclRule(
    val presence: Presence,
    val host: String,
    val resource: AclResource,
    val operation: AclOperation,
)

data class AclResource(
    val type: Type,
    val name: String,
    val namePattern: NamePattern,
) {
    enum class Type {
        TOPIC, GROUP, CLUSTER, TRANSACTIONAL_ID, DELEGATION_TOKEN
    }

    enum class NamePattern {
        LITERAL, PREFIXED
    }
}

data class AclOperation(
    val type: Type,
    val policy: Policy,
) {

    enum class Policy {
        ALLOW, DENY
    }

    enum class Type {
        ALL, READ, WRITE, CREATE, DELETE, ALTER, DESCRIBE, CLUSTER_ACTION, DESCRIBE_CONFIGS, ALTER_CONFIGS, IDEMPOTENT_WRITE
    }
}

