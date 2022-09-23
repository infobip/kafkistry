package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.utils.Filter
import com.infobip.kafkistry.utils.FilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties(prefix = "app.kafka.enabled")
class KafkaEnabledClustersProperties {

    @NestedConfigurationProperty
    var clusters = FilterProperties()

    @NestedConfigurationProperty
    var tags = FilterProperties()
}

@Component
class ClusterEnabledFilter(
    enabledClustersProperties: KafkaEnabledClustersProperties
) {

    private val identifierFilter = Filter(enabledClustersProperties.clusters)
    private val tagFilter = Filter(enabledClustersProperties.tags)

    fun enabled(clusterIdentifier: KafkaClusterIdentifier): Boolean = enabled(ClusterRef(clusterIdentifier))
    fun enabled(clusterRef: ClusterRef): Boolean = identifierFilter(clusterRef.identifier) && tagFilter.matches(clusterRef.tags)

}