package com.infobip.kafkistry.it.cluster_ops.custom

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.NodeId
import com.infobip.kafkistry.utils.getFieldReflective
import kafka.server.KafkaConfig
import kafka.testkit.*
import kafka.utils.Exit
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.utils.Utils
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

    private val brokerProperties = Properties()
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
		Utils.closeQuietly(cluster, "embedded Kafka cluster", shutdownFailure);
		if (shutdownFailure.get() != null) {
			throw IllegalStateException("Failed to shut down embedded Kafka cluster", shutdownFailure.get());
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

    private fun start() {
		try {
			val nodes = TestKitNodes.Builder().apply {
				var nextBrokerId = START_BROKER_ID
				var nextControllerId = START_CONTROLLER_ID
				var nextCombinedId = START_COMBINED_ID
				repeat(combinedBrokerControllers) {
					val nodeId = nextCombinedId++
					addNode(BrokerNode.Builder().setId(nodeId))
					addNode(ControllerNode.Builder().setId(nodeId))
				}
				repeat(justBrokers) {
					addNode(BrokerNode.Builder().setId(nextBrokerId++))
				}
				repeat(justControllers) {
					addNode(ControllerNode.Builder().setId(nextControllerId++))
				}
			}.build()
			val clusterBuilder = KafkaClusterTestKit.Builder(nodes)
			brokerProperties.forEach { (k, v) -> clusterBuilder.setConfigProp(k as String, v as String) }
			cluster = clusterBuilder.build()
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to create embedded cluster", ex);
		}
		try {
			cluster.format();
			cluster.startup();
			cluster.waitForReadyBrokers();
			log.info("bootstrap.controllers: {}", controllersAsString())
		} catch (ex: Exception) {
			throw IllegalStateException("Failed to start test Kafka cluster", ex);
		}
	}

    private fun overrideExitMethods() {
		val exitMsg = "Exit.%s(%d, %s) called";
        Exit.setExitProcedure { statusCode, message ->
			if (log.isDebugEnabled) {
				log.debug(String.format(exitMsg, "exit", statusCode, message), RuntimeException())
			} else {
				log.warn(String.format(exitMsg, "exit", statusCode, message));
			}
			null
		}
		Exit.setHaltProcedure { statusCode, message ->
			if (log.isDebugEnabled) {
				log.debug(String.format(exitMsg, "halt", statusCode, message), RuntimeException())
			} else {
				log.warn(String.format(exitMsg, "halt", statusCode, message));
			}
			null
		}
	}

	private fun TestKitNodes.Builder.addNode(node: ControllerNode.Builder) {
		addNode(node.id(), node, "controllerNodeBuilders")
	}
	private fun TestKitNodes.Builder.addNode(node: BrokerNode.Builder) {
		val overrides = brokerOverridePropertiesSupplier(node.id())
		node.getFieldReflective<MutableMap<String, String>>("propertyOverrides").putAll(overrides)
		addNode(node.id(), node, "brokerNodeBuilders")
	}
	private fun <NODE> TestKitNodes.Builder.addNode(nodeId: Int, node: NODE, mapFieldName: String ) {
		val map = getFieldReflective<MutableMap<Int, NODE>>(mapFieldName)
		map[nodeId] = node
	}

    private fun addDefaultBrokerPropsIfAbsent() {
		brokerProperties.putIfAbsent(KafkaConfig.DeleteTopicEnableProp(), "true")
		brokerProperties.putIfAbsent(KafkaConfig.GroupInitialRebalanceDelayMsProp(), "0")
		brokerProperties.putIfAbsent(KafkaConfig.OffsetsTopicReplicationFactorProp(), "" + (combinedBrokerControllers + justBrokers))
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
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>?, seekToEnd: Boolean, vararg topicsToConsume: String?, )  = error("Unsupported")
    override fun consumeFromEmbeddedTopics(consumer: Consumer<*, *>?, vararg topicsToConsume: String?)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>?, seekToEnd: Boolean, topic: String?)  = error("Unsupported")
    override fun consumeFromAnEmbeddedTopic(consumer: Consumer<*, *>?, topic: String?)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>?, seekToEnd: Boolean)  = error("Unsupported")
    override fun consumeFromAllEmbeddedTopics(consumer: Consumer<*, *>?)  = error("Unsupported")
    override fun getPartitionsPerTopic(): Int  = error("Unsupported")
}
