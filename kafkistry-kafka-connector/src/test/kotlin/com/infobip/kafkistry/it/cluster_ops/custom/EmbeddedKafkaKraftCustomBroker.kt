package com.infobip.kafkistry.it.cluster_ops.custom

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.NodeId
import kafka.testkit.BrokerNode
import kafka.testkit.ControllerNode
import kafka.testkit.KafkaClusterTestKit
import kafka.testkit.TestKitNodes
import kafka.utils.Exit
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.Uuid
import org.apache.kafka.common.utils.Utils
import org.apache.kafka.coordinator.group.GroupCoordinatorConfig.GROUP_INITIAL_REBALANCE_DELAY_MS_CONFIG
import org.apache.kafka.coordinator.group.GroupCoordinatorConfig.OFFSETS_TOPIC_REPLICATION_FACTOR_CONFIG
import org.apache.kafka.metadata.bootstrap.BootstrapMetadata
import org.apache.kafka.server.common.MetadataVersion
import org.apache.kafka.server.config.ServerConfigs.DELETE_TOPIC_ENABLE_CONFIG
import org.apache.kafka.test.TestUtils
import org.slf4j.LoggerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class EmbeddedKafkaKraftCustomBroker(
	private val combinedBrokerControllers: Int = 0,
	private val justBrokers: Int = 0,
	private val justControllers: Int = 0,
): EmbeddedKafkaBroker {

	companion object {
		const val START_BROKER_ID = 0
		const val START_CONTROLLER_ID = 3000
		const val START_COMBINED_ID = 10_0000
	}

    private val log = LoggerFactory.getLogger(javaClass)

    private val brokerProperties = mutableMapOf<String, String>()
	private var brokerOverridePropertiesSupplier: (BrokerId) -> Map<String, String> = { emptyMap() }

    private lateinit var cluster: KafkaClusterTestKit

    fun brokerProperty(property: String, value: String): EmbeddedKafkaKraftCustomBroker {
        this.brokerProperties[property] = value
		return this
	}

	fun brokerPropertyOverride(supplier: (BrokerId) -> Map<String, String>) {
		brokerOverridePropertiesSupplier = supplier
	}

	override fun destroy() {
		val shutdownFailure = AtomicReference<Throwable>()
		Utils.closeQuietly(cluster, "embedded Kafka cluster", shutdownFailure)
		if (shutdownFailure.get() != null) {
			throw IllegalStateException("Failed to shut down embedded Kafka cluster", shutdownFailure.get())
		}
	}

    override fun afterPropertiesSet() {
        overrideExitMethods()
        addDefaultBrokerPropsIfAbsent()
        start()
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

	private fun configForNode(nodeId: NodeId): Map<String, String> {
		return brokerProperties + brokerOverridePropertiesSupplier(nodeId);
	}

    private fun start() {
		try {
			val clusterId = Uuid.randomUuid().toString()
			val baseDirectory = TestUtils.tempDirectory().absolutePath
			val nodes = run {
				var nextBrokerId = START_BROKER_ID
				var nextControllerId = START_CONTROLLER_ID
				var nextCombinedId = START_COMBINED_ID
				val brokerNodes = TreeMap<BrokerId, BrokerNode>()
				val controllerNodes = TreeMap<BrokerId, ControllerNode>()
				fun Map<String, String?>.logAll(id: BrokerId, builder: Any) = apply {
					if (log.isDebugEnabled) {
						val pairs = entries.sortedBy { it.key }.joinToString("\n") { "${it.key} => ${it.value}" }
						log.debug("Config of ${builder.javaClass} id=$id\n${pairs.replaceIndent("    ")}")
					}
				}
				fun addNode(node: ControllerNode.Builder) {
					controllerNodes[node.id()] = node
						.setBaseDirectory(baseDirectory)
						.setClusterId(clusterId)
						.setPropertyOverrides(configForNode(node.id()).logAll(node.id(), node))
						.build()
				}
				fun addNode(node: BrokerNode.Builder) {
					brokerNodes[node.id()] = node
						.setBaseDirectory(baseDirectory)
						.setClusterId(clusterId)
						.setPropertyOverrides(configForNode(node.id()).logAll(node.id(), node))
						.build()
				}
				repeat(combinedBrokerControllers) {
					val nodeId = nextCombinedId++
					addNode(ControllerNode.builder().setId(nodeId).setCombined(true))
					addNode(BrokerNode.builder().setId(nodeId).setCombined(true))
				}
				repeat(justBrokers) {
					addNode(BrokerNode.builder().setId(nextBrokerId++).setCombined(false))
				}
				repeat(justControllers) {
					addNode(ControllerNode.builder().setId(nextControllerId++).setCombined(false))
				}
				// This reflective call on private constructor could break in future kafka releases (works for 3.8.1).
				// Reason for this is that we want to manually build and configure ControllerNode-s and BrokerNode-s
				// Problem is that built in builders have hardcoded CONTROLLER and EXTERNAL listener names and we need
				// to be able to configure multiple of them.
				// Alternative approach would be to completely re-implement (copy/paste) whole KafkaClusterTestKit, but then
				// we risk even more that setup requirements might change in the future, and it will get harder to troubleshoot.
				// With this reflective call it will be exactly obvious when/where breaking issue happened.
				val testKitNodesConstructor = TestKitNodes::class.java.getDeclaredConstructor(
					//expected constructor signature:
					// String baseDirectory, String clusterId, BootstrapMetadata bootstrapMetadata, SortedMap<Integer, ControllerNode> controllerNodes, SortedMap<Integer, BrokerNode> brokerNodes
					String::class.java, String::class.java, BootstrapMetadata::class.java, SortedMap::class.java, SortedMap::class.java
				).apply { isAccessible = true }
				testKitNodesConstructor.newInstance(
					baseDirectory, clusterId,
					BootstrapMetadata.fromVersion(MetadataVersion.latestTesting(), "testkit"),
					controllerNodes, brokerNodes,
				)
			}
			val clusterBuilder = KafkaClusterTestKit.Builder(nodes)
			cluster = clusterBuilder.build()
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to create embedded cluster", ex)
		}

		try {
			cluster.format()
			cluster.startup()
			cluster.waitForReadyBrokers()
			log.info("bootstrap.controllers: {}", controllersAsString())
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to start test Kafka cluster", ex)
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
			null
		}
		Exit.setHaltProcedure { statusCode, message ->
			if (log.isDebugEnabled) {
				log.debug(String.format(exitMsg, "halt", statusCode, message), RuntimeException())
			} else {
				log.warn(String.format(exitMsg, "halt", statusCode, message))
			}
			null
		}
	}

    private fun addDefaultBrokerPropsIfAbsent() {
		brokerProperties.putIfAbsent(DELETE_TOPIC_ENABLE_CONFIG, "true")
		brokerProperties.putIfAbsent(GROUP_INITIAL_REBALANCE_DELAY_MS_CONFIG, "0")
		brokerProperties.putIfAbsent(OFFSETS_TOPIC_REPLICATION_FACTOR_CONFIG, "" + (combinedBrokerControllers + justBrokers))
	}

    override fun getBrokersAsString(): String {
        return cluster.clientProperties()[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] as String
    }

    override fun kafkaPorts(vararg ports: Int): EmbeddedKafkaBroker = error("Unsupported")
    override fun getTopics(): MutableSet<String> = error("Unsupported")
    override fun brokerProperties(properties: MutableMap<String, String>?): EmbeddedKafkaBroker = error("Unsupported")
    override fun brokerListProperty(brokerListProperty: String?): EmbeddedKafkaBroker = error("Unsupported")
    override fun addTopics(vararg topicsToAdd: String?)  = error("Unsupported")
    override fun addTopics(vararg topicsToAdd: NewTopic?)  = error("Unsupported")
    override fun adminTimeout(adminTimeout: Int): EmbeddedKafkaBroker = error("Unsupported")
    override fun addTopicsWithResults(vararg topicsToAdd: NewTopic?): MutableMap<String, java.lang.Exception>  = error("Unsupported")
    override fun addTopicsWithResults(vararg topicsToAdd: String?): MutableMap<String, java.lang.Exception>  = error("Unsupported")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>?, seekToEnd: Boolean, vararg topicsToConsume: String?)  = error("Unsupported")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>?, vararg topicsToConsume: String?)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>?, seekToEnd: Boolean, topic: String?)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>?, topic: String?)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>?, seekToEnd: Boolean)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>?)  = error("Unsupported")
    override fun getPartitionsPerTopic(): Int  = error("Unsupported")
}
