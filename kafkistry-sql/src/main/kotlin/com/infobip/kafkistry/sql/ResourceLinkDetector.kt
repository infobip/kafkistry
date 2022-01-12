package com.infobip.kafkistry.sql

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclResource
import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException

@Component
class ResourceLinkDetector {

    private enum class Attribute(
            val keywords: List<String>
    ) {

        TOPIC("topic", "affectedTopics"),
        CLUSTER("cluster"),
        PRINCIPAL("principal"),
        ACL_HOST("host"),
        ACL_RESOURCE_TYPE("resourceType"),
        ACL_RESOURCE_NAME("resourceName"),
        ACL_RESOURCE_NAME_PATTERN("resourcePattern"),
        ACL_OPERATION("operation"),
        ACL_POLICY("policy"),
        GROUP("group", "consumerGroup"),
        KSTREAM_APP_ID("kStreamAppId"),
        QUOTA_ENTITY("quotaEntityID");

        constructor(vararg keywords: String) : this(listOf(*keywords))
    }

    private val typeAttributes = mapOf(
            LinkedResource.Type.TOPIC to listOf(Attribute.TOPIC),
            LinkedResource.Type.CLUSTER to listOf(Attribute.CLUSTER),
            LinkedResource.Type.CLUSTER_TOPIC to listOf(Attribute.TOPIC, Attribute.CLUSTER),
            LinkedResource.Type.PRINCIPAL to listOf(Attribute.PRINCIPAL),
            LinkedResource.Type.PRINCIPAL_CLUSTER to listOf(Attribute.PRINCIPAL, Attribute.CLUSTER),
            LinkedResource.Type.PRINCIPAL_ACL to listOf(
                    Attribute.PRINCIPAL, Attribute.ACL_HOST, Attribute.ACL_RESOURCE_TYPE, Attribute.ACL_RESOURCE_NAME, Attribute.ACL_RESOURCE_NAME_PATTERN, Attribute.ACL_OPERATION, Attribute.ACL_POLICY
            ),
            LinkedResource.Type.CLUSTER_GROUP to listOf(Attribute.GROUP, Attribute.CLUSTER),
            LinkedResource.Type.QUOTA_ENTITY to listOf(Attribute.QUOTA_ENTITY),
            LinkedResource.Type.KSTREAM_APP to listOf(Attribute.KSTREAM_APP_ID, Attribute.CLUSTER),
    )

    fun detectLinkedResource(columns: List<ColumnMeta>): LinkedResourceFactory {
        val columnNames = columns.map { it.name }
        val attributesIndexes = findAttributesIndexes(columnNames)
        val foundTypes = typeAttributes
                .filterValues { attributesIndexes.keys.containsAll(it) }
                .takeIf { it.isNotEmpty() }
                ?: return NoResourceFactory.INSTANCE
        return DefaultResourceFactory(
                compoundTypes = foundTypes
                        .filter { it.value.size > 1 }
                        .map { it.key },
                columnTypes = foundTypes.asSequence()
                        .filter { it.value.size == 1 }
                        .flatMap { (type, attributes) ->
                            val attribute = attributes.first()
                            attributesIndexes.getValue(attribute)
                                    .asSequence()
                                    .map { it to type }
                        }
                        .associate { it },
                attributeIndexes = attributesIndexes,
                typeAttributes = foundTypes,
        )
    }

    private fun findAttributesIndexes(columns: List<String>): Map<Attribute, List<Int>> {
        return Attribute.values().mapNotNull { attribute ->
            val columnIndexes = columns.asSequence()
                    .mapIndexed { index, column -> index to column }
                    .filter { (_, column) -> attribute.keywords.any { it in column } }
                    .map { (index, _) -> index }
                    .toList()
            if (columnIndexes.isEmpty()) {
                null
            } else {
                attribute to columnIndexes
            }
        }.associate { it }
    }


    interface LinkedResourceFactory {
        fun columnLinkedTypes(): Map<Int, LinkedResource.Type>
        fun compoundLinkedTypes(): List<LinkedResource.Type>
        fun extractLinkedResource(rowValues: List<Any?>): LinkedResource?
    }

    private class NoResourceFactory : LinkedResourceFactory {
        companion object {
            val INSTANCE = NoResourceFactory()
        }
        override fun columnLinkedTypes(): Map<Int, LinkedResource.Type> = emptyMap()
        override fun compoundLinkedTypes(): List<LinkedResource.Type> = emptyList()
        override fun extractLinkedResource(rowValues: List<Any?>): LinkedResource? = null
    }

    private class DefaultResourceFactory(
            private val compoundTypes: List<LinkedResource.Type>,
            private val columnTypes: Map<Int, LinkedResource.Type>,
            private val attributeIndexes: Map<Attribute, List<Int>>,
            private val typeAttributes: Map<LinkedResource.Type, List<Attribute>>,
    ) : LinkedResourceFactory {

        override fun columnLinkedTypes(): Map<Int, LinkedResource.Type> = columnTypes
        override fun compoundLinkedTypes(): List<LinkedResource.Type> = compoundTypes

        override fun extractLinkedResource(rowValues: List<Any?>): LinkedResource {
            val attributeValues = attributeIndexes.mapValues { (_, indexes) ->
                indexes.asSequence().mapNotNull { rowValues[it] }.firstOrNull()
            }
            val topic = attributeValues.getString(Attribute.TOPIC)
            val cluster = attributeValues.getString(Attribute.CLUSTER)
            val principal = attributeValues.getString(Attribute.PRINCIPAL)
            val group = attributeValues.getString(Attribute.GROUP)
            val kStreamAppId = attributeValues.getString(Attribute.KSTREAM_APP_ID)
            val aclRule = attributeValues.getKafkaRule()
            return LinkedResource(
                    types = typeAttributes.filter { (_, requiredAttributes) ->
                        requiredAttributes.all { attributeValues.getString(it) != null }
                    }.keys.toList(),
                    topic = topic,
                    cluster = cluster,
                    principal = principal,
                    group = group,
                    kafkaStreamAppId = kStreamAppId,
                    acl = aclRule
            )
        }

        private fun Map<Attribute, Any?>.getString(attribute: Attribute): String? {
            val value = this[attribute] ?: return null
            return if (value is String) value
            else null
        }

        private fun Map<Attribute, Any?>.getKafkaRule(): KafkaAclRule? {
            val principal = getString(Attribute.PRINCIPAL) ?: return null
            val host = getString(Attribute.ACL_HOST) ?: return null
            val resourceType = getString(Attribute.ACL_RESOURCE_TYPE)
                    ?.toEnumOrNull(AclResource.Type::class.java)
                    ?: return null
            val resourceName = getString(Attribute.ACL_RESOURCE_NAME) ?: return null
            val resourceNamePattern = getString(Attribute.ACL_RESOURCE_NAME_PATTERN)
                    ?.toEnumOrNull(AclResource.NamePattern::class.java)
                    ?: return null
            val operation = getString(Attribute.ACL_OPERATION)
                    ?.toEnumOrNull(AclOperation.Type::class.java)
                    ?: return null
            val policy = getString(Attribute.ACL_POLICY)
                    ?.toEnumOrNull(AclOperation.Policy::class.java)
                    ?: return null
            return KafkaAclRule(
                    principal = principal,
                    host = host,
                    resource = AclResource(resourceType, resourceName, resourceNamePattern),
                    operation = AclOperation(operation, policy)
            )
        }

        private fun <E : Enum<E>> String.toEnumOrNull(enum: Class<E>): E? {
            return try {
                java.lang.Enum.valueOf(enum, this)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}