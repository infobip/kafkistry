package com.infobip.kafkistry.utils

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

}


class ClusterTopicFilterProperties {

    @NestedConfigurationProperty
    var clusters = FilterProperties()

    @NestedConfigurationProperty
    var topics = FilterProperties()
}

open class ClusterTopicFilter(
    clusters: FilterProperties,
    topics: FilterProperties,
): (KafkaClusterIdentifier, TopicName) -> Boolean {

    constructor(properties: ClusterTopicFilterProperties): this(properties.clusters, properties.topics)

    private val clusterFilter = Filter(clusters)
    private val topicFilter = Filter(topics)

    fun filter(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName,
    ): Boolean = this(clusterIdentifier, topicName)

    override fun invoke(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName
    ): Boolean = clusterFilter(clusterIdentifier) && topicFilter(topicName)

    fun testCluster(clusterIdentifier: KafkaClusterIdentifier): Boolean = clusterFilter(clusterIdentifier)
    fun testTopic(topicName: TopicName): Boolean = topicFilter(topicName)

}

class ClusterTopicConsumerGroupFilterProperties {

    @NestedConfigurationProperty
    var clusters = FilterProperties()

    @NestedConfigurationProperty
    var topics = FilterProperties()

    @NestedConfigurationProperty
    var consumerGroups = FilterProperties()
}

open class ClusterTopicConsumerGroupFilter(
    clusters: FilterProperties,
    topics: FilterProperties,
    consumerGroups: FilterProperties,
): ClusterTopicFilter(clusters, topics), (KafkaClusterIdentifier, TopicName, ConsumerGroupId) -> Boolean {

    constructor(properties: ClusterTopicConsumerGroupFilterProperties): this(properties.clusters, properties.topics, properties.consumerGroups)

    private val consumerGroupsFilter = Filter(consumerGroups)

    override fun invoke(
        clusterIdentifier: KafkaClusterIdentifier, topicName: TopicName, consumerGroupId: ConsumerGroupId
    ): Boolean = this(clusterIdentifier, topicName) && consumerGroupsFilter(consumerGroupId)

    fun testConsumerGroup(consumerGroupId: ConsumerGroupId): Boolean = consumerGroupsFilter(consumerGroupId)
}
