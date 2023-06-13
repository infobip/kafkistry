@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.sql.SqlDataSource
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.Label
import com.infobip.kafkistry.model.Tag
import org.springframework.stereotype.Component
import jakarta.persistence.*

@Component
class RegistryTopicsDataSource(
    private val topicsRegistry: TopicsRegistryService,
    private val clustersRegistry: ClustersRegistryService,
) : SqlDataSource<RegistryTopic> {

    override fun modelAnnotatedClass(): Class<RegistryTopic> = RegistryTopic::class.java

    override fun supplyEntities(): List<RegistryTopic> {
        val allClusterRefs = clustersRegistry.listClustersRefs()
        val allTopics = topicsRegistry.listTopics()
        return allTopics.map { mapTopic(it, allClusterRefs) }
    }

    private fun mapTopic(
        topicDescription: TopicDescription,
        allClusters: List<ClusterRef>
    ): RegistryTopic {
        return RegistryTopic().apply {
            topic = topicDescription.name
            owner = topicDescription.owner
            producer = topicDescription.producer
            description = topicDescription.description
            labels = topicDescription.labels.map {
                TopicLabel().apply {
                    category = it.category
                    name = it.name
                }
            }
            presenceType = topicDescription.presence.type
            presenceClusters = allClusters
                .filter { topicDescription.presence.needToBeOnCluster(it) }
                .map { PresenceCluster().apply { cluster = it.identifier } }
            presenceTag = topicDescription.presence.tag
            frozenProperties = topicDescription.freezeDirectives.flatMap { directive ->
                listOfNotNull(
                    FrozenProperty().apply {
                        name = "partition-count"
                        reason = directive.reasonMessage
                    }.takeIf { directive.partitionCount },
                    FrozenProperty().apply {
                        name = "replication-factor"
                        reason = directive.reasonMessage
                    }.takeIf { directive.replicationFactor },
                ) + directive.configProperties.map {
                    FrozenProperty().apply {
                        name = it
                        reason = directive.reasonMessage
                    }
                }
            }
        }
    }

}

@Entity
@Table(name = "RegistryTopics")
class RegistryTopic {

    @Id
    lateinit var topic: TopicName

    lateinit var producer: String
    lateinit var owner: String
    lateinit var description: String

    @ElementCollection
    @JoinTable(name = "RegistryTopics_Labels")
    lateinit var labels: List<TopicLabel>

    @Enumerated(EnumType.STRING)
    lateinit var presenceType: PresenceType

    @ElementCollection
    @JoinTable(name = "RegistryTopics_PresenceClusters")
    lateinit var presenceClusters: List<PresenceCluster>

    var presenceTag: Tag? = null

    @ElementCollection
    @JoinTable(name = "RegistryTopics_FrozenProperties")
    lateinit var frozenProperties: List<FrozenProperty>

}

@Embeddable
class TopicLabel {
    lateinit var category: LabelCategory
    lateinit var name: LabelName
}

@Embeddable
class FrozenProperty {
    lateinit var name: String
    lateinit var reason: String
}



