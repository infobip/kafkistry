package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.utils.ClusterFilter
import com.infobip.kafkistry.utils.ClusterFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties(prefix = "app.kafka.enabled")
class KafkaEnabledClustersProperties {

    @NestedConfigurationProperty
    var clusters = ClusterFilterProperties()
}

@Component
class ClusterEnabledFilter(
    enabledClustersProperties: KafkaEnabledClustersProperties
) {

    private val filter = ClusterFilter(enabledClustersProperties.clusters)

    fun enabled(clusterIdentifier: KafkaClusterIdentifier): Boolean = enabled(ClusterRef(clusterIdentifier))
    fun enabled(clusterRef: ClusterRef): Boolean = filter(clusterRef)

}