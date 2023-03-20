package com.infobip.kafkistry.model

import com.infobip.kafkistry.model.PresenceType.*
import java.io.Serializable

typealias TopicName = String

data class TopicDescription(
    val name: TopicName,
    val owner: String,
    val description: String,
    val labels: List<Label> = emptyList(),
    val resourceRequirements: ResourceRequirements?,
    val producer: String,
    val presence: Presence,
    val properties: TopicProperties,
    val config: TopicConfigMap,
    val perClusterProperties: Map<KafkaClusterIdentifier, TopicProperties>,
    val perClusterConfigOverrides: Map<KafkaClusterIdentifier, TopicConfigMap>,
    val perTagProperties: Map<Tag, TopicProperties> = emptyMap(),
    val perTagConfigOverrides: Map<Tag, TopicConfigMap> = emptyMap(),
) : Serializable

data class TopicProperties(
    val partitionCount: Int,
    val replicationFactor: Int
) : Serializable

typealias TopicConfigMap = Map<String, String?>

data class Presence(
    val type: PresenceType,
    val kafkaClusterIdentifiers: List<KafkaClusterIdentifier>? = null,
    val tag: Tag? = null,
) : Serializable {

    companion object {
        val ALL = Presence(ALL_CLUSTERS)
    }

    init {
        when (type) {
            ALL_CLUSTERS -> if (kafkaClusterIdentifiers != null || tag != null) throw IllegalArgumentException(
                "Presence type is $type with kafkaClusterIdentifiers=$kafkaClusterIdentifiers, tag=$tag"
            )
            INCLUDED_CLUSTERS, EXCLUDED_CLUSTERS -> if (kafkaClusterIdentifiers == null || tag != null) throw IllegalArgumentException(
                "Presence type is $type with kafkaClusterIdentifiers=$kafkaClusterIdentifiers, tag=$tag"
            )
            TAGGED_CLUSTERS -> if (tag == null || kafkaClusterIdentifiers != null) throw IllegalArgumentException(
                "Presence type is $type with tag==$tag, kafkaClusterIdentifiers=$kafkaClusterIdentifiers"
            )
        }
    }

    fun needToBeOnCluster(cluster: ClusterRef): Boolean = when (type) {
        ALL_CLUSTERS -> true
        INCLUDED_CLUSTERS -> cluster.identifier in kafkaClusterIdentifiers.orEmpty()
        EXCLUDED_CLUSTERS -> cluster.identifier !in kafkaClusterIdentifiers.orEmpty()
        TAGGED_CLUSTERS -> tag in cluster.tags
    }

    override fun toString(): String {
        return when (type) {
            ALL_CLUSTERS -> "Presence(*)"
            INCLUDED_CLUSTERS -> "Presence(only=$kafkaClusterIdentifiers)"
            EXCLUDED_CLUSTERS -> "Presence(all_except=$kafkaClusterIdentifiers)"
            TAGGED_CLUSTERS -> "Presence(tagged=$tag)"
        }
    }

}

enum class PresenceType {
    ALL_CLUSTERS,
    INCLUDED_CLUSTERS,
    EXCLUDED_CLUSTERS,
    TAGGED_CLUSTERS,
}

typealias LabelCategory = String
typealias LabelName = String
typealias LabelExternalId = String

data class Label(
    val category: LabelCategory,
    val name: LabelName,
    val externalId: LabelExternalId? = null,
) : Serializable
