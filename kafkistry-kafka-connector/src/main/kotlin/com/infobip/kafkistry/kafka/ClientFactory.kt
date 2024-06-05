package com.infobip.kafkistry.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.model.KafkaProfile
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSamplerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class ClientFactory(
    private val properties: KafkaManagementClientProperties,
    private val recordReadSamplerFactory: RecordReadSamplerFactory,
    private val zookeeperConnectionResolver: Optional<ZookeeperConnectionResolver>,
    private val controllersConnectionResolver: Optional<ControllersConnectionResolver>,
) {

    fun createAdmin(
            connectionDefinition: ConnectionDefinition,
            configurer: (Properties) -> Unit = {}
    ): AdminClient {
        return AdminClient.create(createProperties(connectionDefinition, configurer))
    }

    fun createConsumer(
            connectionDefinition: ConnectionDefinition,
            configurer: (Properties) -> Unit = {}
    ): KafkaConsumer<ByteArray, ByteArray> {
        return KafkaConsumer(
                createProperties(connectionDefinition, configurer),
                ByteArrayDeserializer(),
                ByteArrayDeserializer()
        )
    }

    fun createManagementClient(
            connectionDefinition: ConnectionDefinition,
    ): KafkaManagementClient {
        return KafkaManagementClientImpl(
                connectionDefinition = connectionDefinition,
                clientFactory = this,
                readRequestTimeoutMs = properties.readRequestTimeoutMs,
                writeRequestTimeoutMs = properties.writeRequestTimeoutMs,
                consumerSupplier = object : ConsumerSupplier {

                    override fun createNewConsumer(configurer: (Properties) -> Unit): KafkaConsumer<ByteArray, ByteArray> =
                            createConsumer(connectionDefinition) {
                                configurer(it)
                            }
                },
                recordReadSampler = recordReadSamplerFactory.createReader { properties ->
                    createConsumer(connectionDefinition) {
                        it.putAll(properties)
                    }
                },
                zookeeperConnectionResolver = zookeeperConnectionResolver.orElse(ZookeeperConnectionResolver.DEFAULT),
                eagerlyConnectToZookeeper = properties.eagerlyConnectToZookeeper,
                controllersConnectionResolver = controllersConnectionResolver.orElse(ControllersConnectionResolver.DEFAULT),
        )
    }

    private fun createProperties(
            connectionDefinition: ConnectionDefinition,
            configurer: (Properties) -> Unit
    ) = Properties().apply {
        this.putAll(buildStaticProperties(connectionDefinition.profiles))
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = connectionDefinition.connectionString
        this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = connectionDefinition.securityProtocol().toString()
        configurer(this)
    }

    private fun buildStaticProperties(profiles: List<KafkaProfile>): Map<String, String> {
        return profiles
            .map {
                properties.profiles[it]?.properties ?: throw IllegalArgumentException(
                    "Error while building kafka properties, unknown profile to use '$it'"
                )
            }
            //properties from first profile wins over props from last
            .foldRight(properties.properties.toMap()) { props, acc -> acc + props }
    }

    private fun ConnectionDefinition.securityProtocol(): SecurityProtocol =
            when (ssl) {
                true -> when (sasl) {
                    true -> SecurityProtocol.SASL_SSL
                    false -> SecurityProtocol.SSL
                }
                false -> when (sasl) {
                    true -> SecurityProtocol.SASL_PLAINTEXT
                    false -> SecurityProtocol.PLAINTEXT
                }
            }

    interface ConsumerSupplier {

        fun createNewConsumer(configurer: (Properties) -> Unit = {}): KafkaConsumer<ByteArray, ByteArray>
    }
}

