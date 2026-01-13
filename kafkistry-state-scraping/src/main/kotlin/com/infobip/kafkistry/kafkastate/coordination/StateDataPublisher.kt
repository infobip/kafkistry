package com.infobip.kafkistry.kafkastate.coordination

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.memory.Capacity
import com.hazelcast.memory.MemoryUnit
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.slf4j.LoggerFactory

/**
 * Publisher for sharing scraped state data across multiple Kafkistry instances.
 */
interface StateDataPublisher {

    /**
     * Publish scraped state data for other instances to consume.
     *
     * @param stateData The state data to publish
     */
    fun <V> publishStateData(stateData: StateData<V>)

    /**
     * Subscribe to state data updates from other instances.
     *
     * @param stateTypeName The type of state to subscribe to
     * @param listener Callback invoked when state data is received
     */
    fun <V> subscribeToStateUpdates(
        stateTypeName: String,
        listener: (StateData<V>) -> Unit,
    )
}

/**
 * No-op publisher for local-only mode (when Hazelcast is not available).
 * Does nothing - each instance operates independently.
 */
class LocalStateDataPublisher : StateDataPublisher {

    override fun <V> publishStateData(stateData: StateData<V>) {
        // No-op: no sharing in local mode
    }

    override fun <V> subscribeToStateUpdates(stateTypeName: String, listener: (StateData<V>) -> Unit) {
        // No-op: no subscription in local mode
    }
}

/**
 * Hazelcast-based publisher that shares state data via distributed topics.
 *
 * Winners publish their scraped data to Hazelcast topics.
 * Losers subscribe to updates and receive the data.
 * Late joiners will get fresh data on the next scraping round.
 *
 * Uses a separate topic per state type name for efficient message routing.
 */
class HazelcastStateDataPublisher(
    private val hazelcastInstance: HazelcastInstance,
) : StateDataPublisher {

    private val log = LoggerFactory.getLogger(HazelcastStateDataPublisher::class.java)

    /**
     * Get or create a topic for a specific state type.
     */
    private fun getStateDataTopic(stateTypeName: String) =
        hazelcastInstance.getTopic<SerializedStateData>("kafkistry-state-data-$stateTypeName")

    override fun <V> publishStateData(stateData: StateData<V>) = with(stateData) {
        try {
            val serialized = SerializedStateData.from(kafkistryInstance, this)
            val topic = getStateDataTopic(stateTypeName)
            topic.publish(serialized)
            log.debug("Published state data for {}/{} to Hazelcast topic as {} (age: {}ms)",
                stateTypeName, clusterIdentifier, kafkistryInstance, System.currentTimeMillis() - computedTime)
        } catch (ex: Exception) {
            log.error("Failed to publish state data for {}/{}", stateTypeName, clusterIdentifier, ex)
        }
    }

    override fun <V> subscribeToStateUpdates(
        stateTypeName: String,
        listener: (StateData<V>) -> Unit
    ) {
        try {
            // Subscribe to state data messages from the Hazelcast topic for this specific state type
            val topic = getStateDataTopic(stateTypeName)
            topic.addMessageListener { message ->
                if (message.publishingMember.localMember()) {
                    log.trace("Ignoring state data from local member instance={}", message.messageObject.kafkistryInstance)
                    return@addMessageListener
                }
                val serializedData = message.messageObject
                try {
                    val stateData = serializedData.toStateData<V>()
                    val age = System.currentTimeMillis() - stateData.computedTime
                    log.debug("Received shared state for {}/{} from {} (age: {}ms, size: {})",
                        stateTypeName, stateData.clusterIdentifier, serializedData.kafkistryInstance, age,
                        serializedData.valueJson?.length?.let { Capacity(it.toLong(), MemoryUnit.BYTES).toPrettyString()}
                    )
                    listener(stateData)
                } catch (ex: Exception) {
                    log.error("Failed to deserialize state data from Hazelcast topic for {}/{}",
                        serializedData.stateTypeName, serializedData.clusterIdentifier, ex)
                }
            }

            log.info("Subscribed to shared state updates for {} via Hazelcast topic", stateTypeName)
        } catch (ex: Exception) {
            log.error("Failed to subscribe to state updates for {}", stateTypeName, ex)
        }
    }
}
