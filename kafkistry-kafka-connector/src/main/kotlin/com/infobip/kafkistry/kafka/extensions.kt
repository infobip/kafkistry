package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclResource

private val nameRegex = Regex(
    pattern = """\S+|"[^"]+""""
)

private fun nameRegxp() = nameRegex.pattern

private val aclRegex = Regex(
    pattern = """(?<principal>${nameRegxp()})\s+(?<host>${nameRegxp()})\s+(?<resource>\w+):(?<name>${nameRegxp()})(?<nameStar>\*?)\s+(?<operation>\w+)\s+(?<policy>\w+)"""
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
    val rawName = matchResult.groups["name"]!!.value
    val name = rawName.maybeUnquote()
    val hasNameStar = matchResult.groups["nameStar"]!!.value == "*"
    val resource = matchResult.groups["resource"]!!.value.let { AclResource.Type.valueOf(it) }
    val operation = matchResult.groups["operation"]!!.value.let { AclOperation.Type.valueOf(it) }
    val policy = matchResult.groups["policy"]!!.value.let { AclOperation.Policy.valueOf(it) }
    return KafkaAclRule(
        principal = matchResult.groups["principal"]!!.value.maybeUnquote(),
        host = matchResult.groups["host"]!!.value.maybeUnquote(),
        resource = when {
            hasNameStar -> AclResource(resource, name, AclResource.NamePattern.PREFIXED)
            name == "*" || rawName.isQuoted() -> AclResource(resource, name, AclResource.NamePattern.LITERAL)
            name.endsWith("*") -> AclResource(resource, name.substringBeforeLast("*"), AclResource.NamePattern.PREFIXED)
            else -> AclResource(resource, name, AclResource.NamePattern.LITERAL)
        },
        operation = AclOperation(operation, policy)
    )
}

private fun String.isQuoted() = first() == '"' && last() == '"'
private fun String.maybeQuote(): String = if (this != "*" && any { it.isWhitespace() || it == '*' }) "\"${this}\"" else this
private fun String.maybeUnquote(): String = if (isQuoted()) substring(1, length - 1) else this

fun KafkaAclRule.asString(): String {
    val nameSuffix = if (resource.namePattern == AclResource.NamePattern.PREFIXED) "*" else ""
    return "${principal.maybeQuote()} ${host.maybeQuote()} ${resource.type}:${resource.name.maybeQuote()}$nameSuffix ${operation.type} ${operation.policy}"
}

