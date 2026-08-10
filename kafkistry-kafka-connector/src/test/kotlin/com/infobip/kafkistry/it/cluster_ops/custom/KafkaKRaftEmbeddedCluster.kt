package com.infobip.kafkistry.it.cluster_ops.custom

import com.infobip.kafkistry.it.cluster_ops.custom.EmbeddedKafkaKraftCustomBroker.Companion.START_COMBINED_ID
import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.model.TopicName
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.network.ListenerName
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT
import org.apache.kafka.metadata.BrokerState
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.test.util.TestSocketUtils
import java.lang.Exception
import java.util.concurrent.TimeUnit
import kotlin.use

/**
 * Finds available random port to bind, however there is small risk that picked port might get occupied in between time
 * when available port is found and kafka gets started and tries to bound to it.
 */
val RANDOM_PORT_SELECTOR: (broker: Int, listenerName: String) -> Int = { _, _ -> TestSocketUtils.findAvailableTcpPort() }

/**
 * Specifies 0-zero port which tells OS to bind on random port which is guaranteed not to be occupied at time of binding.
 *
 * Note that if you want to stop and start kafka during the test, second start will bind to different port than first starting.
 * For such purpose use [RANDOM_PORT_SELECTOR]
 */
val ZERO_PORT_SELECTOR: (broker: Int, listenerName: String) -> Int = { _, _  -> 0 }

class KafkaKRaftEmbeddedCluster(
    private val interBrokerProtocol: SecurityProtocol = PLAINTEXT,
    private val controllerProtocol: SecurityProtocol = interBrokerProtocol,
    private val count: Int = 1,
    private val topics: List<TopicName> = emptyList(),
    private val partitions: Int = 1,
    private val replicationFactor: Int = 1,
    private val combinedBrokerControllers: Int = count,
    private val justBrokers: Int = 0,
    private val justControllers: Int = 0,
): AutoCloseable, EmbeddedKafkaBroker {

    private val log = LoggerFactory.getLogger(javaClass)

    val embeddedKafka = EmbeddedKafkaKraftCustomBroker(
        ListenerName(interBrokerProtocol.name),
        ListenerName("CONTROLLER_${controllerProtocol.name}"),
        combinedBrokerControllers = combinedBrokerControllers,
        justBrokers = justBrokers,
        justControllers = justControllers,
        startCombinedId = if (justBrokers == 0 && justControllers == 0) 0 else START_COMBINED_ID,
    )
    private var securityProtocols: Array<out SecurityProtocol>? = null

    init {
        securityProtocols(securityProtocols = listOf(interBrokerProtocol), controllerProtocol = controllerProtocol)
    }

    fun start() {
        log.info("Going to start kafka cluster...")
        embeddedKafka.afterPropertiesSet()
        adminClient().use {
            it.createNeededTopics()
        }
        log.info("Kafka cluster started")
    }

    fun stop() = embeddedKafka.destroy()
    override fun close() = stop()

    fun shutdownBroker(id: BrokerId) = embeddedKafka.shutdownBroker(id)
    fun startBroker(id: BrokerId) = embeddedKafka.startBroker(id)
    fun brokerState(id: BrokerId): BrokerState = embeddedKafka.brokerState(id)
    fun brokerStates(): Map<BrokerId, BrokerState> = brokerIds().associateWith { brokerState(it) }

    fun allBrokersProperty(property: String, value: String) = apply { embeddedKafka.allBrokersProperty(property, value) }
    fun brokerProperty(broker: BrokerId, property: String, value: String) = apply { embeddedKafka.brokerProperty(broker, property, value) }

    private fun brokerIds(): List<BrokerId> = (START_COMBINED_ID until (START_COMBINED_ID + count)).toList()

    fun securityProtocols(
        host: String = "localhost",
        securityProtocols: List<SecurityProtocol>,
        controllerProtocol: SecurityProtocol,
        portSelector: (broker: Int, listenerName: String) -> Int = ZERO_PORT_SELECTOR
    ) = apply {
        this.securityProtocols = securityProtocols.toTypedArray()
        val protocols = securityProtocols.ifEmpty { listOf(PLAINTEXT) }
        allBrokersProperty(
            "listener.security.protocol.map",
            securityProtocols.joinToString(",") { "$it:$it,CONTROLLER_$it:$it" }
        )
        val controllerListenerName = "CONTROLLER_$controllerProtocol"
        allBrokersProperty("controller.listener.names", controllerListenerName)
        // KafkaClusterTestKit's PreboundSocketFactoryManager pre-binds sockets for the
        // brokerListenerName and controllerListenerName. Those listeners must use port 0 so
        // PreboundSocketFactory returns the pre-bound socket. Other listeners use portSelector.
        val preboundListeners = setOf(interBrokerProtocol.name, controllerListenerName)
        val quorumVoters = mutableListOf<String>()
        for (broker: BrokerId in brokerIds()) {
            val listenerPorts = protocols
                .flatMap { listOf("$it", "CONTROLLER_$it") }
                .associateWith { name ->
                    if (name in preboundListeners) 0 else portSelector(broker, name)
                }
            val listeners = listenerPorts.entries.joinToString(separator = ",") { (listenerName, port) ->
                "${listenerName}://$host:$port"
            }
            // Don't set advertised.listeners — Kafka will derive it from 'listeners' config using
            // actual bound ports. This is necessary because PreboundSocketFactoryManager overrides
            // the port for the inter-broker listener, so explicit advertised.listeners would have
            // the wrong port.
            brokerProperty(broker, "listeners", listeners)
            quorumVoters.add("$broker@$host:${listenerPorts[controllerListenerName]}")
        }
    }

    private fun adminClient(): AdminClient {
        return AdminClient.create(clientProperties())
    }

    private fun clientProperties(): Map<String, String> {
        return mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafka.brokersAsString,
        )
    }

    fun brokersJaas(jaas: String, saslMechanisms: String? = null, vararg securityProtocols: SecurityProtocol) = apply {
        forAllSecurityProtocolsAndSaslMechanisms(saslMechanisms, securityProtocols) { listenerName, saslMechanism ->
            allBrokersProperty("listener.name.$listenerName.$saslMechanism.sasl.jaas.config", jaas)
        }
    }

    fun saslLoginCallbackClassName(clazz: String, saslMechanisms: String? = null, vararg securityProtocols: SecurityProtocol) = apply {
        forAllSecurityProtocolsAndSaslMechanisms(saslMechanisms, securityProtocols) { listenerName, saslMechanism ->
            if (listenerName.contains("sasl")) {
                allBrokersProperty("listener.name.$listenerName.$saslMechanism.sasl.login.callback.handler.class", clazz)
            }
        }
    }

    fun saslServerCallbackClassName(clazz: String, saslMechanisms: String? = null, vararg securityProtocols: SecurityProtocol) = apply {
        forAllSecurityProtocolsAndSaslMechanisms(saslMechanisms, securityProtocols) { listenerName, saslMechanism ->
            if (listenerName.contains("sasl")) {
                allBrokersProperty("listener.name.$listenerName.$saslMechanism.sasl.server.callback.handler.class", clazz)
            }
        }
    }

    private fun forAllSecurityProtocolsAndSaslMechanisms(
        saslMechanisms: String? = null,
        securityProtocols: Array<out SecurityProtocol> = arrayOf(),
        operation: (String, String) -> Unit //(SecurityProtocol,SaslMechanism)
    ) = apply {
        val saslEnabledMechanisms = saslMechanisms
            ?: embeddedKafka.brokerProperties()["sasl.enabled.mechanisms"]
            ?: throw IllegalArgumentException("you need to specify \"sasl.enabled.mechanisms\"")
        val secureProtocols = this.securityProtocols ?: securityProtocols
        saslEnabledMechanisms.split(",")
            .map { it.lowercase() }
            .forEach { saslMechanism ->
                secureProtocols
                    .map { it.toString().lowercase() }
                    .forEach { securityProtocol ->
                        operation(securityProtocol, saslMechanism)
                        operation("controller_$securityProtocol", saslMechanism)
                    }
            }
    }

    private fun AdminClient.createNeededTopics() {
        log.info("Going to create required topics: {}", topics)
        if (topics.isEmpty()) {
            return
        }
        val newTopics = topics.map { NewTopic(it, partitions, replicationFactor.coerceAtMost(count).toShort()) }
        createTopics(newTopics).all().get()
        var successes = 0
        for (attempt in 1..10) {
            val names = listTopics().names().get(2, TimeUnit.SECONDS)
            if (names == topics.toSet()) successes++
            if (successes > 2) break
            Thread.sleep(200)
        }
        log.info("Created required topics: {}", topics)
    }

    override fun destroy() = embeddedKafka.destroy()
    override fun afterPropertiesSet() = embeddedKafka.afterPropertiesSet()
    override fun kafkaPorts(vararg ports: Int): EmbeddedKafkaBroker = run { embeddedKafka.kafkaPorts(*ports) }
    override fun getTopics(): Set<String> = embeddedKafka.topics
    override fun brokerProperties(properties: MutableMap<String, String>): EmbeddedKafkaBroker = run { embeddedKafka.brokerProperties(properties) }
    override fun brokerListProperty(brokerListProperty: String): EmbeddedKafkaBroker = run { embeddedKafka.brokerListProperty(brokerListProperty) }
    override fun adminTimeout(adminTimeout: Int): EmbeddedKafkaBroker = run { embeddedKafka.adminTimeout(adminTimeout) }
    override fun getBrokersAsString(): String = embeddedKafka.brokersAsString
    override fun addTopics(vararg topicsToAdd: String) = embeddedKafka.addTopics(*topicsToAdd)
    override fun addTopics(vararg topicsToAdd: NewTopic) = embeddedKafka.addTopics(*topicsToAdd)
    override fun addTopicsWithResults(vararg topicsToAdd: NewTopic): Map<String, Exception> = embeddedKafka.addTopicsWithResults(*topicsToAdd)
    override fun addTopicsWithResults(vararg topicsToAdd: String): Map<String, Exception> = embeddedKafka.addTopicsWithResults(*topicsToAdd)
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean, vararg topicsToConsume: String) = embeddedKafka.consumeFromEmbeddedTopics(consumer, seekToEnd, *topicsToConsume)
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, vararg topicsToConsume: String) = embeddedKafka.consumeFromEmbeddedTopics(consumer, *topicsToConsume)
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, seekToEnd: Boolean, topic: String) = embeddedKafka.consumeFromAnEmbeddedTopic(consumer, seekToEnd, topic)
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, topic: String) = embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic)
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean) = embeddedKafka.consumeFromAllEmbeddedTopics(consumer, seekToEnd)
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>) = embeddedKafka.consumeFromAllEmbeddedTopics(consumer)
    override fun getPartitionsPerTopic(): Int = embeddedKafka.partitionsPerTopic
}
