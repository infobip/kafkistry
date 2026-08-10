package com.infobip.kafkistry.it.cluster_ops.custom

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.NodeId
import kafka.server.Server
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.Uuid
import org.apache.kafka.common.network.ListenerName
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT
import org.apache.kafka.common.test.KafkaClusterTestKit
import org.apache.kafka.common.test.TestKitNode
import org.apache.kafka.common.test.TestKitNodes
import org.apache.kafka.common.utils.Exit
import org.apache.kafka.common.utils.Utils
import org.apache.kafka.coordinator.group.GroupCoordinatorConfig.GROUP_INITIAL_REBALANCE_DELAY_MS_CONFIG
import org.apache.kafka.coordinator.group.GroupCoordinatorConfig.OFFSETS_TOPIC_REPLICATION_FACTOR_CONFIG
import org.apache.kafka.metadata.BrokerState
import org.apache.kafka.metadata.bootstrap.BootstrapMetadata
import org.apache.kafka.metadata.properties.MetaProperties
import org.apache.kafka.metadata.properties.MetaPropertiesEnsemble
import org.apache.kafka.metadata.properties.MetaPropertiesVersion
import org.apache.kafka.server.common.MetadataVersion
import org.apache.kafka.server.config.ServerConfigs.DELETE_TOPIC_ENABLE_CONFIG
import org.apache.kafka.test.TestUtils
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.orEmpty
import kotlin.collections.plus

class EmbeddedKafkaKraftCustomBroker(
	private val interBrokerListenerName: ListenerName,
	private val controllerListenerName: ListenerName,
	private val combinedBrokerControllers: Int = 0,
	private val justBrokers: Int = 0,
	private val justControllers: Int = 0,
	private val startBrokerId: BrokerId = START_BROKER_ID,
	private val startControllerId: BrokerId = START_CONTROLLER_ID,
	private val startCombinedId: BrokerId = START_COMBINED_ID,
): EmbeddedKafkaBroker {

	companion object {
		const val START_BROKER_ID = 0
		const val START_CONTROLLER_ID = 3000
		const val START_COMBINED_ID = 10_0000
	}

    private val log = LoggerFactory.getLogger(javaClass)

    private val brokerProperties = mutableMapOf<String, String>()
	private var brokerOverrideProperties = mutableMapOf<BrokerId, MutableMap<String, String>>()

	private val initialized = AtomicBoolean(false)

    private lateinit var cluster: KafkaClusterTestKit

	fun brokerProperties(): Map<String, String> = brokerProperties
	fun brokerPropertiesOf(brokerId: BrokerId): Map<String, String> = brokerProperties() + brokerOverrideProperties[brokerId].orEmpty()

	fun allBrokersProperty(property: String, value: String): EmbeddedKafkaKraftCustomBroker {
		this.brokerProperties[property] = value
		return this
	}

	fun brokerProperty(brokerId: BrokerId, property: String, value: String): EmbeddedKafkaKraftCustomBroker {
		brokerOverrideProperties.computeIfAbsent(brokerId) { mutableMapOf() }[property] = value
		return this
	}

    fun brokerProperty(property: String, value: String): EmbeddedKafkaKraftCustomBroker {
        this.brokerProperties[property] = value
		return this
	}

	override fun destroy() {
		val shutdownFailure = AtomicReference<Throwable>()
		Utils.closeQuietly(cluster, "embedded Kafka cluster", shutdownFailure)
		if (shutdownFailure.get() != null) {
			throw IllegalStateException("Failed to shut down embedded Kafka cluster", shutdownFailure.get())
		}
	}

	override fun afterPropertiesSet() {
		if (initialized.compareAndSet(false, true)) {
			overrideExitMethods()
			addDefaultBrokerPropsIfAbsent()
			start()
		}
	}

	fun connectionString(protocol: SecurityProtocol = PLAINTEXT, host: String = "localhost"): String {
		if (!initialized.get()) {
			throw IllegalStateException("Can't get connection, cluster not started yet, not bounded to random ports")
		}
		return cluster.brokers().values.asSequence()
			.map { it.boundPort(ListenerName(protocol.name)) }
			.joinToString(separator = ",") { "$host:$it" }
	}

	fun controllersAsString(): String = cluster.bootstrapControllers()

	fun brokerIds(): Set<BrokerId> = cluster.brokers().map { it.key }.toSet()
	fun controllerIds(): Set<NodeId> = cluster.controllers().map { it.key }.toSet()

	fun shutdownBroker(id: BrokerId) {
		with(cluster.brokers().getValue(id)) {
			shutdown()
			awaitShutdown()
		}
	}

	fun startBroker(id: BrokerId) {
		with(cluster.brokers().getValue(id)) {
			startup()
		}
	}

	fun shutdownController(id: NodeId) {
		with(cluster.controllers().getValue(id)) {
			shutdown()
			awaitShutdown()
		}
	}

	fun startController(id: NodeId) {
		with(cluster.controllers().getValue(id)) {
			startup()
		}
	}

	fun brokerState(id: BrokerId): BrokerState {
		return with(cluster.brokers().getValue(id)) {
			val state = brokerState()
			//seems like bug in recent BrokerServer and BrokerLifecycleManager, never transitions to NOT_RUNNING
			if (state == BrokerState.SHUTTING_DOWN && status() is Server.`SHUTDOWN$`) {
				BrokerState.NOT_RUNNING
			} else {
				state
			}
		}
	}

    private fun start() {
		try {
			val clusterId = Uuid.randomUuid().toString()
			val baseDirectory = TestUtils.tempDirectory().absolutePath
			val nodes = run {
				var nextBrokerId = startBrokerId
				var nextControllerId = startControllerId
				var nextCombinedId = startCombinedId
				val brokerNodes = TreeMap<BrokerId, TestKitNode>()
				val controllerNodes = TreeMap<BrokerId, TestKitNode>()
				fun Map<String, String>.logAll(id: BrokerId, what: String) = apply {
					if (log.isDebugEnabled) {
						val pairs = entries.sortedBy { it.key }.joinToString("\n") { "${it.key} => ${it.value}" }
						log.debug("Config of $what id=$id\n${pairs.replaceIndent("    ")}")
					}
				}
				fun addControllerNode(nodeId: Int, combined: Boolean) {
					controllerNodes[nodeId] = buildControllerNode(
						nodeId, baseDirectory, clusterId, combined,
						brokerPropertiesOf(nodeId).logAll(nodeId, "controller"),
					)
				}
				fun addBrokerNode(nodeId: Int, combined: Boolean) {
					brokerNodes[nodeId] = buildBrokerNode(
						nodeId, baseDirectory, clusterId, combined,
						brokerPropertiesOf(nodeId).logAll(nodeId, "broker"),
					)
				}
				repeat(combinedBrokerControllers) {
					val nodeId = nextCombinedId++
					addBrokerNode(nodeId, true)
					addControllerNode(nodeId, true)
				}
				repeat(justBrokers) {
					addBrokerNode(nextBrokerId++, false)
				}
				repeat(justControllers) {
					addControllerNode(nextControllerId++, false)
				}
				// This reflective call on private constructor could break in future kafka releases (works for 4.1.1).
				// Reason for this is that we want to manually build and configure ControllerNode-s and BrokerNode-s
				// Problem is that built in builders have hardcoded CONTROLLER and EXTERNAL listener names and we need
				// to be able to configure multiple of them.
				// Alternative approach would be to completely re-implement (copy/paste) whole KafkaClusterTestKit, but then
				// we risk even more that setup requirements might change in the future, and it will get harder to troubleshoot.
				// With this reflective call it will be exactly obvious when/where breaking issue happened.
				val testKitNodesConstructor = TestKitNodes::class.java.getDeclaredConstructor(
					//expected constructor signature:
					// String baseDirectory,
					// String clusterId,
					// BootstrapMetadata bootstrapMetadata,
					// SortedMap<Integer, ControllerNode> controllerNodes,
					// SortedMap<Integer, BrokerNode> brokerNodes,
					// ListenerName brokerListenerName,
					// SecurityProtocol brokerSecurityProtocol,
					// ListenerName controllerListenerName,
					// SecurityProtocol controllerSecurityProtocol
					String::class.java, String::class.java, BootstrapMetadata::class.java, SortedMap::class.java, SortedMap::class.java,
					ListenerName::class.java, SecurityProtocol::class.java, ListenerName::class.java, SecurityProtocol::class.java
				).apply { isAccessible = true }
				testKitNodesConstructor.newInstance(
					baseDirectory, clusterId,
					BootstrapMetadata.fromVersion(MetadataVersion.latestTesting(), "testkit"),
					controllerNodes, brokerNodes,
					interBrokerListenerName, resolveSecurityProtocolFor(interBrokerListenerName),
					controllerListenerName, resolveSecurityProtocolFor(controllerListenerName),
				)
			}
			val clusterBuilder = KafkaClusterTestKit.Builder(nodes)
			cluster = clusterBuilder.build()
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to create embedded cluster", ex)
		}

		try {
			log.info("Starting kafka, broker states ${cluster.brokers().keys.associateWith { brokerState(it) }}")
			cluster.format()
			// Start controllers before brokers to avoid a race condition in SharedServer (Kafka 4.1.1).
			// For combined (controller+broker) nodes, both ControllerServer and BrokerServer call
			// SharedServer.start() — but only the first caller creates the KafkaRaftManager.
			// ControllerServer passes real listener endpoints; BrokerServer passes Endpoints.empty().
			// KafkaClusterTestKit.startup() runs both concurrently, so the broker can win the race,
			// creating a KafkaRaftManager with empty localListeners. This causes UpdateVoter RPCs
			// to fail with INVALID_REQUEST, crashing the Raft IO thread via fatalFaultHandler.
			// By starting controllers first, SharedServer.startForController() always wins.
			val controllerFutures = cluster.controllers().values.map { controller ->
				CompletableFuture.runAsync { controller.startup() }
			}
			CompletableFuture.allOf(*controllerFutures.toTypedArray()).get(2, TimeUnit.MINUTES)
			val brokerFutures = cluster.brokers().values.map { broker ->
				CompletableFuture.runAsync { broker.startup() }
			}
			CompletableFuture.allOf(*brokerFutures.toTypedArray()).get(2, TimeUnit.MINUTES)
			cluster.waitForReadyBrokers()
			log.info("Kafka started, broker states ${cluster.brokers().keys.associateWith { brokerState(it) }}")
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to start test Kafka cluster", ex)
		}
	}

	private fun resolveSecurityProtocolFor(listenerName: ListenerName): SecurityProtocol {
		return brokerProperties["listener.security.protocol.map"]
			?.split(",")
			?.associate {
				val (listener, protocol) = it.split(":", limit = 2)
				ListenerName(listener) to SecurityProtocol.forName(protocol)
			}
			?.get(listenerName)
			?: SecurityProtocol.forName(listenerName.value())
	}

	private fun buildBrokerNode(
		id: Int,
		baseDirectory: String,
		clusterId: String,
		combined: Boolean,
		propertyOverrides: Map<String, String>,
		numDisksPerBroker: Int = 1,
	): TestKitNode {
		val logDataDirectories = (0 until numDisksPerBroker)
			.map { if (combined) String.format("combined_%d_%d", id, it) else String.format("broker_%d_data%d", id, it) }
			.map { if (Paths.get(it).isAbsolute) it else File(baseDirectory, it).absolutePath }
		val copier = MetaPropertiesEnsemble.Copier(MetaPropertiesEnsemble.EMPTY).apply {
			setMetaLogDir(Optional.of(logDataDirectories[0]!!))
			for (logDir in logDataDirectories) {
				setLogDirProps(
					logDir,
					MetaProperties.Builder()
						.setVersion(MetaPropertiesVersion.V1)
						.setClusterId(clusterId)
						.setNodeId(id)
						.setDirectoryId(generateValidDirectoryId())
						.build()
				)
			}
		}
		return object : TestKitNode {
			private val ensemble: MetaPropertiesEnsemble = copier.copy()
			override fun initialMetaPropertiesEnsemble(): MetaPropertiesEnsemble = ensemble
			override fun propertyOverrides(): Map<String, String> = Collections.unmodifiableMap(propertyOverrides)
		}
	}

	private fun buildControllerNode(
		id: Int,
		baseDirectory: String,
		clusterId: String,
		combined: Boolean,
		propertyOverrides: Map<String, String>,
	): TestKitNode {
		val metadataDirectory = File(
			baseDirectory,
			if (combined) String.format("combined_%d_0", id) else String.format("controller_%d", id)
		).absolutePath
		val copier = MetaPropertiesEnsemble.Copier(MetaPropertiesEnsemble.EMPTY).apply {
			setMetaLogDir(Optional.of(metadataDirectory))
			setLogDirProps(
				metadataDirectory,
				MetaProperties.Builder()
					.setVersion(MetaPropertiesVersion.V1)
					.setClusterId(clusterId)
					.setNodeId(id)
					.setDirectoryId(generateValidDirectoryId())
					.build()
			)
		}
		return object : TestKitNode {
			private val ensemble: MetaPropertiesEnsemble = copier.copy()
			override fun initialMetaPropertiesEnsemble(): MetaPropertiesEnsemble = ensemble
			override fun propertyOverrides(): Map<String, String> = Collections.unmodifiableMap(propertyOverrides)
		}
	}

    private fun overrideExitMethods() {
		val exitMsg = "Exit.%s(%d, %s) called"
        Exit.setExitProcedure { statusCode, message ->
			if (log.isDebugEnabled) {
				log.debug(String.format(exitMsg, "exit", statusCode, message), RuntimeException())
			} else {
				log.warn(String.format(exitMsg, "exit", statusCode, message))
			}
		}
		Exit.setHaltProcedure { statusCode, message ->
			if (log.isDebugEnabled) {
				log.debug(String.format(exitMsg, "halt", statusCode, message), RuntimeException())
			} else {
				log.warn(String.format(exitMsg, "halt", statusCode, message))
			}
		}
	}

    private fun addDefaultBrokerPropsIfAbsent() {
		brokerProperties.putIfAbsent(DELETE_TOPIC_ENABLE_CONFIG, "true")
		brokerProperties.putIfAbsent(GROUP_INITIAL_REBALANCE_DELAY_MS_CONFIG, "0")
		brokerProperties.putIfAbsent(OFFSETS_TOPIC_REPLICATION_FACTOR_CONFIG, "" + (combinedBrokerControllers + justBrokers))
	}

    override fun getBrokersAsString(): String = cluster.bootstrapServers()
    override fun kafkaPorts(vararg ports: Int): EmbeddedKafkaBroker = error("Unsupported")
    override fun getTopics(): MutableSet<String> = error("Unsupported")
    override fun brokerProperties(properties: MutableMap<String, String>): EmbeddedKafkaBroker = error("Unsupported")
    override fun brokerListProperty(brokerListProperty: String): EmbeddedKafkaBroker = error("Unsupported")
    override fun addTopics(vararg topicsToAdd: String)  = error("Unsupported")
    override fun addTopics(vararg topicsToAdd: NewTopic)  = error("Unsupported")
    override fun adminTimeout(adminTimeout: Int): EmbeddedKafkaBroker = error("Unsupported")
    override fun addTopicsWithResults(vararg topicsToAdd: NewTopic): MutableMap<String, java.lang.Exception>  = error("Unsupported")
    override fun addTopicsWithResults(vararg topicsToAdd: String): MutableMap<String, java.lang.Exception>  = error("Unsupported")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean, vararg topicsToConsume: String)  = error("Unsupported")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>, vararg topicsToConsume: String)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, seekToEnd: Boolean, topic: String)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>, topic: String)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>, seekToEnd: Boolean)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>)  = error("Unsupported")
    override fun getPartitionsPerTopic(): Int  = error("Unsupported")
}
