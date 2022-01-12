package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclResource

private val aclRegex = Regex(
    pattern = """(?<principal>\S+)\s+(?<host>\S+)\s+(?<resource>\w+):(?<name>\S+)\s+(?<operation>\w+)\s+(?<policy>\w+)"""
)

/**
 * Form:
 * <User:name> <host> <resource>:<name>[*] <operation> <policy>
 *
 * Example: "User:foo 10.0.0.1 TOPIC:bar READ ALLOW"
 */
fun String.parseAcl(): KafkaAclRule {
    val matchResult = aclRegex.matchEntire(this)
        ?: throw IllegalArgumentException("Acl rule can't be parsed with pattern /$aclRegex/, got string: '$this'")
    val name = matchResult.groups["name"]!!.value
    val resource = matchResult.groups["resource"]!!.value.let { AclResource.Type.valueOf(it) }
    val operation = matchResult.groups["operation"]!!.value.let { AclOperation.Type.valueOf(it) }
    val policy = matchResult.groups["policy"]!!.value.let { AclOperation.Policy.valueOf(it) }
    return KafkaAclRule(
        principal = matchResult.groups["principal"]!!.value,
        host = matchResult.groups["host"]!!.value,
        resource = if (name.endsWith("*") && name != "*") {
            AclResource(resource, name.substringBeforeLast("*"), AclResource.NamePattern.PREFIXED)
        } else {
            AclResource(resource, name, AclResource.NamePattern.LITERAL)
        },
        operation = AclOperation(operation, policy)
    )
}

fun KafkaAclRule.asString(): String {
    val nameSuffix = if (resource.namePattern == AclResource.NamePattern.PREFIXED) "*" else ""
    return "$principal $host ${resource.type}:${resource.name}$nameSuffix ${operation.type} ${operation.policy}"
}

