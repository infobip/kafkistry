package com.infobip.kafkistry.webapp.hazelcast

import com.hazelcast.config.Config
import com.hazelcast.config.NetworkConfig
import com.hazelcast.config.TcpIpConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.FlushMode
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession

/**
 * Hazelcast is, at time of writing this documentation, used for 2 things:
 *  - providing distributed session storage, meaning, when spring http session is created on one instance,
 *    the next incoming http request will be visible on other instances
 *  - providing platform for publishing and receiving KafkistryEvents between the same and between different
 *    instances of application
 */
@EnableHazelcastHttpSession(flushMode = FlushMode.IMMEDIATE)
@Configuration
@ConditionalOnProperty("app.hazelcast.enabled", matchIfMissing = true)
@EnableConfigurationProperties(HazelcastProperties::class)
class HazelcastConfig(
    private val hazelcastProperties: HazelcastProperties,
    customDiscoveryProvider: ObjectProvider<CustomDiscoveryIpsProvider>
) {

    private val customDiscovery = customDiscoveryProvider.getIfAvailable {
        emptyIpsDiscovery
    }

    @Bean
    fun hazelcastInstance(): HazelcastInstance {
        val config = Config().apply {
            clusterName = hazelcastProperties.group
            networkConfig.apply {
                port = hazelcastProperties.port
                publicAddress = hazelcastProperties.advertiseAddress + ":" + hazelcastProperties.port
                join.apply {
                    multicastConfig.isEnabled = false
                    when (hazelcastProperties.discovery.type) {
                        DiscoveryType.NONE -> Unit
                        DiscoveryType.CUSTOM_IMPLEMENTATION -> tcpIpConfig.addMembers(customDiscovery.getSeedIps())
                        DiscoveryType.LOCALHOST_IP -> tcpIpConfig.addMembers("127.0.0.1")
                        DiscoveryType.MULTICAST -> run { multicastConfig.isEnabled = true }
                        DiscoveryType.STATIC_IPS -> tcpIpConfig.addMembers(hazelcastProperties.discovery.memberIps)
                    }
                }
            }
        }
        return Hazelcast.newHazelcastInstance(config)
    }

    private fun TcpIpConfig.addMembers(memberIps: String) = addMembers(listOf(memberIps))

    private fun TcpIpConfig.addMembers(memberIps: List<String>) {
        isEnabled = true
        memberIps.forEach { addMember(it) }
    }

}

@ConfigurationProperties(prefix = "app.hazelcast")
class HazelcastProperties {

    var enabled = true
    var port: Int = NetworkConfig.DEFAULT_PORT
    var group = "kafkistry"
    var advertiseAddress = "127.0.0.1"

    @NestedConfigurationProperty
    val discovery = HazelcastDiscoveryProperties()
}

class HazelcastDiscoveryProperties {

    var type = DiscoveryType.MULTICAST
    var memberIps = ""
}

enum class DiscoveryType {
    NONE,
    CUSTOM_IMPLEMENTATION,
    LOCALHOST_IP,
    MULTICAST,
    STATIC_IPS
}

interface CustomDiscoveryIpsProvider {

    fun getSeedIps(): List<String>
}

private val emptyIpsDiscovery = object : CustomDiscoveryIpsProvider {
    override fun getSeedIps(): List<String> = emptyList()
}
