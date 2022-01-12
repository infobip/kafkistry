package com.infobip.kafkistry.model

typealias KafkaClusterId = String
typealias KafkaClusterIdentifier = String
typealias Tag = String
typealias KafkaProfile = String

data class KafkaCluster(
    val identifier: KafkaClusterIdentifier,
    val clusterId: KafkaClusterId,
    val connectionString: String,
    val sslEnabled: Boolean,
    val saslEnabled: Boolean,
    val tags: List<Tag> = emptyList(),
    val profiles: List<KafkaProfile> = emptyList(),
) {
    fun ref() = ClusterRef(identifier, tags)
}

data class ClusterRef(
    val identifier: KafkaClusterIdentifier,
    val tags: List<Tag> = emptyList(),
)


