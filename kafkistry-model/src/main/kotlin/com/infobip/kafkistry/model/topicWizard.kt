package com.infobip.kafkistry.model

data class TopicCreationWizardAnswers(
    val purpose: String,
    val labels: List<Label>,
    val teamName: String,
    val producerServiceName: String,

    val topicNameMetadata: TopicNameMetadata,

    val resourceRequirements: ResourceRequirements,

    val highAvailability: HighAvailability,
    val presence: Presence,
)

enum class HighAvailability {

    /**
     * replication-factor  = 1
     * min.insync.replicas = 1
     */
    NONE,

    /**
     * replication-factor  = 2
     * min.insync.replicas = 1
     */
    BASIC,

    /**
     * replication-factor  = 3
     * min.insync.replicas = 1
     */
    STRONG_AVAILABILITY,

    /**
     * replication-factor  = 3
     * min.insync.replicas = 2
     */
    STRONG_DURABILITY,
}

data class TopicNameMetadata(
    val attributes: Map<String, String>
)

