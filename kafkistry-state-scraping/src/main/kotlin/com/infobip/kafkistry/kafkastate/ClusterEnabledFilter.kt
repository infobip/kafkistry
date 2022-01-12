package com.infobip.kafkistry.kafkastate

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
}

@Component
class ClusterEnabledFilter(
    enabledClustersProperties: KafkaEnabledClustersProperties
) {

    private val filter = Filter(enabledClustersProperties.clusters)

    fun enabled(clusterIdentifier: KafkaClusterIdentifier): Boolean = filter(clusterIdentifier)

}