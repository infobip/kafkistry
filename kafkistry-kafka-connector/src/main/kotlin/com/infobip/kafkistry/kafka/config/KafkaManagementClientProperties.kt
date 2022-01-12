package com.infobip.kafkistry.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.kafka")
class KafkaManagementClientProperties {

    var readRequestTimeoutMs: Long = 10_000
    var writeRequestTimeoutMs: Long = 120_000
    var eagerlyConnectToZookeeper = false
    var properties = mutableMapOf<String, String>()
    var profiles = mutableMapOf<String, PropertiesProfile>()

    /**
     * How many client's (admin+consumer) to have opened toward one cluster
     */
    var clusterConcurrency: Int = 30
}

class PropertiesProfile {
    var properties = mutableMapOf<String, String>()
}



