package com.infobip.kafkistry.utils

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.boot.context.properties.NestedConfigurationProperty

class FilterProperties {
    var included: Set<String> = emptySet()
    var excluded: Set<String> = emptySet()
}

class Filter(private val properties: FilterProperties) : (String) -> Boolean {

    companion object {
        const val ALL = "*"
    }

    override fun invoke(value: String): Boolean {
        with(properties) {
            if (included.isNotEmpty() && value !in included && ALL !in included) {
                return false
            }
            if (excluded.isNotEmpty() && value in excluded || ALL in excluded) {
                return false
            }
        }
        return true
    }

    fun matches(values: Collection<String>): Boolean {
        with(properties) {
            if (ALL in included) {
                return values.isNotEmpty()
            }
            if (included.isNotEmpty() && values.all { it !in included }) {
                return false
            }
            if (ALL in excluded) {
                return values.isEmpty()
            }
            if (excluded.isNotEmpty() && values.any { it in excluded }) {
                return false
            }
        }
        return true
    }

}

class ClusterFilterProperties {

    @NestedConfigurationProperty
    var identifiers = FilterProperties()

    @NestedConfigurationProperty
    var tags = FilterProperties()
}

open class ClusterFilter(
    properties: ClusterFilterProperties
): (ClusterRef) -> Boolean {

    private val identifierFilter = Filter(properties.identifiers)
    private val tagFilter = Filter(properties.tags)

    fun enabled(clusterIdentifier: KafkaClusterIdentifier): Boolean = this(ClusterRef(clusterIdentifier))
    fun enabled(clusterRef: ClusterRef): Boolean = this(clusterRef)

    override fun invoke(
        clusterRef: ClusterRef
    ): Boolean = identifierFilter(clusterRef.identifier) && tagFilter.matches(clusterRef.tags)
}

class ClusterTopicFilterProperties {

    @NestedConfigurationProperty
    var clusters = ClusterFilterProperties()

    @NestedConfigurationProperty
    var topics = FilterProperties()
}

open class ClusterTopicFilter(
    clusters: ClusterFilterProperties,
    topics: FilterProperties,
): (ClusterRef, TopicName) -> Boolean {

    constructor(properties: ClusterTopicFilterProperties): this(properties.clusters, properties.topics)

    private val clusterFilter = ClusterFilter(clusters)
    private val topicFilter = Filter(topics)

    fun filter(
        clusterRef: ClusterRef, topicName: TopicName,
    ): Boolean = this(clusterRef, topicName)

    override fun invoke(
        clusterRef: ClusterRef, topicName: TopicName
    ): Boolean = clusterFilter(clusterRef) && topicFilter(topicName)

    fun testCluster(clusterRef: ClusterRef): Boolean = clusterFilter(clusterRef)
    fun testTopic(topicName: TopicName): Boolean = topicFilter(topicName)

}

class ClusterTopicConsumerGroupFilterProperties {

    @NestedConfigurationProperty
    var clusters = ClusterFilterProperties()

    @NestedConfigurationProperty
    var topics = FilterProperties()

    @NestedConfigurationProperty
    var consumerGroups = FilterProperties()
}

open class ClusterTopicConsumerGroupFilter(
    clusters: ClusterFilterProperties,
    topics: FilterProperties,
    consumerGroups: FilterProperties,
): ClusterTopicFilter(clusters, topics), (ClusterRef, TopicName, ConsumerGroupId) -> Boolean {

    constructor(properties: ClusterTopicConsumerGroupFilterProperties): this(properties.clusters, properties.topics, properties.consumerGroups)

    private val consumerGroupsFilter = Filter(consumerGroups)

    override fun invoke(
        clusterRef: ClusterRef, topicName: TopicName, consumerGroupId: ConsumerGroupId
    ): Boolean = this(clusterRef, topicName) && consumerGroupsFilter(consumerGroupId)

    fun testConsumerGroup(consumerGroupId: ConsumerGroupId): Boolean = consumerGroupsFilter(consumerGroupId)
}
